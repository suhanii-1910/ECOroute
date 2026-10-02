package ecoroute.algorithm;

import ecoroute.planning.RequestView;
import ecoroute.planning.StopPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * STEP 3 OF THE ALGORITHM PIPELINE
 *
 * Orders one vehicle's stops using the NEAREST-NEIGHBOUR heuristic.
 *
 * =====================================================================
 *  WHAT PROBLEM IS THIS?
 * =====================================================================
 * The TRAVELLING SALESMAN PROBLEM: visit every stop once, with the shortest
 * total travel. TSP is NP-HARD - for N stops there are (N-1)!/2 possible
 * tours, so brute force is hopeless even for modest N. Ten stops is already
 * 181,440 tours; twenty stops is more than 10^16.
 *
 * So we use a heuristic.
 *
 * =====================================================================
 *  THE ALGORITHM
 * =====================================================================
 *   1. Pick a deterministic starting stop.
 *   2. Mark it visited. It becomes stop_sequence = 1.
 *   3. From the current position, go to the NEAREST UNVISITED stop.
 *   4. Repeat until every stop is visited.
 *
 * COMPLEXITY: O(N^2). Each of N steps scans up to N unvisited stops.
 *
 * =====================================================================
 *  WHY IT IS NOT OPTIMAL - THE COUNTER-EXAMPLE TO MEMORISE
 * =====================================================================
 * Nearest-neighbour is greedy, so it grabs all the easy nearby stops first
 * and can strand a far-away stop, forcing one long ugly leg at the end.
 * It has been called "the algorithm that leaves itself a long walk home."
 *
 * Four stops on a line at positions 0, 1, 3 and 7, starting at 1:
 *     NN:       1 -> 0 (1) -> 3 (3) -> 7 (4)   total 8
 *     better:   0 -> 1 (1) -> 3 (2) -> 7 (4)   total 7
 * Small, concrete, drawable on a whiteboard. Learn it.
 *
 * In the worst case nearest-neighbour can be arbitrarily bad relative to the
 * optimal tour; in practice on clustered real-world points it typically lands
 * within about 25% of optimal, which is entirely adequate for ordering a
 * handful of stops inside one city zone.
 *
 * =====================================================================
 *  WHERE DO WE START? - A REAL DESIGN DECISION
 * =====================================================================
 * Nearest-neighbour needs a starting point, and the answer changes the whole
 * route. The schema has no depot or garage table, so there is no "home" to
 * start from. Our rule:
 *
 *      START AT THE REQUEST WITH THE LOWEST request_id
 *      (among those that have coordinates)
 *
 * Two justifications, and the second is the better one:
 *
 *   TECHNICAL - request ids are assigned by AUTO_INCREMENT, so the lowest id
 *   is a stable, always-available, never-ambiguous choice. Determinism is
 *   guaranteed with no extra data.
 *
 *   OPERATIONAL - the lowest id is the EARLIEST-RAISED request. Serving the
 *   longest-waiting customer first is a defensible fairness rule that a
 *   collection manager would recognise and endorse.
 *
 * If a depot were ever added to the schema, the honest improvement would be
 * to start from the stop nearest the depot. That is one line of change here,
 * and nothing else in the system would need touching - note it as future
 * scope.
 *
 * =====================================================================
 *  REQUESTS WITHOUT COORDINATES
 * =====================================================================
 * WASTE_GENERATOR.latitude and .longitude are nullable, so a generator may
 * have none. Such a stop cannot participate in distance ranking. Rather than
 * crash, or silently drop it - which would mean a driver never collects it -
 * we APPEND those stops at the end, ordered by request id.
 *
 * They are still visited, still get a valid stop_sequence, and the route
 * stays complete. Correctness before cleverness.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class NearestNeighborRoute implements RouteStrategy {

    /**
     * Two distances closer together than this count as equal.
     *
     * Needed because binary floating point cannot represent most decimals
     * exactly, so two genuinely identical distances can differ in the last
     * bit or two. Without an epsilon, a tie would be resolved by accumulated
     * rounding noise - which is the definition of non-deterministic.
     * With it, ties fall through to the request-id rule below.
     */
    private static final double EPSILON = 1e-12;

    private static final Comparator<RequestView> BY_REQUEST_ID = new Comparator<RequestView>() {
        @Override
        public int compare(RequestView a, RequestView b) {
            return Long.compare(a.getRequestId(), b.getRequestId());
        }
    };

    @Override
    public String getName() {
        return "Nearest Neighbour";
    }

    @Override
    public String getDescription() {
        return "Greedy nearest-unvisited-stop heuristic (O(N^2)). "
             + "Produces a valid, deterministic visiting order. "
             + "Does NOT guarantee the shortest possible route. "
             + "Distances are straight-line approximations on latitude/longitude, "
             + "not road distances.";
    }

    @Override
    public List<StopPlan> order(List<RequestView> requests) {

        List<StopPlan> plan = new ArrayList<StopPlan>();
        if (requests == null || requests.isEmpty()) return plan;   // contract rule 4

        // ---- Split into stops we can rank and stops we cannot ----
        List<RequestView> locatable = new ArrayList<RequestView>();
        List<RequestView> unlocatable = new ArrayList<RequestView>();
        for (RequestView request : requests) {
            if (request == null) continue;
            if (request.hasCoordinates()) locatable.add(request);
            else unlocatable.add(request);
        }

        // Deterministic baseline order before any distance work happens.
        Collections.sort(locatable, BY_REQUEST_ID);
        Collections.sort(unlocatable, BY_REQUEST_ID);

        int sequence = 1;

        // ---- The nearest-neighbour walk ----
        if (!locatable.isEmpty()) {

            Set<Long> visited = new HashSet<Long>();   // HashSet: O(1) contains()

            // Step 1: the deterministic start - lowest request id.
            RequestView current = locatable.get(0);
            visited.add(Long.valueOf(current.getRequestId()));
            plan.add(new StopPlan(current, sequence++, 0.0));   // no previous leg

            // Steps 2..N: repeatedly hop to the nearest unvisited stop.
            while (visited.size() < locatable.size()) {

                RequestView nearest = null;
                double nearestDistance = Double.MAX_VALUE;

                for (RequestView candidate : locatable) {
                    if (visited.contains(Long.valueOf(candidate.getRequestId()))) continue;

                    double distance = Distance.between(current, candidate);

                    if (nearest == null) {
                        nearest = candidate;
                        nearestDistance = distance;

                    } else if (distance < nearestDistance - EPSILON) {
                        // Clearly closer.
                        nearest = candidate;
                        nearestDistance = distance;

                    } else if (Math.abs(distance - nearestDistance) <= EPSILON
                            && candidate.getRequestId() < nearest.getRequestId()) {
                        // EQUIDISTANT: break the tie by the lower request id.
                        // Without this the winner would depend on iteration
                        // order, and two equidistant stops would make the
                        // route non-reproducible.
                        nearest = candidate;
                        nearestDistance = distance;
                    }
                }

                visited.add(Long.valueOf(nearest.getRequestId()));
                plan.add(new StopPlan(nearest, sequence++, nearestDistance));
                current = nearest;
            }
        }

        // ---- Stops with no coordinates go last, in request-id order ----
        for (RequestView request : unlocatable) {
            plan.add(new StopPlan(request, sequence++, Distance.UNKNOWN));
        }

        return plan;
    }

    // =================================================================
    //  REPORTING
    // =================================================================

    /**
     * Total straight-line length of the planned route, in degrees.
     * Legs to stops with unknown coordinates are excluded rather than
     * poisoning the total with Double.MAX_VALUE.
     */
    public static double totalDistance(List<StopPlan> plan) {
        double total = 0.0;
        if (plan == null) return total;
        for (StopPlan stop : plan) {
            double leg = stop.getDistanceFromPrevious();
            if (leg != Distance.UNKNOWN) total += leg;
        }
        return total;
    }

    /** A readable route sheet, suitable for a console demo or a file export. */
    public String describe(List<StopPlan> plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" - ").append(plan.size()).append(" stop(s), ")
          .append(String.format("%.5f", totalDistance(plan))).append(" degrees ")
          .append(String.format("(~%.2f km straight-line estimate)",
                  Distance.toApproxKilometres(totalDistance(plan))))
          .append('\n');
        for (StopPlan stop : plan) {
            sb.append("   ").append(stop);
            if (stop.getStopSequence() > 1 && stop.getDistanceFromPrevious() != Distance.UNKNOWN) {
                sb.append(String.format("   [+%.5f deg]", stop.getDistanceFromPrevious()));
            } else if (stop.getDistanceFromPrevious() == Distance.UNKNOWN) {
                sb.append("   [no coordinates - appended at end]");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}

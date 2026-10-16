package ecoroute.algorithm;

import ecoroute.exception.CapacityExceededException;
import ecoroute.planning.AssignmentResult;
import ecoroute.planning.RequestView;
import ecoroute.planning.UnassignedRequest;
import ecoroute.planning.VehicleLoad;
import ecoroute.planning.VehicleView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STEP 2 OF THE ALGORITHM PIPELINE - THE CORE OF MEMBER 3'S CONTRIBUTION
 *
 * Decides which pickup requests go on which vehicle, without ever exceeding
 * a vehicle's capacity.
 *
 * =====================================================================
 *  WHAT PROBLEM IS THIS, REALLY?
 * =====================================================================
 * This is the BIN PACKING problem. Vehicles are bins with a fixed capacity;
 * requests are items with a weight. Pack the items into as few bins as
 * possible without overfilling any bin.
 *
 * Bin packing is NP-HARD. No known algorithm finds the guaranteed-best
 * packing in reasonable time as the input grows. That is the real,
 * principled reason for using a heuristic here - not laziness, but
 * computational complexity. Say exactly that in a viva.
 *
 * =====================================================================
 *  THE STRATEGY: FIRST-FIT DECREASING (FFD)
 * =====================================================================
 *   1. Sort requests by estimated quantity, LARGEST FIRST.
 *   2. For each request, scan the vehicles in a fixed order and put it on
 *      the FIRST one that still has enough remaining capacity.
 *   3. Reduce that vehicle's remaining capacity.
 *   4. If no vehicle has room, report the request unassigned WITH A REASON.
 *
 * WHY LARGEST FIRST?
 *   Big awkward items are the hardest to place. Placing them while every
 *   vehicle is still empty gives the most options. Small items can slot into
 *   leftover gaps afterwards. Smallest-first is the classic mistake: the
 *   trucks fill with light items and the one huge request has nowhere to go.
 *   FFD is a well-studied heuristic with a known quality bound - it never
 *   uses more than 11/9 * OPTIMAL + 6/9 bins for the classic equal-bin case.
 *   Naming FFD out loud is worth real marks.
 *
 * WHY FIRST-FIT AND NOT BEST-FIT?
 *   Both are valid. First-fit is simpler and, given a fixed vehicle order,
 *   fully deterministic. We prioritise being able to explain and reproduce
 *   the result over squeezing out marginal packing efficiency. That is a
 *   design decision we MADE, and it should be presented as one.
 *
 * =====================================================================
 *  WHAT THIS GUARANTEES, AND WHAT IT DOES NOT
 * =====================================================================
 *  GUARANTEES
 *    - FEASIBILITY: no vehicle is ever loaded beyond capacity. Enforced
 *      structurally by VehicleLoad.add(), and re-verified at the end.
 *    - COMPLETENESS: every request is either assigned or returned in the
 *      unassigned list with a specific reason. Nothing vanishes.
 *    - DETERMINISM: identical input always produces identical output.
 *
 *  DOES NOT GUARANTEE
 *    - the minimum number of vehicles
 *    - the best possible packing
 *    - anything about travel distance (that is NearestNeighborRoute's job)
 *
 *  THE COUNTER-EXAMPLE TO MEMORISE (why greedy is not optimal):
 *    Coins of 1, 3 and 4; pay 6. Greedy takes 4, then 1, then 1 = three
 *    coins. Optimal is 3 + 3 = two coins. Greedy never reconsiders an
 *    earlier choice, so a locally sensible decision can block a globally
 *    better one. Exactly the same thing can happen here with vehicles.
 *
 * =====================================================================
 *  COMPLEXITY
 * =====================================================================
 *    O(R log R) to sort the requests
 *  + O(V log V) to sort the vehicles
 *  + O(R x V)   for the assignment scan
 *  = O(R x V) dominant, where R = requests and V = vehicles.
 *
 *  At this project's scale - tens of requests per zone per day - this is
 *  instantaneous. State the scale alongside the complexity; it shows you
 *  know the difference between asymptotic cost and practical cost.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class VehicleAssignment {

    // =================================================================
    //  DETERMINISTIC ORDERINGS
    //  Every comparator here has a TIE-BREAKER. Without one, two requests
    //  of equal weight could come out in either order depending on what
    //  the database returned, and the whole run stops being reproducible.
    // =================================================================

    /**
     * Requests: heaviest first; ties broken by ascending request id.
     *
     * The tie-breaker is not optional. Collections.sort is stable, so it
     * preserves the incoming order of equal elements - but the incoming
     * order came from SQL, which without ORDER BY guarantees nothing.
     * Comparing request ids makes exactly one ordering possible, forever.
     */
    static final Comparator<RequestView> HEAVIEST_FIRST = new Comparator<RequestView>() {
        @Override
        public int compare(RequestView a, RequestView b) {
            int byQuantity = b.getTotalEstimatedQuantity()
                              .compareTo(a.getTotalEstimatedQuantity());   // b,a = descending
            if (byQuantity != 0) return byQuantity;
            return Long.compare(a.getRequestId(), b.getRequestId());       // tie-break
        }
    };

    /**
     * Vehicles: largest capacity first; ties broken by ascending vehicle id.
     *
     * Pairing "largest item first" with "largest bin first" is the natural
     * reading of First-Fit Decreasing when the bins are not identical: the
     * hardest item gets first refusal on the roomiest vehicle. Any FIXED
     * order would still be correct and deterministic - this one is simply
     * the easiest to justify.
     */
    static final Comparator<VehicleView> LARGEST_CAPACITY_FIRST = new Comparator<VehicleView>() {
        @Override
        public int compare(VehicleView a, VehicleView b) {
            int byCapacity = b.getCapacityKg().compareTo(a.getCapacityKg());  // descending
            if (byCapacity != 0) return byCapacity;
            return Long.compare(a.getVehicleId(), b.getVehicleId());          // tie-break
        }
    };

    // =================================================================
    //  SINGLE-ZONE ASSIGNMENT
    // =================================================================

    /**
     * Assign one zone's requests to the vehicles that may serve that zone.
     *
     * @param zoneId   the zone being planned
     * @param requests that zone's eligible requests (already filtered by
     *                 RequestEligibility and grouped by ZoneGrouper)
     * @param vehicles candidate vehicles; those that cannot serve this zone
     *                 are filtered out here as a safety net
     * @return a complete result: loads + unassigned, never null
     */
    public AssignmentResult assign(long zoneId,
                                   List<RequestView> requests,
                                   List<VehicleView> vehicles) {

        List<VehicleLoad> loads = new ArrayList<VehicleLoad>();
        List<UnassignedRequest> unassigned = new ArrayList<UnassignedRequest>();

        List<RequestView> workList = (requests == null)
                ? new ArrayList<RequestView>() : new ArrayList<RequestView>(requests);
        workList.remove(null);

        // ---- STEP 1: which vehicles may legally serve this zone? ----
        // Mirrors Member 2's rule in RoutePersistenceService.save():
        //   assignedZoneId == null || assignedZoneId.equals(zoneId)
        // Filtering here means we can never propose a plan she will reject.
        List<VehicleView> usable = new ArrayList<VehicleView>();
        if (vehicles != null) {
            for (VehicleView vehicle : vehicles) {
                if (vehicle == null) continue;
                if (vehicle.canServeZone(zoneId)) usable.add(vehicle);
            }
        }

        // ---- STEP 2: no vehicles at all (mandatory scenario 4) ----
        // This is a normal outcome, not an error. Every request is returned
        // with a clear reason so the manager knows exactly what happened.
        if (usable.isEmpty()) {
            for (RequestView request : workList) {
                unassigned.add(new UnassignedRequest(request,
                        UnassignedRequest.NO_VEHICLE_AVAILABLE,
                        "No available vehicle can serve zone " + zoneId + " today"));
            }
            return new AssignmentResult(zoneId, loads, unassigned);
        }

        // ---- STEP 3: deterministic ordering ----
        Collections.sort(usable, LARGEST_CAPACITY_FIRST);
        Collections.sort(workList, HEAVIEST_FIRST);

        // One load per usable vehicle. Created up front so remaining
        // capacity is tracked per vehicle across the whole scan.
        for (VehicleView vehicle : usable) {
            loads.add(new VehicleLoad(vehicle));
        }

        // The biggest vehicle in this zone. Needed to distinguish
        // "physically impossible" from "merely no room left today" - two
        // very different messages for the manager.
        BigDecimal largestCapacity = usable.get(0).getCapacityKg();

        // ---- STEP 4: the greedy scan ----
        for (RequestView request : workList) {

            BigDecimal quantity = request.getTotalEstimatedQuantity();

            // 4a. Defensive: a non-positive quantity should have been
            // filtered by RequestEligibility, but never trust input.
            if (quantity.signum() <= 0) {
                unassigned.add(new UnassignedRequest(request,
                        UnassignedRequest.INVALID_QUANTITY,
                        "Estimated quantity must be greater than zero"));
                continue;
            }

            // 4b. Larger than EVERY vehicle (mandatory scenario 2).
            // Distinguished from 4d because no amount of waiting fixes it -
            // the request must be split or an external vehicle hired.
            if (quantity.compareTo(largestCapacity) > 0) {
                unassigned.add(new UnassignedRequest(request,
                        UnassignedRequest.EXCEEDS_LARGEST_VEHICLE,
                        "Request of " + quantity + " kg exceeds the largest available vehicle ("
                                + largestCapacity + " kg) in zone " + zoneId));
                continue;
            }

            // 4c. FIRST FIT: the first vehicle with room wins.
            boolean placed = false;
            for (VehicleLoad load : loads) {
                if (load.add(request)) {     // add() refuses if it will not fit
                    placed = true;
                    break;                   // first fit - stop at the first success
                }
            }

            // 4d. It would fit on an empty vehicle, but today's fleet is
            // already committed (mandatory scenario 5). Never overload to
            // force it in.
            if (!placed) {
                unassigned.add(new UnassignedRequest(request,
                        UnassignedRequest.FLEET_CAPACITY_FULL,
                        "No vehicle in zone " + zoneId + " has " + quantity
                                + " kg of remaining capacity today"));
            }
        }

        AssignmentResult result = new AssignmentResult(zoneId, loads, unassigned);

        // ---- STEP 5: VERIFY THE INVARIANT ----
        // Should be impossible to fail, because VehicleLoad.add() refuses
        // anything that does not fit. If it ever does fail, this algorithm
        // has a bug and we must NOT hand the plan onwards: Member 2's
        // save() would reject it, or a driver would get an impossible load.
        // Failing loudly here is far better than failing quietly later.
        if (!result.allLoadsRespectCapacity()) {
            throw new CapacityExceededException(
                    "Internal error: assignment produced an overloaded vehicle in zone "
                            + zoneId + ". " + result.summary());
        }

        return result;
    }

    // =================================================================
    //  MULTI-ZONE ASSIGNMENT
    // =================================================================

    /**
     * Assign every zone in turn, from ZoneGrouper's output.
     *
     * THE SUBTLE RULE THIS HANDLES: A VEHICLE CANNOT WORK TWO ZONES
     * -------------------------------------------------------------
     * Member 2's save() sets the vehicle's status AVAILABLE -> IN_USE when a
     * route is created. So a vehicle used for zone 1 is no longer available
     * for zone 2 on the same day. If we naively assigned each zone against
     * the full fleet, a vehicle with a NULL home zone would be planned into
     * two zones at once - and the second save() would fail with
     * "Vehicle is not available."
     *
     * This method therefore removes a vehicle from the candidate pool as
     * soon as it has been given work. That is a genuine integration
     * requirement discovered by reading her service, not something stated in
     * the Member 3 work plan.
     *
     * Zones are processed in ascending id order, because ZoneGrouper returns
     * a TreeMap. That matters: whichever zone is processed first gets first
     * claim on the shared, home-zone-less vehicles. A fixed order makes that
     * competition deterministic rather than arbitrary.
     *
     * @return zoneId -> AssignmentResult, in ascending zone order
     */
    public Map<Long, AssignmentResult> assignAll(Map<Long, List<RequestView>> requestsByZone,
                                                  List<VehicleView> availableVehicles) {

        Map<Long, AssignmentResult> results = new LinkedHashMap<Long, AssignmentResult>();
        if (requestsByZone == null || requestsByZone.isEmpty()) return results;

        Set<Long> committedVehicleIds = new HashSet<Long>();

        for (Map.Entry<Long, List<RequestView>> entry : requestsByZone.entrySet()) {
            long zoneId = entry.getKey().longValue();

            // Only vehicles not yet committed to an earlier zone.
            List<VehicleView> stillFree = new ArrayList<VehicleView>();
            if (availableVehicles != null) {
                for (VehicleView vehicle : availableVehicles) {
                    if (vehicle == null) continue;
                    if (!committedVehicleIds.contains(Long.valueOf(vehicle.getVehicleId()))) {
                        stillFree.add(vehicle);
                    }
                }
            }

            AssignmentResult result = assign(zoneId, entry.getValue(), stillFree);
            results.put(entry.getKey(), result);

            // Any vehicle that actually received work is now committed.
            for (VehicleLoad load : result.getUsedLoads()) {
                committedVehicleIds.add(Long.valueOf(load.getVehicle().getVehicleId()));
            }
        }
        return results;
    }

    // =================================================================
    //  REPORTING
    // =================================================================

    /** A readable dump of one zone's assignment, for console demos. */
    public String describe(AssignmentResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.summary()).append('\n');

        for (VehicleLoad load : result.getUsedLoads()) {
            sb.append("  ").append(load).append('\n');
            for (RequestView request : load.getAssignedRequests()) {
                sb.append("      ").append(request).append('\n');
            }
        }
        if (result.getUnassignedCount() > 0) {
            sb.append("  UNASSIGNED:\n");
            for (UnassignedRequest u : result.getUnassigned()) {
                sb.append("      ").append(u).append('\n');
            }
        }
        return sb.toString();
    }
}

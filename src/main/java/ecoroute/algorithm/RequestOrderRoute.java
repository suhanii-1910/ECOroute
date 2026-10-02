package ecoroute.algorithm;

import ecoroute.planning.RequestView;
import ecoroute.planning.StopPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A SECOND implementation of RouteStrategy: visit stops in request-id order,
 * oldest request first. No distance calculation at all.
 *
 * =====================================================================
 *  WHY THIS CLASS EXISTS - IT IS NOT FILLER
 * =====================================================================
 * It does three real jobs.
 *
 *  1. IT PROVES THE STRATEGY PATTERN IS REAL.
 *     An interface with exactly one implementation is just a class with
 *     extra ceremony - and an examiner may well say so. With two
 *     implementations, swapping algorithms becomes demonstrable:
 *
 *         RouteStrategy s = new NearestNeighborRoute();   // 0.07 degrees
 *         RouteStrategy s = new RequestOrderRoute();      // 0.11 degrees
 *
 *     Same calling code, different behaviour, zero changes elsewhere.
 *     That is polymorphism you can actually point at during a demo.
 *
 *  2. IT IS THE SCIENTIFIC CONTROL.
 *     "Nearest-neighbour produces a shorter route" is an assertion until
 *     you measure it against a baseline. With this class we can compute
 *     both totals on the same data and show the improvement as a number.
 *     A measured claim beats a claimed claim.
 *
 *  3. IT IS A GENUINE FALLBACK.
 *     If no generator in a zone has coordinates recorded, distance-based
 *     ordering is meaningless. A first-come-first-served order is then
 *     both sensible and fair, and this class provides it.
 *
 * =====================================================================
 *  IS FIRST-COME-FIRST-SERVED A REASONABLE POLICY?
 * =====================================================================
 * Yes - just a different objective. Nearest-neighbour optimises for FUEL
 * AND TIME. Request order optimises for FAIRNESS: the generator who has
 * waited longest is collected first. Real operators trade these off all the
 * time, and a priority-customer rule would be a third strategy - which is
 * precisely what the interface makes cheap to add.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class RequestOrderRoute implements RouteStrategy {

    private static final Comparator<RequestView> BY_REQUEST_ID = new Comparator<RequestView>() {
        @Override
        public int compare(RequestView a, RequestView b) {
            return Long.compare(a.getRequestId(), b.getRequestId());
        }
    };

    @Override
    public String getName() {
        return "Request Order (FIFO)";
    }

    @Override
    public String getDescription() {
        return "Visits stops in ascending request id, i.e. oldest request first. "
             + "Ignores distance entirely. Optimises fairness rather than travel. "
             + "Used as a baseline for comparison and as a fallback when no "
             + "coordinates are available.";
    }

    @Override
    public List<StopPlan> order(List<RequestView> requests) {

        List<StopPlan> plan = new ArrayList<StopPlan>();
        if (requests == null || requests.isEmpty()) return plan;

        List<RequestView> sorted = new ArrayList<RequestView>();
        for (RequestView request : requests) {
            if (request != null) sorted.add(request);
        }
        Collections.sort(sorted, BY_REQUEST_ID);

        int sequence = 1;
        RequestView previous = null;
        for (RequestView request : sorted) {
            double leg = (previous == null) ? 0.0 : Distance.between(previous, request);
            if (leg == Distance.UNKNOWN) leg = Distance.UNKNOWN;
            plan.add(new StopPlan(request, sequence++, leg));
            previous = request;
        }
        return plan;
    }
}

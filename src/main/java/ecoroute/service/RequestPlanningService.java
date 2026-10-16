package ecoroute.service;

import ecoroute.planning.RequestEligibility;
import ecoroute.planning.RequestView;
import ecoroute.planning.UnassignedRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides which pickup requests are even CANDIDATES for planning on a given
 * date, and explains every request it turns away.
 *
 * WHY THE NAME IS RequestPlanningService AND NOT RequestService
 * -------------------------------------------------------------
 * The work plan lists "RequestService" as a Member 3 deliverable, but
 * Member 2's branch already contains ecoroute.service.PickupRequestService
 * handling request CRUD against the database. Two people writing classes with
 * overlapping names into one package means whoever commits second silently
 * destroys the other's work.
 *
 * So this layer is suffixed *PlanningService throughout:
 *
 *   Member 2  PickupRequestService  - persistence: create, read, update status
 *   Member 3  RequestPlanningService - planning: is this request plannable today?
 *
 * That is a real architectural distinction, not a workaround. Her services
 * answer "what is in the database?"; these answer "what should we do about
 * it?". Keeping them apart means the planning rules can be unit-tested with no
 * database at all - which is exactly why this class has zero JDBC imports.
 *
 * THIS CLASS HAS NO STATE
 * -----------------------
 * No fields, no configuration, no caching. Same inputs always produce the same
 * outputs. That makes it trivially safe to share between threads and trivially
 * easy to test, and it is the right default until something genuinely needs to
 * be remembered between calls.
 */
public final class RequestPlanningService {

    /**
     * The requests that may be planned for routeDate.
     *
     * A request qualifies only when its status is PENDING, the route date is
     * not before the date it was raised, and the route date is not before any
     * preferred pickup date. Those rules live in RequestEligibility rather
     * than here, because Member 2's RoutePersistenceService.save() enforces
     * the same conditions at the database boundary. Encoding them once, in one
     * class, is what stops the planner from confidently scheduling a route
     * that persistence will then reject.
     */
    public List<RequestView> eligible(List<RequestView> requests, LocalDate routeDate) {
        List<RequestView> result = new ArrayList<RequestView>();
        if (requests == null || routeDate == null) return result;
        for (RequestView request : requests) {
            if (request != null && RequestEligibility.isEligible(request, routeDate)) {
                result.add(request);
            }
        }
        return result;
    }

    /**
     * Every request that is NOT eligible, each carrying a machine-readable
     * reason code and a sentence a human can read.
     *
     * Returning the rejects is as important as returning the acceptances. A
     * planner that silently discards half its input is impossible to trust and
     * impossible to debug: the operator sees a short route and has no way to
     * find out what happened to the rest.
     */
    public List<UnassignedRequest> ineligible(List<RequestView> requests, LocalDate routeDate) {
        List<UnassignedRequest> result = new ArrayList<UnassignedRequest>();
        if (requests == null || routeDate == null) return result;
        for (RequestView request : requests) {
            if (request == null) continue;
            if (RequestEligibility.isEligible(request, routeDate)) continue;
            result.add(new UnassignedRequest(
                    request,
                    RequestEligibility.reasonCodeIfIneligible(request, routeDate),
                    RequestEligibility.explain(request, routeDate)));
        }
        return result;
    }

    /** Total estimated weight of a set of requests - the demand side of the day. */
    public BigDecimal totalQuantity(List<RequestView> requests) {
        BigDecimal total = BigDecimal.ZERO;
        if (requests == null) return total;
        for (RequestView request : requests) {
            if (request != null && request.getTotalEstimatedQuantity() != null) {
                total = total.add(request.getTotalEstimatedQuantity());
            }
        }
        return total;
    }

    /**
     * How many eligible requests cannot be routed geographically because their
     * generator has no coordinates.
     *
     * These are NOT excluded from planning - they are still collected, and the
     * routing strategy appends them at the end of the stop order. This count
     * exists so the operator knows the stop order is partly unoptimised, and
     * so Member 1 can see how much of WASTE_GENERATOR.latitude/longitude is
     * still unpopulated. A data-quality signal, not a failure.
     */
    public int countWithoutCoordinates(List<RequestView> requests) {
        int count = 0;
        if (requests == null) return count;
        for (RequestView request : requests) {
            if (request != null && !request.hasCoordinates()) count++;
        }
        return count;
    }

    public String describe(List<RequestView> requests, LocalDate routeDate) {
        List<RequestView> ok = eligible(requests, routeDate);
        List<UnassignedRequest> no = ineligible(requests, routeDate);
        return "Requests for " + routeDate + ": "
                + ok.size() + " eligible (" + totalQuantity(ok) + " kg), "
                + no.size() + " not considered, "
                + countWithoutCoordinates(ok) + " without coordinates.";
    }
}

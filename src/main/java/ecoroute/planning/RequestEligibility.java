package ecoroute.planning;

import java.time.LocalDate;

import static ecoroute.db.DatabaseContract.PENDING;

/**
 * Decides whether a request may be included in a plan for a given route date.
 *
 * WHY THIS CLASS EXISTS - A RULE NOBODY WROTE DOWN
 * ------------------------------------------------
 * Member 2's RoutePersistenceService.save() silently enforces a date rule
 * that appears in no work plan:
 *
 *   Validation.require(!date.isBefore(request.getRequestDate().toLocalDate())
 *       && (request.getPreferredPickupDate() == null
 *           || !date.isBefore(request.getPreferredPickupDate())),
 *       "Route is earlier than the request or preferred date.");
 *
 * Plain English: a route dated today CANNOT include a request whose preferred
 * pickup date is tomorrow. If the algorithm ignores this, it will happily
 * build a perfect plan that her service then rejects outright - and the whole
 * save fails, not just the offending stop.
 *
 * So the rule is mirrored here, BEFORE planning, and such requests are simply
 * left out and reported as DATE_NOT_DUE. This is the project's edge case
 * "Requests have different preferred pickup dates", finally given a concrete
 * meaning.
 *
 * DESIGN: a tiny class with one job. Keeping eligibility separate from
 * ZoneGrouper and VehicleAssignment means each class has a Single
 * Responsibility, and this rule can be unit-tested on its own.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class RequestEligibility {

    private RequestEligibility() { }

    /**
     * May this request be planned onto a route dated routeDate?
     *
     * All four conditions must hold:
     *   1. status is PENDING  (excludes CANCELLED - mandatory scenario 9 -
     *                          and ASSIGNED and COMPLETED)
     *   2. estimated quantity is strictly positive
     *      (Member 2's Validation.quantity(..., true, ...) requires signum > 0)
     *   3. routeDate is not before the request was raised
     *   4. routeDate is not before the preferred pickup date, when one is set
     */
    public static boolean isEligible(RequestView request, LocalDate routeDate) {
        return reasonCodeIfIneligible(request, routeDate) == null;
    }

    /**
     * @return null when the request IS eligible, otherwise the reason code
     *         explaining why it is not. Returning the reason rather than a
     *         bare boolean lets the caller build a useful UnassignedRequest
     *         instead of silently dropping the row.
     */
    public static String reasonCodeIfIneligible(RequestView request, LocalDate routeDate) {
        if (request == null) return UnassignedRequest.INVALID_QUANTITY;

        if (!PENDING.equalsIgnoreCase(request.getStatus())) {
            return UnassignedRequest.DATE_NOT_DUE;
        }
        if (request.getTotalEstimatedQuantity().signum() <= 0) {
            return UnassignedRequest.INVALID_QUANTITY;
        }
        if (routeDate == null) {
            return UnassignedRequest.DATE_NOT_DUE;
        }
        if (request.getRequestDate() != null && routeDate.isBefore(request.getRequestDate())) {
            return UnassignedRequest.DATE_NOT_DUE;
        }
        if (request.getPreferredPickupDate() != null
                && routeDate.isBefore(request.getPreferredPickupDate())) {
            return UnassignedRequest.DATE_NOT_DUE;
        }
        return null;
    }

    /** A readable sentence for the UI, matching the reason code above. */
    public static String explain(RequestView request, LocalDate routeDate) {
        String code = reasonCodeIfIneligible(request, routeDate);
        if (code == null) return "Eligible";
        if (UnassignedRequest.INVALID_QUANTITY.equals(code)) {
            return "Estimated quantity must be greater than zero";
        }
        if (request != null && !PENDING.equalsIgnoreCase(request.getStatus())) {
            return "Request status is " + request.getStatus() + ", not PENDING";
        }
        if (request != null && request.getPreferredPickupDate() != null) {
            return "Preferred pickup date " + request.getPreferredPickupDate()
                 + " is later than the route date " + routeDate;
        }
        return "Request is not due on " + routeDate;
    }
}

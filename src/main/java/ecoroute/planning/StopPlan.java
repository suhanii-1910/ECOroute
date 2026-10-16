package ecoroute.planning;

import java.math.BigDecimal;

/**
 * One stop in a planned route: a request plus the position the routing
 * strategy gave it.
 *
 * NAMING NOTE - why StopPlan and not PlannedStop
 * ----------------------------------------------
 * Member 2 already has a nested record
 * ecoroute.service.RoutePersistenceService.PlannedStop(long requestId, int sequence).
 * Two differently-shaped types with the same simple name in one codebase is a
 * recipe for an import mistake that compiles but misbehaves. Ours is named
 * StopPlan so the two can never be confused.
 *
 * This is the RICH, pre-persistence form - it carries the whole RequestView
 * so Member 4's JTable can show the generator name and quantity. Member 2's
 * record is the THIN wire format - just an id and a sequence number. The
 * conversion happens in one place, in RoutePlanStore's adapter.
 *
 * stopSequence is 1-based, not 0-based: it is shown to a human driver as
 * "stop 1, stop 2" and is stored under UNIQUE(route_id, stop_sequence).
 * Member 2 also validates that sequences are positive and ascending.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class StopPlan {

    private final RequestView request;
    private final int stopSequence;

    /** Straight-line distance in degrees from the previous stop. Diagnostic only. */
    private final double distanceFromPrevious;

    public StopPlan(RequestView request, int stopSequence, double distanceFromPrevious) {
        this.request = request;
        this.stopSequence = stopSequence;
        this.distanceFromPrevious = distanceFromPrevious;
    }

    public RequestView getRequest()         { return request; }
    public int getStopSequence()            { return stopSequence; }
    public double getDistanceFromPrevious() { return distanceFromPrevious; }

    /** Convenience accessors for Member 4's JTable. */
    public long getRequestId()       { return request.getRequestId(); }
    public String getGeneratorName() { return request.getGeneratorName(); }
    public BigDecimal getQuantity()  { return request.getTotalEstimatedQuantity(); }

    @Override
    public String toString() {
        return "Stop " + stopSequence + " -> " + request.getGeneratorName()
             + " (request#" + request.getRequestId() + ", "
             + request.getTotalEstimatedQuantity() + " kg)";
    }
}

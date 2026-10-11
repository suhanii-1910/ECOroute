package ecoroute.planning;

import ecoroute.algorithm.Distance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One vehicle's complete plan for one zone on one day: which vehicle, which
 * stops, in which order, carrying how much.
 *
 * WHY THIS CLASS EXISTS
 * ---------------------
 * Before this class, the result of planning was spread across three different
 * objects: a VehicleLoad (who carries what), a List&lt;StopPlan&gt; (in what order)
 * and a zone id floating separately. Member 4 would have had to join those
 * together himself inside the Swing layer, which is exactly the kind of
 * business logic that must not live in the UI.
 *
 * The work plan is explicit about this: "Expose service methods instead of
 * making Swing call algorithm classes directly" and "Return domain/DTO objects
 * suitable for JTable display". RoutePlan is that object. One row of the route
 * table = one RoutePlan.
 *
 * IMMUTABLE ON PURPOSE
 * --------------------
 * Every field is final and the stop list is defensively copied and wrapped
 * unmodifiable. A plan that has been produced is a decision that has been made;
 * the UI should render it, not edit it. If something needs to change, planning
 * runs again and produces a new plan. That makes the object safe to hand to any
 * layer without worrying about who might mutate it.
 *
 * DISTANCE IS COMPUTED HERE, NOT IMPORTED FROM A STRATEGY
 * -------------------------------------------------------
 * NearestNeighborRoute has its own static totalDistance() helper, and it would
 * have been tempting to call it. We deliberately do not: RoutePlan must work
 * for EVERY RouteStrategy, including ones written later. Depending on one
 * concrete strategy would quietly undo the whole point of the interface.
 * Summing the legs is four lines, so we own those four lines.
 */
public final class RoutePlan {

    private final long zoneId;
    private final VehicleView vehicle;
    private final List<StopPlan> stops;
    private final BigDecimal totalEstimatedWeight;
    private final String strategyName;

    public RoutePlan(long zoneId,
                     VehicleView vehicle,
                     List<StopPlan> stops,
                     BigDecimal totalEstimatedWeight,
                     String strategyName) {
        this.zoneId = zoneId;
        this.vehicle = vehicle;
        this.stops = Collections.unmodifiableList(
                new ArrayList<StopPlan>(stops == null ? new ArrayList<StopPlan>() : stops));
        this.totalEstimatedWeight =
                totalEstimatedWeight == null ? BigDecimal.ZERO : totalEstimatedWeight;
        this.strategyName = strategyName == null ? "unknown" : strategyName;
    }

    public long getZoneId()                    { return zoneId; }
    public VehicleView getVehicle()            { return vehicle; }
    public List<StopPlan> getStops()           { return stops; }
    public BigDecimal getTotalEstimatedWeight(){ return totalEstimatedWeight; }
    public String getStrategyName()            { return strategyName; }

    /** Convenience accessors so the UI never has to reach through to the vehicle. */
    public long getVehicleId()        { return vehicle.getVehicleId(); }
    public String getVehicleNumber()  { return vehicle.getVehicleNumber(); }
    public BigDecimal getCapacityKg() { return vehicle.getCapacityKg(); }
    public int getStopCount()         { return stops.size(); }
    public boolean isEmpty()          { return stops.isEmpty(); }

    /**
     * Total travel distance in raw degrees.
     *
     * Legs marked Distance.UNKNOWN are SKIPPED rather than added. This matters:
     * UNKNOWN is Double.MAX_VALUE, so adding even one would overflow the total
     * into nonsense. A stop whose generator has no coordinates contributes zero
     * to the distance but still appears in the route - we never silently drop a
     * stop just because we cannot measure the leg to it.
     */
    public double getTotalDistanceDegrees() {
        double total = 0.0;
        for (StopPlan stop : stops) {
            double leg = stop.getDistanceFromPrevious();
            if (leg != Distance.UNKNOWN) total += leg;
        }
        return total;
    }

    /** The same figure in approximate kilometres, for display only. */
    public double getApproxKilometres() {
        return Distance.toApproxKilometres(getTotalDistanceDegrees());
    }

    /** How many stops could not be measured, i.e. have no coordinates. */
    public int getUnlocatedStopCount() {
        int count = 0;
        for (StopPlan stop : stops) {
            if (!stop.getRequest().hasCoordinates()) count++;
        }
        return count;
    }

    /**
     * Weight utilisation as a percentage of vehicle capacity.
     *
     * Note this is WEIGHT utilisation, not volume. Recyclables are bulky and
     * light, so a vehicle at 90% by weight may already be full by volume. That
     * limitation is recorded in the assumptions document; the percentage below
     * must never be presented as "how full the truck is".
     */
    public BigDecimal getUtilisationPercent() {
        BigDecimal capacity = vehicle.getCapacityKg();
        if (capacity == null || capacity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return totalEstimatedWeight
                .multiply(new BigDecimal("100"))
                .divide(capacity, 2, java.math.RoundingMode.HALF_UP);
    }

    /** Stop ids in visiting order, e.g. "10 -> 12 -> 13" - handy for logs and the UI. */
    public String getStopOrderText() {
        if (stops.isEmpty()) return "(no stops)";
        StringBuilder sb = new StringBuilder();
        for (StopPlan stop : stops) {
            if (sb.length() > 0) sb.append(" -> ");
            sb.append(stop.getRequestId());
        }
        return sb.toString();
    }

    public String summary() {
        return "Zone " + zoneId
                + " | " + vehicle.getVehicleNumber()
                + " | " + getStopCount() + " stop(s)"
                + " | " + totalEstimatedWeight + " kg"
                + " (" + getUtilisationPercent() + "%)"
                + " | ~" + String.format("%.2f", getApproxKilometres()) + " km"
                + " | " + getStopOrderText();
    }

    @Override
    public String toString() { return summary(); }
}

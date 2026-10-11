package ecoroute.planning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete result of planning one day: every route that will run, plus
 * every request that will NOT be collected and the reason why.
 *
 * THE UNASSIGNED LIST IS NOT AN ERROR LIST
 * ----------------------------------------
 * It is the most valuable half of this object. A planner that only reports
 * successes is useless to a manager, because the manager's actual job is
 * dealing with what could not be done. The work plan requires us to "provide
 * unassigned requests with reasons", and the reasons are deliberately
 * different from one another so that each implies a different action:
 *
 *   EXCEEDS_LARGEST_VEHICLE -> split the request, or hire a bigger vehicle
 *   FLEET_CAPACITY_FULL     -> defer to tomorrow, today's fleet is genuinely full
 *   NO_VEHICLE_AVAILABLE    -> check the depot, something may be in maintenance
 *   INVALID_QUANTITY        -> the data is wrong, go back to the generator
 *   DATE_NOT_DUE            -> nothing is wrong, it simply is not due yet
 *
 * "We could not collect 150 kg from Hadapsar today" is a failure. "No vehicle
 * in zone 2 has 150 kg of remaining capacity today" is a decision someone can
 * act on. Same fact, completely different value.
 *
 * TWO SEPARATE REJECTION LISTS, ON PURPOSE
 * ----------------------------------------
 * ineligible = filtered out BEFORE planning (wrong status, not due yet).
 * unassigned = entered planning, but no vehicle could take it.
 *
 * Merging them would be convenient and wrong. A cancelled request was never a
 * candidate; a 450 kg request that no truck can carry is a capacity problem.
 * Only the second one tells you something about your fleet, and only the second
 * one should influence a decision to buy another vehicle.
 */
public final class DayPlan {

    private final LocalDate routeDate;
    private final String strategyName;
    private final List<RoutePlan> routes;
    private final List<UnassignedRequest> unassigned;
    private final List<UnassignedRequest> ineligible;

    public DayPlan(LocalDate routeDate,
                   String strategyName,
                   List<RoutePlan> routes,
                   List<UnassignedRequest> unassigned,
                   List<UnassignedRequest> ineligible) {
        this.routeDate = routeDate;
        this.strategyName = strategyName == null ? "unknown" : strategyName;
        this.routes = unmodifiableCopyOfRoutes(routes);
        this.unassigned = unmodifiableCopyOfRequests(unassigned);
        this.ineligible = unmodifiableCopyOfRequests(ineligible);
    }

    private static List<RoutePlan> unmodifiableCopyOfRoutes(List<RoutePlan> source) {
        List<RoutePlan> copy = new ArrayList<RoutePlan>();
        if (source != null) copy.addAll(source);
        return Collections.unmodifiableList(copy);
    }

    private static List<UnassignedRequest> unmodifiableCopyOfRequests(List<UnassignedRequest> source) {
        List<UnassignedRequest> copy = new ArrayList<UnassignedRequest>();
        if (source != null) copy.addAll(source);
        return Collections.unmodifiableList(copy);
    }

    public LocalDate getRouteDate()                   { return routeDate; }
    public String getStrategyName()                   { return strategyName; }
    public List<RoutePlan> getRoutes()                { return routes; }
    public List<UnassignedRequest> getUnassigned()    { return unassigned; }
    public List<UnassignedRequest> getIneligible()    { return ineligible; }

    public int getRouteCount()      { return routes.size(); }
    public int getUnassignedCount() { return unassigned.size(); }
    public int getIneligibleCount() { return ineligible.size(); }

    /** True when every request that was eligible got onto a vehicle. */
    public boolean isFullyAssigned() { return unassigned.isEmpty(); }

    public int getTotalStops() {
        int total = 0;
        for (RoutePlan route : routes) total += route.getStopCount();
        return total;
    }

    public BigDecimal getTotalPlannedWeight() {
        BigDecimal total = BigDecimal.ZERO;
        for (RoutePlan route : routes) total = total.add(route.getTotalEstimatedWeight());
        return total;
    }

    public double getTotalDistanceDegrees() {
        double total = 0.0;
        for (RoutePlan route : routes) total += route.getTotalDistanceDegrees();
        return total;
    }

    public double getTotalApproxKilometres() {
        double total = 0.0;
        for (RoutePlan route : routes) total += route.getApproxKilometres();
        return total;
    }

    /** Distinct zones that have at least one route today. */
    public List<Long> getPlannedZoneIds() {
        List<Long> zones = new ArrayList<Long>();
        for (RoutePlan route : routes) {
            Long id = Long.valueOf(route.getZoneId());
            if (!zones.contains(id)) zones.add(id);
        }
        return zones;
    }

    /** All routes belonging to one zone, in planning order. */
    public List<RoutePlan> getRoutesForZone(long zoneId) {
        List<RoutePlan> matching = new ArrayList<RoutePlan>();
        for (RoutePlan route : routes) {
            if (route.getZoneId() == zoneId) matching.add(route);
        }
        return matching;
    }

    /**
     * A full plain-text report. Member 4 can print this straight into a text
     * area, and it doubles as the console evidence for the demonstration.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("ROUTE PLAN FOR ").append(routeDate).append('\n');
        sb.append("Strategy: ").append(strategyName).append('\n');
        sb.append("------------------------------------------------------------\n");

        if (routes.isEmpty()) {
            sb.append("No routes planned.\n");
        } else {
            for (RoutePlan route : routes) {
                sb.append(route.summary()).append('\n');
            }
        }

        sb.append("------------------------------------------------------------\n");
        sb.append("Routes: ").append(getRouteCount())
          .append(" | Stops: ").append(getTotalStops())
          .append(" | Weight: ").append(getTotalPlannedWeight()).append(" kg")
          .append(" | Distance: ~").append(String.format("%.2f", getTotalApproxKilometres())).append(" km")
          .append('\n');

        if (!unassigned.isEmpty()) {
            sb.append("\nUNASSIGNED (").append(unassigned.size()).append(") - no vehicle could take these:\n");
            for (UnassignedRequest u : unassigned) {
                sb.append("  #").append(u.getRequestId())
                  .append(' ').append(u.getGeneratorName())
                  .append(' ').append(u.getQuantity()).append(" kg")
                  .append("  [").append(u.getReasonCode()).append("] ")
                  .append(u.getReasonMessage()).append('\n');
            }
        }

        if (!ineligible.isEmpty()) {
            sb.append("\nNOT CONSIDERED (").append(ineligible.size()).append(") - filtered before planning:\n");
            for (UnassignedRequest u : ineligible) {
                sb.append("  #").append(u.getRequestId())
                  .append("  [").append(u.getReasonCode()).append("] ")
                  .append(u.getReasonMessage()).append('\n');
            }
        }
        return sb.toString();
    }

    public String summary() {
        return "DayPlan[" + routeDate + " " + getRouteCount() + " route(s), "
                + getTotalStops() + " stop(s), "
                + getUnassignedCount() + " unassigned, "
                + getIneligibleCount() + " ineligible]";
    }

    @Override
    public String toString() { return summary(); }
}

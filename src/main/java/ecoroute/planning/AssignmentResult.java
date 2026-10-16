package ecoroute.planning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The complete outcome of one greedy vehicle-assignment run for ONE zone.
 *
 * It carries BOTH halves of the answer on purpose:
 *   loads      - the vehicles that got work, and what they carry
 *   unassigned - the requests that could not be placed, each with a reason
 *
 * A Java method can return only one thing, but the assignment genuinely
 * produces two related things that must travel together. A caller who saw
 * only the loads would wrongly believe every request had been handled.
 * Bundling them makes the full truth impossible to ignore. This is the
 * Result Object pattern.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class AssignmentResult {

    private final long zoneId;
    private final List<VehicleLoad> loads;
    private final List<UnassignedRequest> unassigned;

    public AssignmentResult(long zoneId,
                            List<VehicleLoad> loads,
                            List<UnassignedRequest> unassigned) {
        this.zoneId = zoneId;
        this.loads = (loads == null) ? new ArrayList<VehicleLoad>() : loads;
        this.unassigned = (unassigned == null) ? new ArrayList<UnassignedRequest>() : unassigned;
    }

    public long getZoneId() { return zoneId; }

    public List<VehicleLoad> getLoads() {
        return Collections.unmodifiableList(loads);
    }

    public List<UnassignedRequest> getUnassigned() {
        return Collections.unmodifiableList(unassigned);
    }

    /** Only the vehicles that actually received at least one request. */
    public List<VehicleLoad> getUsedLoads() {
        List<VehicleLoad> used = new ArrayList<VehicleLoad>();
        for (VehicleLoad load : loads) {
            if (!load.isEmpty()) used.add(load);
        }
        return used;
    }

    public int getAssignedRequestCount() {
        int total = 0;
        for (VehicleLoad load : loads) total += load.getStopCount();
        return total;
    }

    public int getUnassignedCount()  { return unassigned.size(); }
    public boolean isFullyAssigned() { return unassigned.isEmpty(); }

    /**
     * Total estimated weight planned across all vehicles in this zone.
     * DERIVED - computed in memory for planning and display only. Never
     * written to a ROUTE column; the schema exposes route weight through the
     * V_ROUTE_WEIGHT view.
     */
    public BigDecimal getTotalPlannedWeight() {
        BigDecimal total = BigDecimal.ZERO;
        for (VehicleLoad load : loads) total = total.add(load.getUsedCapacity());
        return total;
    }

    /**
     * Verifies the capacity invariant across every vehicle in the result.
     * If this ever returns false there is a bug in VehicleAssignment, and
     * Member 2's RoutePersistenceService would reject the save anyway.
     */
    public boolean allLoadsRespectCapacity() {
        for (VehicleLoad load : loads) {
            if (!load.respectsCapacity()) return false;
        }
        return true;
    }

    public String summary() {
        return "Zone " + zoneId + ": "
             + getAssignedRequestCount() + " request(s) assigned across "
             + getUsedLoads().size() + " vehicle(s), "
             + getUnassignedCount() + " unassigned, "
             + getTotalPlannedWeight() + " kg planned";
    }

    @Override
    public String toString() { return summary(); }
}

package ecoroute.planning;

import java.math.BigDecimal;

/**
 * A read-only view of one collection vehicle as the assignment algorithm
 * sees it. Mirrors ecoroute.model.Vehicle, minus the fields planning does
 * not use (createdDate).
 *
 * IMPORTANT: this object holds the vehicle's TOTAL capacity and never
 * changes. Remaining capacity during an assignment run is tracked separately
 * in VehicleLoad. If we mutated capacity here, running the algorithm twice on
 * the same input would start the second run from a half-full truck - a
 * classic and very confusing determinism bug.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class VehicleView {

    private final long vehicleId;
    private final String vehicleNumber;
    private final BigDecimal capacityKg;
    private final String status;

    /** Nullable - VEHICLE.assigned_zone_id is NULL-able (SET NULL on zone delete). */
    private final Long assignedZoneId;

    public VehicleView(long vehicleId,
                       String vehicleNumber,
                       BigDecimal capacityKg,
                       String status,
                       Long assignedZoneId) {
        this.vehicleId = vehicleId;
        this.vehicleNumber = vehicleNumber;
        this.capacityKg = (capacityKg == null) ? BigDecimal.ZERO : capacityKg;
        this.status = status;
        this.assignedZoneId = assignedZoneId;
    }

    public long getVehicleId()      { return vehicleId; }
    public String getVehicleNumber(){ return vehicleNumber; }
    public BigDecimal getCapacityKg(){ return capacityKg; }
    public String getStatus()       { return status; }
    public Long getAssignedZoneId() { return assignedZoneId; }

    /**
     * Can this vehicle legally serve the given zone?
     *
     * This mirrors Member 2's rule in RoutePersistenceService.save() EXACTLY:
     *
     *   Validation.require(vehicle.getAssignedZoneId() == null
     *                   || vehicle.getAssignedZoneId().equals(zoneId),
     *           "Route zone differs from the vehicle home zone.");
     *
     * So a NULL home zone means "no home-zone restriction, deployable
     * anywhere" - not "deployable nowhere". Matching her rule here means the
     * algorithm can never propose a plan her service will reject.
     */
    public boolean canServeZone(long zoneId) {
        return assignedZoneId == null || assignedZoneId == zoneId;
    }

    @Override
    public String toString() {
        return "Vehicle#" + vehicleId + " [" + vehicleNumber + "] cap "
             + capacityKg + " kg (" + status + ", home zone "
             + (assignedZoneId == null ? "ANY" : assignedZoneId) + ")";
    }
}

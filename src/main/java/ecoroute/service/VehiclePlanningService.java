package ecoroute.service;

import ecoroute.planning.VehicleView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static ecoroute.db.DatabaseContract.AVAILABLE;

/**
 * Decides which vehicles may be planned with today, and answers capacity
 * questions about the fleet as a whole.
 *
 * NAMED *PlanningService FOR THE SAME REASON AS RequestPlanningService
 * --------------------------------------------------------------------
 * Member 2's branch already contains ecoroute.service.VehicleService with
 * findAll, findAvailable, findById, create, updateStatus and updateHomeZone.
 * The work plan also assigns "VehicleService" to Member 3. Same package, same
 * filename - one of us would overwrite the other.
 *
 * Hers is vehicle administration against the database. This is vehicle
 * selection for a planning run. Different jobs, different names, no collision.
 *
 * STATUS STRINGS COME FROM DatabaseContract, NEVER FROM A LITERAL
 * ---------------------------------------------------------------
 * The import below is deliberate. Writing "AVAILABLE" as a string literal here
 * would work today and break silently the day Member 1 renames the status
 * vocabulary - the code would still compile, still run, and simply stop
 * matching any vehicle. Importing the shared constant turns that into a
 * compile error, which is the only kind of error worth having.
 */
public final class VehiclePlanningService {

    /**
     * Vehicles that may be planned with today: status AVAILABLE.
     *
     * IN_USE and MAINTENANCE are both excluded, but they mean very different
     * things operationally - IN_USE will free up, MAINTENANCE may not - which
     * is why the assignment layer reports NO_VEHICLE_AVAILABLE rather than a
     * generic failure. The distinction is what lets a manager know whether to
     * wait or to call the workshop.
     *
     * NOTE a real limitation: VEHICLE.status is a single present-tense value
     * with no date dimension, so "available" means available NOW, not available
     * on routeDate. That is why the planner runs one day at a time. Recorded as
     * assumption A8.
     */
    public List<VehicleView> available(List<VehicleView> vehicles) {
        List<VehicleView> result = new ArrayList<VehicleView>();
        if (vehicles == null) return result;
        for (VehicleView vehicle : vehicles) {
            if (vehicle != null && AVAILABLE.equalsIgnoreCase(vehicle.getStatus())) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /**
     * Available vehicles that may serve a particular zone.
     *
     * A vehicle qualifies if its assigned_zone_id matches, OR if it is null.
     * The nullable home zone is not an oversight in the schema - it models a
     * vehicle that is not tied to any ward and can be deployed anywhere, which
     * is genuinely useful when one zone is overloaded.
     */
    public List<VehicleView> servingZone(long zoneId, List<VehicleView> vehicles) {
        List<VehicleView> result = new ArrayList<VehicleView>();
        if (vehicles == null) return result;
        for (VehicleView vehicle : vehicles) {
            if (vehicle != null
                    && AVAILABLE.equalsIgnoreCase(vehicle.getStatus())
                    && vehicle.canServeZone(zoneId)) {
                result.add(vehicle);
            }
        }
        return result;
    }

    /** Combined capacity of the given vehicles - the supply side of the day. */
    public BigDecimal totalCapacity(List<VehicleView> vehicles) {
        BigDecimal total = BigDecimal.ZERO;
        if (vehicles == null) return total;
        for (VehicleView vehicle : vehicles) {
            if (vehicle != null && vehicle.getCapacityKg() != null) {
                total = total.add(vehicle.getCapacityKg());
            }
        }
        return total;
    }

    /**
     * Capacity of the single largest vehicle, or ZERO if there are none.
     *
     * This is the threshold that separates "no single vehicle can carry this"
     * from "the fleet is merely full today" - two unassigned reasons that
     * demand completely different responses from the manager.
     */
    public BigDecimal largestCapacity(List<VehicleView> vehicles) {
        BigDecimal largest = BigDecimal.ZERO;
        if (vehicles == null) return largest;
        for (VehicleView vehicle : vehicles) {
            if (vehicle == null || vehicle.getCapacityKg() == null) continue;
            if (vehicle.getCapacityKg().compareTo(largest) > 0) {
                largest = vehicle.getCapacityKg();
            }
        }
        return largest;
    }

    /**
     * Whether total fleet capacity could, in principle, cover total demand.
     *
     * READ THE WORD "COULD" CAREFULLY. This is an upper bound, not a promise.
     * Even when it returns true, individual requests may still go unassigned,
     * because capacity that is spread across several vehicles cannot be
     * combined to carry one indivisible request: three 100 kg vehicles total
     * 300 kg and still cannot take a single 150 kg load.
     *
     * That gap between "enough capacity in total" and "enough capacity in the
     * right place" is the whole reason bin packing is hard. Use this for a
     * warning to the operator, never as a guarantee.
     */
    public boolean fleetCouldCover(BigDecimal demand, List<VehicleView> vehicles) {
        if (demand == null) return true;
        return totalCapacity(vehicles).compareTo(demand) >= 0;
    }

    public String describe(List<VehicleView> vehicles) {
        List<VehicleView> free = available(vehicles);
        int total = vehicles == null ? 0 : vehicles.size();
        return "Fleet: " + free.size() + " of " + total + " available, "
                + totalCapacity(free) + " kg total capacity, "
                + "largest " + largestCapacity(free) + " kg.";
    }
}

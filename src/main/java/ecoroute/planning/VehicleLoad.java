package ecoroute.planning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One vehicle plus the requests the greedy algorithm has decided to put on
 * it. This is the "bin" in the bin-packing analogy.
 *
 * THE MOST IMPORTANT CLASS IN THE PROJECT FOR THE CAPACITY RULE
 * -------------------------------------------------------------
 * The hardest constraint in the whole work plan is "it must never exceed
 * remaining capacity." Rather than scatter that check through the algorithm
 * and hope nobody forgets it, the rule lives in ONE place - add() - and is
 * made structurally impossible to bypass:
 *
 *   - the assigned list is private, exposed only as unmodifiable
 *   - usedCapacity is recomputed by add() itself and can never be set
 *   - add() refuses and returns false rather than overloading
 *
 * That is ENCAPSULATION doing real work: the data and the rule protecting it
 * live together, so no caller can get the pair out of sync.
 *
 * WHY BigDecimal.compareTo() AND NOT equals() OR <
 * ------------------------------------------------
 * BigDecimal.equals() compares SCALE as well as value, so
 *   new BigDecimal("100.0").equals(new BigDecimal("100.00"))  ->  FALSE
 * even though both are one hundred. compareTo() compares numeric value only
 * and returns -1 / 0 / +1. Always use compareTo() on BigDecimal.
 * This is a very common exam and interview question.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class VehicleLoad {

    private final VehicleView vehicle;
    private final List<RequestView> assigned = new ArrayList<RequestView>();
    private BigDecimal usedCapacity = BigDecimal.ZERO;

    public VehicleLoad(VehicleView vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("VehicleLoad requires a vehicle");
        }
        this.vehicle = vehicle;
    }

    public VehicleView getVehicle() { return vehicle; }

    /** Unmodifiable on purpose - all changes must go through add(). */
    public List<RequestView> getAssignedRequests() {
        return Collections.unmodifiableList(assigned);
    }

    public BigDecimal getUsedCapacity()  { return usedCapacity; }
    public BigDecimal getTotalCapacity() { return vehicle.getCapacityKg(); }
    public int getStopCount()            { return assigned.size(); }
    public boolean isEmpty()             { return assigned.isEmpty(); }

    public BigDecimal getRemainingCapacity() {
        return vehicle.getCapacityKg().subtract(usedCapacity);
    }

    /**
     * Would this request fit in what is left? A pure query - changes nothing.
     *
     * compareTo(...) <= 0  means  quantity <= remaining.
     * Note that EXACTLY filling the vehicle is allowed (compareTo == 0),
     * which is mandatory test scenario 1, "exact capacity fit".
     */
    public boolean canFit(RequestView request) {
        if (request == null) return false;
        return request.getTotalEstimatedQuantity().compareTo(getRemainingCapacity()) <= 0;
    }

    /**
     * Try to load a request.
     *
     * @return true if loaded, false if it would not fit.
     *
     * Returning false rather than throwing is deliberate. In a greedy scan we
     * try vehicle after vehicle until one has room, so "does not fit" is the
     * EXPECTED result most of the time. Throwing here would be using
     * exceptions for ordinary control flow, a well-known anti-pattern, and
     * would make the algorithm unreadable.
     */
    public boolean add(RequestView request) {
        if (!canFit(request)) return false;
        assigned.add(request);
        usedCapacity = usedCapacity.add(request.getTotalEstimatedQuantity());
        return true;
    }

    /**
     * Utilisation percentage, for reports and for Member 4's display.
     * Guards against a zero-capacity vehicle, which would otherwise throw
     * ArithmeticException on divide.
     */
    public BigDecimal getUtilisationPercent() {
        BigDecimal capacity = vehicle.getCapacityKg();
        if (capacity.signum() <= 0) return BigDecimal.ZERO;
        return usedCapacity
                .multiply(new BigDecimal("100"))
                .divide(capacity, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Self-check of the capacity invariant. Should ALWAYS be true.
     * Having the object able to verify its own correctness is a persuasive
     * thing to show in a viva - the system proves its rule rather than
     * merely claiming it.
     */
    public boolean respectsCapacity() {
        return usedCapacity.compareTo(vehicle.getCapacityKg()) <= 0;
    }

    @Override
    public String toString() {
        return vehicle.getVehicleNumber() + ": " + assigned.size()
             + " request(s), " + usedCapacity + "/" + vehicle.getCapacityKg()
             + " kg (" + getUtilisationPercent() + "%)";
    }
}

package ecoroute.planning;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A read-only, pre-joined view of one pickup request, holding exactly what the
 * planning algorithms need.
 *
 * WHY THIS EXISTS INSTEAD OF USING ecoroute.model.PickupRequest DIRECTLY
 * ---------------------------------------------------------------------
 * Member 2's PickupRequest is a faithful mirror of one PICKUP_REQUEST row.
 * That row is, on its own, unusable for planning:
 *
 *   - it has NO zone        (schema rule: zone comes from the generator)
 *   - it has NO quantity    (quantities live in REQUEST_WASTE, one row per category)
 *   - it has NO coordinates (those live on WASTE_GENERATOR)
 *
 * A RequestView is that row ALREADY JOINED to its generator and with its
 * REQUEST_WASTE lines ALREADY SUMMED. The algorithms then deal with one flat
 * object and never think about joins.
 *
 * TYPE CHOICES - these matter, do not "simplify" them
 * ---------------------------------------------------
 * long for IDs          : matches Member 2's Long ids exactly.
 * BigDecimal for weight : matches Member 2's BigDecimal exactly, and is EXACT.
 *                         RoutePersistenceService.save() rejects a route with
 *                           total.compareTo(vehicle.getCapacityKg()) > 0
 *                         If we planned in double, a route we computed as
 *                         exactly 300.0 kg could arrive there as
 *                         300.00000000000006 and the whole save would fail.
 *                         Binary floating point cannot represent 0.1 exactly;
 *                         BigDecimal stores decimal digits, so it can.
 * double for lat/long   : distance is only ever used to ORDER stops, and the
 *                         work plan calls it "an academic approximation".
 *                         Exactness buys nothing here, and Math.sqrt needs
 *                         a double anyway.
 *
 * IMMUTABLE: every field is final and there are no setters, so an algorithm
 * can never corrupt its own input.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class RequestView {

    private final long requestId;
    private final long generatorId;
    private final String generatorName;
    private final long zoneId;

    /** Nullable - a generator may have no coordinates recorded. */
    private final Double latitude;
    private final Double longitude;

    /** SUM(REQUEST_WASTE.estimated_quantity) for this request, in kg. Never null. */
    private final BigDecimal totalEstimatedQuantity;

    private final String status;

    /** Date the request was raised. Used for the route-date eligibility rule. */
    private final LocalDate requestDate;

    /** Nullable - PICKUP_REQUEST.preferred_pickup_date is NULL-able. */
    private final LocalDate preferredPickupDate;

    public RequestView(long requestId,
                       long generatorId,
                       String generatorName,
                       long zoneId,
                       Double latitude,
                       Double longitude,
                       BigDecimal totalEstimatedQuantity,
                       String status,
                       LocalDate requestDate,
                       LocalDate preferredPickupDate) {
        this.requestId = requestId;
        this.generatorId = generatorId;
        this.generatorName = generatorName;
        this.zoneId = zoneId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.totalEstimatedQuantity =
                (totalEstimatedQuantity == null) ? BigDecimal.ZERO : totalEstimatedQuantity;
        this.status = status;
        this.requestDate = requestDate;
        this.preferredPickupDate = preferredPickupDate;
    }

    public long getRequestId()                    { return requestId; }
    public long getGeneratorId()                  { return generatorId; }
    public String getGeneratorName()              { return generatorName; }
    public long getZoneId()                       { return zoneId; }
    public Double getLatitude()                   { return latitude; }
    public Double getLongitude()                  { return longitude; }
    public BigDecimal getTotalEstimatedQuantity() { return totalEstimatedQuantity; }
    public String getStatus()                     { return status; }
    public LocalDate getRequestDate()             { return requestDate; }
    public LocalDate getPreferredPickupDate()     { return preferredPickupDate; }

    /**
     * True only when BOTH coordinates are present. Nearest-neighbour ordering
     * needs a usable point; a request without one must fall back to a
     * deterministic rule rather than crash on a null.
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @Override
    public String toString() {
        return "Request#" + requestId
             + " [" + generatorName + ", zone " + zoneId + "] "
             + totalEstimatedQuantity + " kg (" + status + ")";
    }
}

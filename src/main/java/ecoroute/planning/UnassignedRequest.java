package ecoroute.planning;

import java.math.BigDecimal;

/**
 * A request that could NOT be placed on any vehicle, paired with the reason.
 *
 * WHY THIS IS A RESULT AND NOT AN EXCEPTION
 * -----------------------------------------
 * The work plan says: "If fleet capacity is insufficient, leave some requests
 * unassigned with reasons" and "A request larger than every vehicle is
 * REPORTED unassignable."
 *
 * Reported, not thrown. Failing to fit is an ordinary, expected outcome of a
 * day's planning that the manager must see and act on - hire a vehicle, defer
 * the request, split it. It is not a crash and not a bug.
 *
 * Being able to articulate that distinction is a likely viva question:
 *   exception        = "this operation cannot sensibly continue"
 *   unassigned item  = "the operation succeeded; this is part of the answer"
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class UnassignedRequest {

    /** Stable reason codes, so the UI can group, colour or translate them. */
    public static final String EXCEEDS_LARGEST_VEHICLE = "EXCEEDS_LARGEST_VEHICLE";
    public static final String NO_VEHICLE_AVAILABLE    = "NO_VEHICLE_AVAILABLE";
    public static final String FLEET_CAPACITY_FULL     = "FLEET_CAPACITY_FULL";
    public static final String INVALID_QUANTITY        = "INVALID_QUANTITY";
    public static final String DATE_NOT_DUE            = "DATE_NOT_DUE";
    /**
     * The request is not in PENDING status - typically CANCELLED, or already
     * ASSIGNED or COMPLETED.
     *
     * Added in Phase 7. Until then a non-PENDING request was reported as
     * DATE_NOT_DUE, which produced the self-contradicting line
     * "[DATE_NOT_DUE] Request status is CANCELLED, not PENDING". The message
     * was right and the code was wrong, and because the UI is meant to group
     * and colour rows BY CODE, a cancelled request would have been filed under
     * "not due yet" - implying it would come back tomorrow, when in fact it is
     * never coming back. Two genuinely different outcomes need two codes.
     */
    public static final String NOT_PENDING             = "NOT_PENDING";

    private final RequestView request;
    private final String reasonCode;
    private final String reasonMessage;

    public UnassignedRequest(RequestView request, String reasonCode, String reasonMessage) {
        this.request = request;
        this.reasonCode = reasonCode;
        this.reasonMessage = reasonMessage;
    }

    public RequestView getRequest()  { return request; }
    public String getReasonCode()    { return reasonCode; }
    public String getReasonMessage() { return reasonMessage; }

    /** Convenience accessors for Member 4's JTable. */
    public long getRequestId()        { return request.getRequestId(); }
    public String getGeneratorName()  { return request.getGeneratorName(); }
    public BigDecimal getQuantity()   { return request.getTotalEstimatedQuantity(); }

    @Override
    public String toString() {
        return "Request#" + request.getRequestId()
             + " (" + request.getTotalEstimatedQuantity() + " kg) UNASSIGNED - "
             + reasonMessage;
    }
}

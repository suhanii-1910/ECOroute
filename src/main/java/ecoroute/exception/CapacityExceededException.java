package ecoroute.exception;

/**
 * Thrown when a planned vehicle load would exceed that vehicle's capacity.
 *
 * MEMBER 3'S DELIVERABLE. Member 2 already provides InvalidRequestException,
 * VehicleUnavailableException and DatabaseOperationException; this completes
 * the set required by the Member 3 work plan. It follows her style exactly -
 * extends RuntimeException, two constructors - so the exception package stays
 * internally consistent.
 *
 * WHEN IS THIS ACTUALLY THROWN? ALMOST NEVER - AND THAT IS THE POINT
 * ------------------------------------------------------------------
 * This is the single most likely thing an examiner will probe, so be precise:
 *
 *   A request that does not fit is NOT an exception. It is an ordinary,
 *   expected business outcome, reported as an UnassignedRequest carrying a
 *   reason. During a greedy scan we try vehicle after vehicle and "does not
 *   fit" is the normal answer most of the time. Throwing there would be
 *   using exceptions for control flow - a well-known anti-pattern - and
 *   would make the algorithm unreadable.
 *
 *   This exception is a DEFENSIVE GUARD against a programming error. The
 *   greedy algorithm PREVENTS overloading by construction, via
 *   VehicleLoad.add(), which refuses anything that does not fit. If a final
 *   verification sweep ever finds a load above capacity, the algorithm itself
 *   is broken. We then fail loudly and immediately rather than hand Member 2
 *   a plan that her RoutePersistenceService would reject - or worse, hand a
 *   driver a truck that cannot physically carry its load.
 *
 * WHY UNCHECKED (extends RuntimeException, not Exception)
 * -------------------------------------------------------
 * A checked exception would force every caller up the stack to catch or
 * declare it, cluttering signatures for a condition that should never occur.
 * Unchecked is correct for programming errors and broken invariants. Member 4
 * catches it once, at the button handler, and shows a dialog.
 *
 * THE VIVA ANSWER, IN ONE SENTENCE
 *   "Capacity is enforced structurally by VehicleLoad, so this exception
 *    represents a bug rather than a business condition; a request that
 *    simply does not fit is returned as an unassigned result with a reason."
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public class CapacityExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CapacityExceededException(String message) { super(message); }

    public CapacityExceededException(String message, Throwable cause) { super(message, cause); }
}

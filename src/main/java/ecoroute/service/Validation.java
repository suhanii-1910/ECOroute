package ecoroute.service;

import ecoroute.exception.InvalidRequestException;
import java.math.BigDecimal;
import static ecoroute.db.DatabaseContract.*;

final class Validation {
    private Validation() {}
    static void require(boolean condition, String message) {
        if (!condition) throw new InvalidRequestException(message);
    }
    static void text(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required.");
    }
    static void id(Long value, String field) { require(value != null && value > 0, field + " must be a positive ID."); }
    static void quantity(BigDecimal value, boolean positive, String field) {
        require(value != null, field + " is required.");
        require(positive ? value.signum() > 0 : value.signum() >= 0, field + " has an invalid sign.");
        BigDecimal normalized = value.stripTrailingZeros();
        require(normalized.scale() <= QUANTITY_SCALE && normalized.precision() - normalized.scale()
                <= QUANTITY_PRECISION - QUANTITY_SCALE, field + " must fit DECIMAL(12,3) kg.");
    }
    static void transition(String current, String expected) {
        require(expected.equals(current), "Expected state " + expected + "; found " + current + ".");
    }
}

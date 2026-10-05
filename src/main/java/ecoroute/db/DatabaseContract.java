package ecoroute.db;

import java.util.Set;

/** Existing vocabularies await Member 1 confirmation; quantity precision follows the frozen design. */
public final class DatabaseContract {
    private DatabaseContract() {}
    public static final String GENERATOR = "GENERATOR", ADMIN = "ADMIN", OPERATOR = "OPERATOR";
    public static final Set<String> ROLES = Set.of(GENERATOR, ADMIN, OPERATOR);
    public static final Set<String> GENERATOR_TYPES = Set.of("HOSPITAL", "HOUSING_SOCIETY", "FACTORY",
            "HOTEL", "SCHOOL", "RESTAURANT", "OFFICE", "OTHER");
    public static final String PENDING = "PENDING", ASSIGNED = "ASSIGNED", COMPLETED = "COMPLETED", CANCELLED = "CANCELLED";
    public static final String AVAILABLE = "AVAILABLE", IN_USE = "IN_USE", MAINTENANCE = "MAINTENANCE";
    public static final String PLANNED = "PLANNED";
    public static final Set<String> HAZARD_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    public static final int QUANTITY_SCALE = 2;
    public static final int QUANTITY_PRECISION = 12;
}

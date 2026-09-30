package ecoroute.db;

import java.util.Set;

/** PROVISIONAL values pending Member 1 approval; see docs/database_contract.md. */
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
    public static final int QUANTITY_SCALE = 3;
    public static final int QUANTITY_PRECISION = 12;
}

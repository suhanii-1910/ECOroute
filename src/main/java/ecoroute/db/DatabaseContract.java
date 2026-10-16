package ecoroute.db;

import java.util.Set;

public final class DatabaseContract {
    private DatabaseContract() {}
    public static final String GENERATOR = "GENERATOR", ADMIN = "ADMIN", OPERATOR = "OPERATOR", DRIVER = "DRIVER";
    public static final Set<String> ROLES = Set.of(GENERATOR, ADMIN, OPERATOR, DRIVER);
    public static final Set<String> GENERATOR_TYPES = Set.of("HOSPITAL", "HOUSING_SOCIETY", "FACTORY",
            "HOTEL", "SCHOOL", "RESTAURANT", "OFFICE", "OTHER");
    // Vehicle statuses
    public static final String AVAILABLE = "AVAILABLE", IN_ROUTE = "IN_ROUTE", MAINTENANCE = "MAINTENANCE";
    // Request statuses
    public static final String PENDING = "PENDING", ASSIGNED = "ASSIGNED", COMPLETED = "COMPLETED", CANCELLED = "CANCELLED";
    // Route statuses
    public static final String PLANNED = "PLANNED", IN_PROGRESS = "IN_PROGRESS",
            DISPATCHED_TO_DISPOSAL = "DISPATCHED_TO_DISPOSAL";
    // Stop statuses
    public static final String SKIPPED = "SKIPPED";
    // Hazard levels
    public static final Set<String> HAZARD_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "BIOMEDICAL");
    // Facility statuses
    public static final String ACTIVE = "ACTIVE", INACTIVE = "INACTIVE";
    // Disposal record statuses
    public static final String VERIFIED = "VERIFIED", DISCREPANCY = "DISCREPANCY", REJECTED = "REJECTED";
    public static final int QUANTITY_SCALE = 2;
    public static final int QUANTITY_PRECISION = 12;
}

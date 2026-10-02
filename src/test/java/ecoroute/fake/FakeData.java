package ecoroute.fake;

import ecoroute.planning.RequestView;
import ecoroute.planning.VehicleView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static ecoroute.db.DatabaseContract.AVAILABLE;
import static ecoroute.db.DatabaseContract.CANCELLED;
import static ecoroute.db.DatabaseContract.MAINTENANCE;
import static ecoroute.db.DatabaseContract.PENDING;

/**
 * A deliberately-designed dataset for developing and testing the planning
 * algorithms with no database running.
 *
 * WHY BigDecimal IS BUILT FROM STRINGS, NEVER FROM double
 * -------------------------------------------------------
 *     new BigDecimal(0.1)        ->  0.1000000000000000055511151231257827...
 *     new BigDecimal("0.1")      ->  0.1    exactly
 *
 * The double constructor inherits binary floating-point error, which is the
 * precise bug BigDecimal exists to avoid. Always use the String constructor
 * (or BigDecimal.valueOf). This is a classic exam question.
 *
 * THE NUMBERS ARE NOT RANDOM
 * --------------------------
 * Zone 1 (Kothrud)  vehicles: V1 500 kg, V2 300 kg, plus V5 350 kg (no home zone)
 *                   requests: R103 450, R102 200, R101 120   (total 770)
 * Zone 2 (Hadapsar) vehicles: V3 400 kg, plus V5 350 kg (no home zone)
 *                   requests: R105 380, R104 150            (total 530)
 *
 * R106 is CANCELLED and must be excluded everywhere (mandatory scenario 9).
 * V4 is in MAINTENANCE and must never be used.
 * V5 has a NULL home zone and must be usable in BOTH zones.
 *
 * Coordinates are real points around Pune, so nearest-neighbour ordering is
 * geographically meaningful and can be sanity-checked on a map.
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public final class FakeData {

    private FakeData() { }

    public static final long ZONE_KOTHRUD  = 1L;
    public static final long ZONE_HADAPSAR = 2L;

    /** Fixed date so every test run produces identical results. */
    public static final LocalDate TODAY     = LocalDate.of(2026, 10, 1);
    public static final LocalDate YESTERDAY = TODAY.minusDays(1);
    public static final LocalDate TOMORROW  = TODAY.plusDays(1);

    private static BigDecimal kg(String amount) {
        return new BigDecimal(amount);
    }

    // ------------------------------------------------------------------
    // VEHICLES
    // ------------------------------------------------------------------

    public static List<VehicleView> allVehicles() {
        List<VehicleView> v = new ArrayList<VehicleView>();
        v.add(new VehicleView(1L, "MH12-AB-1234", kg("500.000"), AVAILABLE,   Long.valueOf(ZONE_KOTHRUD)));
        v.add(new VehicleView(2L, "MH12-CD-5678", kg("300.000"), AVAILABLE,   Long.valueOf(ZONE_KOTHRUD)));
        v.add(new VehicleView(3L, "MH12-EF-9012", kg("400.000"), AVAILABLE,   Long.valueOf(ZONE_HADAPSAR)));
        v.add(new VehicleView(4L, "MH12-GH-3456", kg("250.000"), MAINTENANCE, Long.valueOf(ZONE_HADAPSAR)));
        v.add(new VehicleView(5L, "MH12-IJ-7890", kg("350.000"), AVAILABLE,   null));
        return v;
    }

    /** Only AVAILABLE vehicles - what a well-behaved port returns. */
    public static List<VehicleView> availableVehicles() {
        List<VehicleView> available = new ArrayList<VehicleView>();
        for (VehicleView v : allVehicles()) {
            if (AVAILABLE.equalsIgnoreCase(v.getStatus())) available.add(v);
        }
        return available;
    }

    // ------------------------------------------------------------------
    // REQUESTS
    // ------------------------------------------------------------------

    public static List<RequestView> allRequests() {
        List<RequestView> r = new ArrayList<RequestView>();

        // --- Zone 1 : Kothrud ---
        r.add(new RequestView(101L, 1L, "Sahyadri Hospital", ZONE_KOTHRUD,
                Double.valueOf(18.5074), Double.valueOf(73.8077),
                kg("120.000"), PENDING, YESTERDAY, TODAY));

        r.add(new RequestView(102L, 2L, "Green Meadows Society", ZONE_KOTHRUD,
                Double.valueOf(18.5100), Double.valueOf(73.8150),
                kg("200.000"), PENDING, YESTERDAY, TODAY));

        r.add(new RequestView(103L, 3L, "Pioneer Factory", ZONE_KOTHRUD,
                Double.valueOf(18.4980), Double.valueOf(73.8000),
                kg("450.000"), PENDING, YESTERDAY, TODAY));

        // --- Zone 2 : Hadapsar ---
        r.add(new RequestView(104L, 4L, "Hotel Silverleaf", ZONE_HADAPSAR,
                Double.valueOf(18.5000), Double.valueOf(73.9300),
                kg("150.000"), PENDING, YESTERDAY, TODAY));

        r.add(new RequestView(105L, 5L, "Sunrise School", ZONE_HADAPSAR,
                Double.valueOf(18.5080), Double.valueOf(73.9400),
                kg("380.000"), PENDING, YESTERDAY, TODAY));

        // --- Must be excluded from all planning (mandatory scenario 9) ---
        r.add(new RequestView(106L, 1L, "Sahyadri Hospital", ZONE_KOTHRUD,
                Double.valueOf(18.5074), Double.valueOf(73.8077),
                kg("60.000"), CANCELLED, YESTERDAY, TODAY));

        return r;
    }

    public static List<RequestView> pendingRequests() {
        List<RequestView> pending = new ArrayList<RequestView>();
        for (RequestView r : allRequests()) {
            if (PENDING.equalsIgnoreCase(r.getStatus())) pending.add(r);
        }
        return pending;
    }

    // ------------------------------------------------------------------
    // SPECIAL FIXTURES for the mandatory scenarios
    // ------------------------------------------------------------------

    /** Scenario 2: bigger than every vehicle in the fleet. */
    public static RequestView oversizedRequest() {
        return new RequestView(107L, 3L, "Pioneer Factory", ZONE_KOTHRUD,
                Double.valueOf(18.4980), Double.valueOf(73.8000),
                kg("900.000"), PENDING, YESTERDAY, TODAY);
    }

    /** Scenario 1: exactly fills vehicle 2 (capacity 300). */
    public static RequestView exactFitRequest() {
        return new RequestView(108L, 2L, "Green Meadows Society", ZONE_KOTHRUD,
                Double.valueOf(18.5100), Double.valueOf(73.8150),
                kg("300.000"), PENDING, YESTERDAY, TODAY);
    }

    /** No coordinates recorded - exercises the null-safe routing path. */
    public static RequestView requestWithoutCoordinates() {
        return new RequestView(109L, 6L, "Unmapped Warehouse", ZONE_KOTHRUD,
                null, null, kg("80.000"), PENDING, YESTERDAY, TODAY);
    }

    /** Zero quantity - must be rejected by eligibility. */
    public static RequestView zeroQuantityRequest() {
        return new RequestView(110L, 1L, "Sahyadri Hospital", ZONE_KOTHRUD,
                Double.valueOf(18.5074), Double.valueOf(73.8077),
                kg("0.000"), PENDING, YESTERDAY, TODAY);
    }

    /**
     * Preferred pickup date is TOMORROW. Must be excluded from a plan dated
     * TODAY, because Member 2's RoutePersistenceService.save() would reject
     * the entire route otherwise.
     */
    public static RequestView notDueUntilTomorrow() {
        return new RequestView(111L, 2L, "Green Meadows Society", ZONE_KOTHRUD,
                Double.valueOf(18.5100), Double.valueOf(73.8150),
                kg("90.000"), PENDING, YESTERDAY, TOMORROW);
    }

    // ------------------------------------------------------------------
    // Ad-hoc builders for one-off tests
    // ------------------------------------------------------------------

    public static RequestView request(long id, long zoneId, String quantity) {
        return new RequestView(id, id, "Generator-" + id, zoneId,
                Double.valueOf(18.50 + (id * 0.001)), Double.valueOf(73.80 + (id * 0.001)),
                kg(quantity), PENDING, YESTERDAY, TODAY);
    }

    public static RequestView requestAt(long id, long zoneId, String quantity,
                                        double latitude, double longitude) {
        return new RequestView(id, id, "Generator-" + id, zoneId,
                Double.valueOf(latitude), Double.valueOf(longitude),
                kg(quantity), PENDING, YESTERDAY, TODAY);
    }

    public static VehicleView vehicle(long id, String capacity, Long homeZone) {
        return new VehicleView(id, "TEST-" + id, kg(capacity), AVAILABLE, homeZone);
    }
}

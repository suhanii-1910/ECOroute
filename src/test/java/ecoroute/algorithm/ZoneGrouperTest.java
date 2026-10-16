package ecoroute.algorithm;

import ecoroute.fake.FakeData;
import ecoroute.harness.Check;
import ecoroute.planning.RequestEligibility;
import ecoroute.planning.RequestView;
import ecoroute.planning.VehicleLoad;
import ecoroute.planning.VehicleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PHASE 3 TEST - ZoneGrouper, plus the foundation classes it stands on.
 *
 * Run with:
 *     mvn -q clean test-compile
 *     java -cp target/classes:target/test-classes ecoroute.algorithm.ZoneGrouperTest
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public class ZoneGrouperTest {

    private ZoneGrouper grouper;

    @BeforeEach
    public void setUp() {
        Check.reset();
        grouper = new ZoneGrouper();
    }

    @Test
    public void testBasicGrouping() {
        if (grouper == null) grouper = new ZoneGrouper();
        // ==============================================================
        Check.suite("ZoneGrouper - basic grouping");
        // ==============================================================
        Map<Long, List<RequestView>> grouped = grouper.groupByZone(FakeData.pendingRequests());

        Check.eq("5 pending requests split into 2 zones", 2, grouped.size());
        Check.eq("zone 1 holds 3 requests", 3, grouped.get(1L).size());
        Check.eq("zone 2 holds 2 requests", 2, grouped.get(2L).size());
        Check.eq("no request is lost in grouping", 5, grouper.countAll(grouped));
        Check.assertNoFailures();
    }

    @Test
    public void testDeterminism() {
        if (grouper == null) grouper = new ZoneGrouper();
        // ==============================================================
        Check.suite("ZoneGrouper - DETERMINISM");
        Check.note("TreeMap guarantees zone order; the per-zone sort guarantees row order.");
        // ==============================================================
        Map<Long, List<RequestView>> grouped = grouper.groupByZone(FakeData.pendingRequests());
        List<Long> zones = grouper.zonesWithWork(grouped);
        Check.eq("zones always come out in ascending order - first is 1", 1L, zones.get(0).longValue());
        Check.eq("...and second is 2", 2L, zones.get(1).longValue());

        List<RequestView> zone1 = grouped.get(1L);
        Check.eq("zone 1 request order is 101 first", 101L, zone1.get(0).getRequestId());
        Check.eq("...then 102", 102L, zone1.get(1).getRequestId());
        Check.eq("...then 103", 103L, zone1.get(2).getRequestId());

        // Feed the SAME data in a DIFFERENT order and demand the same answer.
        List<RequestView> shuffled = new ArrayList<RequestView>();
        shuffled.add(FakeData.pendingRequests().get(4));   // 105
        shuffled.add(FakeData.pendingRequests().get(2));   // 103
        shuffled.add(FakeData.pendingRequests().get(0));   // 101
        shuffled.add(FakeData.pendingRequests().get(3));   // 104
        shuffled.add(FakeData.pendingRequests().get(1));   // 102

        Map<Long, List<RequestView>> fromShuffled = grouper.groupByZone(shuffled);
        Check.eq("a different input ORDER gives the identical zone 1 order",
                 101L, fromShuffled.get(1L).get(0).getRequestId());
        Check.eq("...confirmed for the second element",
                 102L, fromShuffled.get(1L).get(1).getRequestId());
        Check.eq("...and the third",
                 103L, fromShuffled.get(1L).get(2).getRequestId());
        Check.note("This is why a SELECT without ORDER BY cannot break the demo.");
        Check.assertNoFailures();
    }

    @Test
    public void testDefensiveInputHandling() {
        if (grouper == null) grouper = new ZoneGrouper();
        // ==============================================================
        Check.suite("ZoneGrouper - defensive input handling");
        // ==============================================================
        Check.eq("a null list returns an empty map, not a crash",
                 0, grouper.groupByZone(null).size());
        Check.eq("an empty list returns an empty map",
                 0, grouper.groupByZone(new ArrayList<RequestView>()).size());

        List<RequestView> withNull = new ArrayList<RequestView>();
        withNull.add(FakeData.request(1L, 1L, "100.000"));
        withNull.add(null);
        withNull.add(FakeData.request(2L, 1L, "100.000"));
        Check.eq("a null element is skipped, the rest still group",
                 2, grouper.countAll(grouper.groupByZone(withNull)));
        Check.assertNoFailures();
    }

    @Test
    public void testRequestEligibility() {
        // ==============================================================
        Check.suite("RequestEligibility - who may be planned today");
        // ==============================================================
        Check.isTrue("a normal PENDING request due today is eligible",
                RequestEligibility.isEligible(FakeData.request(1L, 1L, "100.000"), FakeData.TODAY));

        Check.isFalse("a CANCELLED request is NOT eligible (scenario 9)",
                RequestEligibility.isEligible(FakeData.allRequests().get(5), FakeData.TODAY));

        Check.isFalse("a zero-quantity request is NOT eligible",
                RequestEligibility.isEligible(FakeData.zeroQuantityRequest(), FakeData.TODAY));

        Check.isFalse("a request preferred for TOMORROW is NOT eligible today",
                RequestEligibility.isEligible(FakeData.notDueUntilTomorrow(), FakeData.TODAY));
        Check.note("Member 2's save() rejects the WHOLE route if this slips through.");

        Check.isTrue("...but the same request IS eligible tomorrow",
                RequestEligibility.isEligible(FakeData.notDueUntilTomorrow(), FakeData.TOMORROW));

        System.out.println();
        System.out.println("    Reason given to the user: \""
                + RequestEligibility.explain(FakeData.notDueUntilTomorrow(), FakeData.TODAY) + "\"");
        Check.assertNoFailures();
    }

    @Test
    public void testGroupEligibleByZone() {
        if (grouper == null) grouper = new ZoneGrouper();
        // ==============================================================
        Check.suite("ZoneGrouper - groupEligibleByZone filters and reports");
        // ==============================================================
        List<RequestView> candidates = new ArrayList<RequestView>(FakeData.allRequests());
        candidates.add(FakeData.notDueUntilTomorrow());
        candidates.add(FakeData.zeroQuantityRequest());

        List<RequestView> rejected = new ArrayList<RequestView>();
        Map<Long, List<RequestView>> eligible =
                grouper.groupEligibleByZone(candidates, FakeData.TODAY, rejected);

        Check.eq("8 candidates reduce to 5 eligible", 5, grouper.countAll(eligible));
        Check.eq("3 were rejected and REPORTED, not silently dropped", 3, rejected.size());
        Check.note("Cancelled 106, not-due 111, zero-quantity 110.");

        boolean cancelledLeaked = false;
        for (List<RequestView> bucket : eligible.values()) {
            for (RequestView r : bucket) {
                if (r.getRequestId() == 106L) cancelledLeaked = true;
            }
        }
        Check.isFalse("the CANCELLED request never reaches a zone bucket", cancelledLeaked);
        Check.assertNoFailures();
    }

    @Test
    public void testVehicleLoadCapacityInvariant() {
        // ==============================================================
        Check.suite("VehicleLoad - THE CAPACITY INVARIANT (BigDecimal)");
        Check.note("Mirrors Member 2's check: total.compareTo(capacity) <= 0");
        // ==============================================================
        VehicleLoad load = new VehicleLoad(FakeData.vehicle(99L, "300.000", Long.valueOf(1L)));

        Check.eqDecimal("a new load starts at zero", "0", load.getUsedCapacity());
        Check.eqDecimal("remaining starts at full capacity", "300", load.getRemainingCapacity());

        Check.isTrue("200 kg fits into 300 kg", load.add(FakeData.request(1L, 1L, "200.000")));
        Check.eqDecimal("used is now 200", "200", load.getUsedCapacity());
        Check.eqDecimal("remaining is now 100", "100", load.getRemainingCapacity());

        Check.isFalse("150 kg is REFUSED when only 100 kg remains",
                load.add(FakeData.request(2L, 1L, "150.000")));
        Check.eqDecimal("a refused add changed nothing", "200", load.getUsedCapacity());
        Check.eq("a refused add created no stop", 1, load.getStopCount());

        Check.isTrue("exactly 100 kg fits into exactly 100 kg (scenario 1)",
                load.add(FakeData.request(3L, 1L, "100.000")));
        Check.eqDecimal("the vehicle is exactly full", "300", load.getUsedCapacity());
        Check.eqDecimal("remaining is exactly zero", "0", load.getRemainingCapacity());
        Check.eqDecimal("utilisation is 100%", "100.00", load.getUtilisationPercent());
        Check.isTrue("the load respects capacity", load.respectsCapacity());

        Check.isFalse("even 0.001 kg is refused by a full vehicle",
                load.add(FakeData.request(4L, 1L, "0.001")));

        Check.throwsType("the assigned list cannot be modified from outside",
                UnsupportedOperationException.class,
                new Runnable() {
                    public void run() {
                        load.getAssignedRequests().add(FakeData.request(5L, 1L, "10.000"));
                    }
                });
        Check.assertNoFailures();
    }

    @Test
    public void testVehicleViewNullableHomeZone() {
        // ==============================================================
        Check.suite("VehicleView - the nullable home zone rule");
        Check.note("Mirrors Member 2: assignedZoneId == null || equals(zoneId)");
        // ==============================================================
        VehicleView v1 = FakeData.allVehicles().get(0);   // home zone 1
        VehicleView v5 = FakeData.allVehicles().get(4);   // home zone NULL

        Check.isTrue("vehicle 1 can serve zone 1", v1.canServeZone(1L));
        Check.isFalse("vehicle 1 cannot serve zone 2", v1.canServeZone(2L));
        Check.isNull("vehicle 5 has no home zone", v5.getAssignedZoneId());
        Check.isTrue("a NULL home zone serves zone 1", v5.canServeZone(1L));
        Check.isTrue("a NULL home zone serves zone 2", v5.canServeZone(2L));
        Check.assertNoFailures();
    }

    public static void main(String[] args) {
        Check.reset();
        ZoneGrouperTest t = new ZoneGrouperTest();
        t.grouper = new ZoneGrouper();

        t.testBasicGrouping();
        t.testDeterminism();
        t.testDefensiveInputHandling();
        t.testRequestEligibility();
        t.testGroupEligibleByZone();
        t.testVehicleLoadCapacityInvariant();
        t.testVehicleViewNullableHomeZone();

        List<RequestView> candidates = new ArrayList<RequestView>(FakeData.allRequests());
        candidates.add(FakeData.notDueUntilTomorrow());
        candidates.add(FakeData.zeroQuantityRequest());
        List<RequestView> rejected = new ArrayList<RequestView>();
        Map<Long, List<RequestView>> eligible =
                t.grouper.groupEligibleByZone(candidates, FakeData.TODAY, rejected);

        System.out.println();
        System.out.println(t.grouper.describe(eligible));

        System.exit(Check.report());
    }
}

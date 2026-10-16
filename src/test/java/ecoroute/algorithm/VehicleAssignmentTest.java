package ecoroute.algorithm;

import ecoroute.fake.FakeData;
import ecoroute.harness.Check;
import ecoroute.planning.AssignmentResult;
import ecoroute.planning.RequestView;
import ecoroute.planning.UnassignedRequest;
import ecoroute.planning.VehicleLoad;
import ecoroute.planning.VehicleView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PHASE 5 TEST - VehicleAssignment.
 *
 * Covers mandatory scenarios 1 to 6 and 9 from the Member 3 work plan.
 * Each suite is labelled with its scenario number so that during the
 * demonstration you can point directly at the line the examiner asked about.
 *
 * Run with:
 *     mvn -q clean test-compile
 *     java -cp target/classes:target/test-classes ecoroute.algorithm.VehicleAssignmentTest
 *
 * @author Member 3 - Algorithms & OOP Lead
 */
public class VehicleAssignmentTest {

    private VehicleAssignment assigner;
    private ZoneGrouper grouper;

    @BeforeEach
    public void setUp() {
        Check.reset();
        assigner = new VehicleAssignment();
        grouper = new ZoneGrouper();
    }

    @Test
    public void testScenario1ExactCapacityFit() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 1 - exact capacity fit");
        Check.note("A 300 kg request onto a 300 kg vehicle must be ACCEPTED, not rejected.");
        // ==============================================================
        List<VehicleView> oneVehicle = new ArrayList<VehicleView>();
        oneVehicle.add(FakeData.vehicle(1L, "300.000", Long.valueOf(1L)));

        List<RequestView> exact = new ArrayList<RequestView>();
        exact.add(FakeData.request(1L, 1L, "300.000"));

        AssignmentResult r1 = assigner.assign(1L, exact, oneVehicle);

        Check.eq("the request is assigned", 1, r1.getAssignedRequestCount());
        Check.eq("nothing is left unassigned", 0, r1.getUnassignedCount());
        Check.eqDecimal("remaining capacity is exactly zero", "0",
                r1.getLoads().get(0).getRemainingCapacity());
        Check.isTrue("capacity invariant holds", r1.allLoadsRespectCapacity());
        Check.assertNoFailures();
    }

    @Test
    public void testScenario2RequestLargerThanEveryVehicle() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 2 - request larger than every vehicle");
        Check.note("Must be REPORTED unassignable, not thrown as an exception.");
        // ==============================================================
        List<RequestView> tooBig = new ArrayList<RequestView>();
        tooBig.add(FakeData.oversizedRequest());          // 900 kg

        AssignmentResult r2 = assigner.assign(1L, tooBig, FakeData.availableVehicles());

        Check.eq("nothing was assigned", 0, r2.getAssignedRequestCount());
        Check.eq("exactly one request is reported unassigned", 1, r2.getUnassignedCount());
        Check.eq("the reason code is EXCEEDS_LARGEST_VEHICLE",
                UnassignedRequest.EXCEEDS_LARGEST_VEHICLE,
                r2.getUnassigned().get(0).getReasonCode());
        System.out.println("      message shown to the manager:");
        System.out.println("      \"" + r2.getUnassigned().get(0).getReasonMessage() + "\"");
        Check.assertNoFailures();
    }

    @Test
    public void testScenario3CombinedRequestsExceedOneVehicleSplitAcrossTwo() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 3 - combined requests exceed one vehicle, split across two");
        // ==============================================================
        List<VehicleView> twoVehicles = new ArrayList<VehicleView>();
        twoVehicles.add(FakeData.vehicle(1L, "300.000", Long.valueOf(1L)));
        twoVehicles.add(FakeData.vehicle(2L, "300.000", Long.valueOf(1L)));

        List<RequestView> twoBig = new ArrayList<RequestView>();
        twoBig.add(FakeData.request(1L, 1L, "250.000"));
        twoBig.add(FakeData.request(2L, 1L, "250.000"));   // 500 total, 300 per vehicle

        AssignmentResult r3 = assigner.assign(1L, twoBig, twoVehicles);

        Check.eq("both requests are assigned", 2, r3.getAssignedRequestCount());
        Check.eq("they are SPLIT across two vehicles", 2, r3.getUsedLoads().size());
        Check.eq("nothing unassigned", 0, r3.getUnassignedCount());
        Check.isTrue("neither vehicle is overloaded", r3.allLoadsRespectCapacity());
        Check.assertNoFailures();
    }

    @Test
    public void testScenario4NoVehiclesAvailableAtAll() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 4 - no vehicles available at all");
        // ==============================================================
        AssignmentResult r4 = assigner.assign(1L, FakeData.pendingRequests(),
                new ArrayList<VehicleView>());

        Check.eq("nothing assigned", 0, r4.getAssignedRequestCount());
        Check.eq("every request is reported, none silently lost", 5, r4.getUnassignedCount());
        Check.eq("reason is NO_VEHICLE_AVAILABLE",
                UnassignedRequest.NO_VEHICLE_AVAILABLE,
                r4.getUnassigned().get(0).getReasonCode());
        Check.doesNotThrow("an empty fleet does not crash the algorithm",
                new Runnable() {
                    public void run() {
                        new VehicleAssignment().assign(1L, FakeData.pendingRequests(),
                                new ArrayList<VehicleView>());
                    }
                });
        Check.assertNoFailures();
    }

    @Test
    public void testScenario5TotalDemandExceedsTotalFleetCapacity() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 5 - total demand exceeds total fleet capacity");
        Check.note("THE CRITICAL ONE. Some requests go unassigned; NOTHING is overloaded.");
        // ==============================================================
        List<VehicleView> smallFleet = new ArrayList<VehicleView>();
        smallFleet.add(FakeData.vehicle(1L, "300.000", Long.valueOf(1L)));   // 300 total capacity

        List<RequestView> tooMuch = new ArrayList<RequestView>();
        tooMuch.add(FakeData.request(1L, 1L, "200.000"));
        tooMuch.add(FakeData.request(2L, 1L, "150.000"));
        tooMuch.add(FakeData.request(3L, 1L, "100.000"));   // 450 kg demanded vs 300 available

        AssignmentResult r5 = assigner.assign(1L, tooMuch, smallFleet);

        Check.isTrue("CAPACITY IS NEVER EXCEEDED", r5.allLoadsRespectCapacity());
        Check.isTrue("some requests are left unassigned", r5.getUnassignedCount() > 0);
        Check.eq("assigned + unassigned accounts for every request", 3,
                r5.getAssignedRequestCount() + r5.getUnassignedCount());

        Check.eqDecimal("the vehicle ends up EXACTLY full - the 100 backfilled the gap",
                "300", r5.getLoads().get(0).getUsedCapacity());
        Check.eq("two requests fitted, one did not", 2, r5.getAssignedRequestCount());
        Check.eq("the SKIPPED 150 kg request is the one reported", 2L,
                r5.getUnassigned().get(0).getRequestId());
        Check.note("Skipping an item does not waste its gap - a smaller item backfills it.");
        System.out.println();
        System.out.println(indent(assigner.describe(r5)));
        Check.assertNoFailures();
    }

    @Test
    public void testScenario6MultipleFeasibleVehiclesDeterministicChoice() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 6 - multiple feasible vehicles, DETERMINISTIC choice");
        Check.note("Running the same input 50 times must give the identical answer.");
        // ==============================================================
        List<VehicleView> threeVehicles = new ArrayList<VehicleView>();
        threeVehicles.add(FakeData.vehicle(7L, "500.000", Long.valueOf(1L)));
        threeVehicles.add(FakeData.vehicle(8L, "500.000", Long.valueOf(1L)));  // tie on capacity
        threeVehicles.add(FakeData.vehicle(9L, "500.000", Long.valueOf(1L)));  // tie again

        List<RequestView> one = new ArrayList<RequestView>();
        one.add(FakeData.request(1L, 1L, "100.000"));

        long firstChoice = assigner.assign(1L, one, threeVehicles)
                                   .getUsedLoads().get(0).getVehicle().getVehicleId();

        boolean stable = true;
        for (int i = 0; i < 50; i++) {
            long choice = assigner.assign(1L, one, threeVehicles)
                                  .getUsedLoads().get(0).getVehicle().getVehicleId();
            if (choice != firstChoice) stable = false;
        }
        Check.isTrue("50 runs all picked the same vehicle", stable);
        Check.eq("and the tie-break rule picked the LOWEST vehicle id", 7L, firstChoice);
        Check.note("Equal capacity -> tie broken by vehicle id ascending.");

        // Determinism also when request weights tie.
        List<RequestView> tiedWeights = new ArrayList<RequestView>();
        tiedWeights.add(FakeData.request(30L, 1L, "100.000"));
        tiedWeights.add(FakeData.request(20L, 1L, "100.000"));
        tiedWeights.add(FakeData.request(10L, 1L, "100.000"));

        List<VehicleView> single = new ArrayList<VehicleView>();
        single.add(FakeData.vehicle(1L, "1000.000", Long.valueOf(1L)));

        AssignmentResult tiedResult = assigner.assign(1L, tiedWeights, single);
        List<RequestView> order = tiedResult.getLoads().get(0).getAssignedRequests();
        Check.eq("equal weights are ordered by request id - 10 first", 10L, order.get(0).getRequestId());
        Check.eq("...then 20", 20L, order.get(1).getRequestId());
        Check.eq("...then 30", 30L, order.get(2).getRequestId());
        Check.assertNoFailures();
    }

    @Test
    public void testFirstFitDecreasingLargestFirst() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("FIRST-FIT DECREASING - proof the sort really is largest-first");
        // ==============================================================
        List<VehicleView> ffdFleet = new ArrayList<VehicleView>();
        ffdFleet.add(FakeData.vehicle(1L, "500.000", Long.valueOf(1L)));

        List<RequestView> mixed = new ArrayList<RequestView>();
        mixed.add(FakeData.request(1L, 1L, "100.000"));
        mixed.add(FakeData.request(2L, 1L, "400.000"));
        mixed.add(FakeData.request(3L, 1L, "250.000"));

        AssignmentResult ffd = assigner.assign(1L, mixed, ffdFleet);
        List<RequestView> loaded = ffd.getLoads().get(0).getAssignedRequests();

        Check.eq("the 400 kg request was placed FIRST", 2L, loaded.get(0).getRequestId());
        Check.eq("then the 100 kg one fits in the remaining 100", 1L, loaded.get(1).getRequestId());
        Check.eq("the 250 kg request could not fit and is reported", 1, ffd.getUnassignedCount());
        Check.eq("its reason is FLEET_CAPACITY_FULL - not 'too large'",
                UnassignedRequest.FLEET_CAPACITY_FULL,
                ffd.getUnassigned().get(0).getReasonCode());
        Check.note("Two different reasons for two different problems - the manager can act on each.");
        Check.assertNoFailures();
    }

    @Test
    public void testMultiZoneVehicleNotUsedInTwoZones() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("MULTI-ZONE - a vehicle cannot be used in two zones");
        Check.note("Member 2's save() flips the vehicle to IN_USE, so it leaves the pool.");
        // ==============================================================
        List<VehicleView> sharedFleet = new ArrayList<VehicleView>();
        sharedFleet.add(FakeData.vehicle(1L, "500.000", null));   // NULL home zone - serves anywhere

        List<RequestView> twoZones = new ArrayList<RequestView>();
        twoZones.add(FakeData.request(1L, 1L, "100.000"));        // zone 1
        twoZones.add(FakeData.request(2L, 2L, "100.000"));        // zone 2

        Map<Long, List<RequestView>> grouped = grouper.groupByZone(twoZones);
        Map<Long, AssignmentResult> all = assigner.assignAll(grouped, sharedFleet);

        Check.eq("both zones produced a result", 2, all.size());
        Check.eq("zone 1 got the shared vehicle", 1, all.get(1L).getAssignedRequestCount());
        Check.eq("zone 2 could NOT reuse it", 0, all.get(2L).getAssignedRequestCount());
        Check.eq("zone 2's request is reported, not lost", 1, all.get(2L).getUnassignedCount());
        Check.eq("with reason NO_VEHICLE_AVAILABLE",
                UnassignedRequest.NO_VEHICLE_AVAILABLE,
                all.get(2L).getUnassigned().get(0).getReasonCode());
        Check.assertNoFailures();
    }

    @Test
    public void testScenario9CancelledRequestsNeverReachAssignment() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("SCENARIO 9 - cancelled requests never reach assignment");
        // ==============================================================
        List<RequestView> rejected = new ArrayList<RequestView>();
        Map<Long, List<RequestView>> eligible =
                grouper.groupEligibleByZone(FakeData.allRequests(), FakeData.TODAY, rejected);
        Map<Long, AssignmentResult> fullRun =
                assigner.assignAll(eligible, FakeData.availableVehicles());

        boolean cancelledSeen = false;
        for (AssignmentResult result : fullRun.values()) {
            for (VehicleLoad load : result.getLoads()) {
                for (RequestView request : load.getAssignedRequests()) {
                    if (request.getRequestId() == 106L) cancelledSeen = true;
                }
            }
        }
        Check.isFalse("the CANCELLED request 106 is never on any vehicle", cancelledSeen);
        Check.eq("it was filtered out before planning and reported", 1, rejected.size());
        Check.assertNoFailures();
    }

    @Test
    public void testFullRealisticRun() {
        if (assigner == null) setUp();
        // ==============================================================
        Check.suite("FULL REALISTIC RUN - the fixture dataset end to end");
        // ==============================================================
        int assignedTotal = 0;
        int unassignedTotal = 0;
        boolean allSafe = true;

        List<RequestView> rejected = new ArrayList<RequestView>();
        Map<Long, List<RequestView>> eligible =
                grouper.groupEligibleByZone(FakeData.allRequests(), FakeData.TODAY, rejected);
        Map<Long, AssignmentResult> fullRun =
                assigner.assignAll(eligible, FakeData.availableVehicles());

        for (AssignmentResult result : fullRun.values()) {
            assignedTotal   += result.getAssignedRequestCount();
            unassignedTotal += result.getUnassignedCount();
            if (!result.allLoadsRespectCapacity()) allSafe = false;
        }

        Check.isTrue("capacity invariant holds across EVERY load in EVERY zone", allSafe);
        Check.eq("assigned + unassigned equals the 5 eligible requests",
                 5, assignedTotal + unassignedTotal);
        Check.isTrue("the majority of requests are successfully assigned",
                 assignedTotal > unassignedTotal);

        System.out.println();
        for (Map.Entry<Long, AssignmentResult> entry : fullRun.entrySet()) {
            System.out.println("      ZONE " + entry.getKey() + ":");
            System.out.println(indent(assigner.describe(entry.getValue())));
        }
        Check.assertNoFailures();
    }

    public static void main(String[] args) {
        Check.reset();
        VehicleAssignmentTest t = new VehicleAssignmentTest();
        t.setUp();

        t.testScenario1ExactCapacityFit();
        t.testScenario2RequestLargerThanEveryVehicle();
        t.testScenario3CombinedRequestsExceedOneVehicleSplitAcrossTwo();
        t.testScenario4NoVehiclesAvailableAtAll();
        t.testScenario5TotalDemandExceedsTotalFleetCapacity();
        t.testScenario6MultipleFeasibleVehiclesDeterministicChoice();
        t.testFirstFitDecreasingLargestFirst();
        t.testMultiZoneVehicleNotUsedInTwoZones();
        t.testScenario9CancelledRequestsNeverReachAssignment();
        t.testFullRealisticRun();

        System.exit(Check.report());
    }

    private static String indent(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n")) sb.append("      ").append(line).append('\n');
        return sb.toString();
    }
}

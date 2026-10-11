package ecoroute.algorithm;

import ecoroute.fake.FakeData;
import ecoroute.harness.Check;
import ecoroute.planning.DayPlan;
import ecoroute.planning.RequestView;
import ecoroute.planning.RoutePlan;
import ecoroute.planning.StopPlan;
import ecoroute.planning.UnassignedRequest;
import ecoroute.planning.VehicleView;
import ecoroute.service.RequestPlanningService;
import ecoroute.service.RoutePlanningService;
import ecoroute.service.VehiclePlanningService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phase 7 - the full pipeline, end to end.
 *
 * The three algorithm classes are already covered by 107 checks of their own.
 * This suite deliberately does NOT re-test them. It tests the things that can
 * only go wrong once they are wired together:
 *
 *   - does a request ever vanish between input and report?
 *   - do stop sequences stay unique and contiguous per route?
 *   - is the same input still reproducible end to end?
 *   - does swapping the strategy actually change the outcome?
 *
 * Integration bugs live in the seams, not in the parts.
 */
public final class RoutePlanningServiceTest {

    public static void main(String[] args) {

        // =================================================================
        Check.suite("Pipeline runs end to end on the standard fixture");
        // =================================================================
        RoutePlanningService service = new RoutePlanningService();
        DayPlan plan = service.planDay(
                FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);

        Check.notNull("a plan is produced", plan);
        Check.eq("plan carries the route date", FakeData.TODAY, plan.getRouteDate());
        Check.eq("plan names the strategy", "Nearest Neighbour", plan.getStrategyName());
        Check.isTrue("at least one route was planned", plan.getRouteCount() > 0);
        Check.note(plan.report());

        // =================================================================
        Check.suite("Nothing is ever silently lost");
        // =================================================================
        // The single most important property of the whole pipeline. Every
        // request that went in must come out somewhere: on a route, in the
        // unassigned list, or in the ineligible list. A request that appears
        // in none of them is a generator nobody will ever collect from, and
        // no error message would have been produced.
        List<RequestView> input = FakeData.allRequests();
        DayPlan full = service.planDay(input, FakeData.allVehicles(), FakeData.TODAY);

        Set<Long> accountedFor = new HashSet<Long>();
        for (RoutePlan route : full.getRoutes()) {
            for (StopPlan stop : route.getStops()) {
                accountedFor.add(Long.valueOf(stop.getRequestId()));
            }
        }
        for (UnassignedRequest u : full.getUnassigned()) {
            accountedFor.add(Long.valueOf(u.getRequestId()));
        }
        for (UnassignedRequest u : full.getIneligible()) {
            accountedFor.add(Long.valueOf(u.getRequestId()));
        }
        Check.eq("every input request is accounted for", input.size(), accountedFor.size());

        // =================================================================
        Check.suite("Cancelled and not-yet-due requests never reach planning");
        // =================================================================
        // R106 is CANCELLED in the fixture. It must be filtered out before the
        // assigner ever sees it - scenario 9 of the mandatory list.
        boolean cancelledIsIneligible = false;
        for (UnassignedRequest u : full.getIneligible()) {
            if (u.getRequestId() == 106L) cancelledIsIneligible = true;
        }
        Check.isTrue("cancelled request 106 is excluded before planning", cancelledIsIneligible);

        // The reason code must say NOT_PENDING, not DATE_NOT_DUE. This check
        // exists because the pipeline originally reported the contradiction
        // "[DATE_NOT_DUE] Request status is CANCELLED, not PENDING". Since the
        // UI groups rows by CODE, a cancelled request would have been filed
        // under "not due yet" - telling the operator to expect it tomorrow,
        // when it is never coming back.
        for (UnassignedRequest u : full.getIneligible()) {
            if (u.getRequestId() == 106L) {
                Check.eq("cancelled request is coded NOT_PENDING, not DATE_NOT_DUE",
                        UnassignedRequest.NOT_PENDING, u.getReasonCode());
            }
        }


        boolean cancelledOnARoute = false;
        for (RoutePlan route : full.getRoutes()) {
            for (StopPlan stop : route.getStops()) {
                if (stop.getRequestId() == 106L) cancelledOnARoute = true;
            }
        }
        Check.isFalse("cancelled request never appears on a route", cancelledOnARoute);

        // A request whose preferred date is tomorrow must not be planned today.
        List<RequestView> withFuture = new ArrayList<RequestView>(FakeData.pendingRequests());
        withFuture.add(FakeData.notDueUntilTomorrow());
        DayPlan today = service.planDay(withFuture, FakeData.allVehicles(), FakeData.TODAY);
        boolean futureDeferred = false;
        for (UnassignedRequest u : today.getIneligible()) {
            if (u.getRequestId() == FakeData.notDueUntilTomorrow().getRequestId()) futureDeferred = true;
        }
        Check.isTrue("request due tomorrow is deferred, not planned today", futureDeferred);

        // ...and that one IS correctly coded DATE_NOT_DUE. The two codes must
        // stay distinct: "come back tomorrow" and "never coming back" are
        // different instructions to the operator.
        for (UnassignedRequest u : today.getIneligible()) {
            if (u.getRequestId() == FakeData.notDueUntilTomorrow().getRequestId()) {
                Check.eq("a genuinely not-yet-due request is still coded DATE_NOT_DUE",
                        UnassignedRequest.DATE_NOT_DUE, u.getReasonCode());
            }
        }

        // =================================================================
        Check.suite("Stop sequences are unique and contiguous 1..N per route");
        // =================================================================
        // ROUTE_STOP carries UNIQUE(route_id, stop_sequence). If the planner
        // ever emitted a duplicate, persistence would fail at insert time with
        // a constraint violation that is painful to trace back. Catching it
        // here is far cheaper - scenario 7 of the mandatory list.
        boolean allSequencesValid = true;
        for (RoutePlan route : full.getRoutes()) {
            Set<Integer> seen = new HashSet<Integer>();
            int expected = 1;
            for (StopPlan stop : route.getStops()) {
                if (stop.getStopSequence() != expected) allSequencesValid = false;
                if (!seen.add(Integer.valueOf(stop.getStopSequence()))) allSequencesValid = false;
                expected++;
            }
        }
        Check.isTrue("every route has stop_sequence 1..N with no gaps or repeats", allSequencesValid);

        // =================================================================
        Check.suite("No route exceeds its vehicle capacity");
        // =================================================================
        boolean allWithinCapacity = true;
        for (RoutePlan route : full.getRoutes()) {
            if (route.getTotalEstimatedWeight().compareTo(route.getCapacityKg()) > 0) {
                allWithinCapacity = false;
            }
        }
        Check.isTrue("planned weight never exceeds capacity on any route", allWithinCapacity);

        // =================================================================
        Check.suite("A vehicle is never planned into two zones on one day");
        // =================================================================
        // Vehicle 5 has a NULL home zone, so it is a candidate in BOTH zones.
        // If it were committed twice, the second persistence call would be
        // rejected because the first already flipped it to IN_USE.
        Set<Long> vehiclesUsed = new HashSet<Long>();
        boolean noDoubleBooking = true;
        for (RoutePlan route : full.getRoutes()) {
            if (!vehiclesUsed.add(Long.valueOf(route.getVehicleId()))) noDoubleBooking = false;
        }
        Check.isTrue("each vehicle appears on at most one route", noDoubleBooking);

        // =================================================================
        Check.suite("Unassigned requests always carry an actionable reason");
        // =================================================================
        // Scenario 5: demand exceeds fleet capacity. The fixture's zone 2 has
        // 530 kg of demand against a 400 kg vehicle, so something must be
        // refused - and the refusal has to explain itself.
        boolean everyReasonExplained = true;
        for (UnassignedRequest u : full.getUnassigned()) {
            if (u.getReasonCode() == null || u.getReasonCode().length() == 0) everyReasonExplained = false;
            if (u.getReasonMessage() == null || u.getReasonMessage().length() == 0) everyReasonExplained = false;
        }
        Check.isTrue("every unassigned request has a code and a message", everyReasonExplained);
        for (UnassignedRequest u : full.getUnassigned()) {
            Check.note("refused: #" + u.getRequestId() + " [" + u.getReasonCode() + "] " + u.getReasonMessage());
        }

        // A request bigger than every vehicle - scenario 2.
        List<RequestView> withGiant = new ArrayList<RequestView>(FakeData.pendingRequests());
        withGiant.add(FakeData.oversizedRequest());
        DayPlan giant = service.planDay(withGiant, FakeData.allVehicles(), FakeData.TODAY);
        boolean giantRefusedCorrectly = false;
        for (UnassignedRequest u : giant.getUnassigned()) {
            if (u.getRequestId() == FakeData.oversizedRequest().getRequestId()
                    && UnassignedRequest.EXCEEDS_LARGEST_VEHICLE.equals(u.getReasonCode())) {
                giantRefusedCorrectly = true;
            }
        }
        Check.isTrue("900 kg request refused as EXCEEDS_LARGEST_VEHICLE", giantRefusedCorrectly);

        // =================================================================
        Check.suite("Empty and degenerate inputs return a valid empty plan");
        // =================================================================
        // "Nothing to do" is an answer, not an error. Scenario 4.
        DayPlan noVehicles = service.planDay(
                FakeData.pendingRequests(), new ArrayList<VehicleView>(), FakeData.TODAY);
        Check.eq("no vehicles -> no routes", 0, noVehicles.getRouteCount());
        Check.isTrue("no vehicles -> every request is reported unassigned",
                noVehicles.getUnassignedCount() > 0);

        DayPlan noRequests = service.planDay(
                new ArrayList<RequestView>(), FakeData.allVehicles(), FakeData.TODAY);
        Check.eq("no requests -> no routes", 0, noRequests.getRouteCount());
        Check.eq("no requests -> nothing unassigned", 0, noRequests.getUnassignedCount());

        Check.doesNotThrow("null request list does not throw", new Runnable() {
            public void run() {
                new RoutePlanningService().planDay(null, FakeData.allVehicles(), FakeData.TODAY);
            }
        });
        Check.doesNotThrow("null vehicle list does not throw", new Runnable() {
            public void run() {
                new RoutePlanningService().planDay(FakeData.allRequests(), null, FakeData.TODAY);
            }
        });
        Check.doesNotThrow("null date does not throw", new Runnable() {
            public void run() {
                new RoutePlanningService().planDay(FakeData.allRequests(), FakeData.allVehicles(), null);
            }
        });

        // =================================================================
        Check.suite("The same input always produces the same plan");
        // =================================================================
        // Determinism is not a nicety. The examiner will run this on a
        // different machine; HashMap iteration order and unstable comparators
        // are exactly how a demo that worked last night stops working.
        DayPlan runA = service.planDay(FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);
        DayPlan runB = service.planDay(FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);
        Check.eq("same route count", runA.getRouteCount(), runB.getRouteCount());
        Check.eq("same total stops", runA.getTotalStops(), runB.getTotalStops());
        Check.eq("identical report text", runA.report(), runB.report());

        // =================================================================
        Check.suite("planZone narrows the scope without changing the rules");
        // =================================================================
        DayPlan zone1 = service.planZone(
                FakeData.ZONE_KOTHRUD, FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);
        boolean onlyZone1 = true;
        for (RoutePlan route : zone1.getRoutes()) {
            if (route.getZoneId() != FakeData.ZONE_KOTHRUD) onlyZone1 = false;
        }
        Check.isTrue("planZone(1) returns only zone 1 routes", onlyZone1);
        Check.isTrue("planZone(1) still plans something", zone1.getRouteCount() > 0);

        // =================================================================
        Check.suite("Swapping the strategy changes the result - polymorphism");
        // =================================================================
        // Identical inputs, identical pipeline, one component replaced. The
        // calling code is the same; only the number at the end differs.
        DayPlan nn = service.planDay(FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);
        DayPlan fifo = service.planDayWith(new RequestOrderRoute(),
                FakeData.allRequests(), FakeData.allVehicles(), FakeData.TODAY);

        Check.eq("both strategies plan the same number of routes",
                nn.getRouteCount(), fifo.getRouteCount());
        Check.eq("both strategies collect the same number of stops",
                nn.getTotalStops(), fifo.getTotalStops());
        Check.eq("both strategies carry the same total weight",
                nn.getTotalPlannedWeight(), fifo.getTotalPlannedWeight());
        Check.isTrue("the two strategies are named differently",
                !nn.getStrategyName().equals(fifo.getStrategyName()));

        Check.note("Nearest Neighbour  total: " + String.format("%.5f", nn.getTotalDistanceDegrees())
                + " deg (~" + String.format("%.2f", nn.getTotalApproxKilometres()) + " km)");
        Check.note("Request Order FIFO total: " + String.format("%.5f", fifo.getTotalDistanceDegrees())
                + " deg (~" + String.format("%.2f", fifo.getTotalApproxKilometres()) + " km)");

        Check.isTrue("nearest neighbour is not longer than naive request order",
                nn.getTotalDistanceDegrees() <= fifo.getTotalDistanceDegrees() + 1e-9);

        // =================================================================
        Check.suite("Supporting services answer their own questions");
        // =================================================================
        RequestPlanningService requests = new RequestPlanningService();
        VehiclePlanningService fleet = new VehiclePlanningService();

        Check.eq("5 pending requests are eligible today",
                FakeData.pendingRequests().size(),
                requests.eligible(FakeData.allRequests(), FakeData.TODAY).size());
        Check.eqDecimal("total eligible demand is 1300 kg", "1300.000",
                requests.totalQuantity(requests.eligible(FakeData.allRequests(), FakeData.TODAY)));
        Check.eq("4 of 5 vehicles are available",
                FakeData.availableVehicles().size(),
                fleet.available(FakeData.allVehicles()).size());
        Check.eqDecimal("largest available vehicle is 500 kg", "500.000",
                fleet.largestCapacity(FakeData.availableVehicles()));

        // fleetCouldCover is an upper bound, never a promise. 1550 kg of total
        // capacity "covers" 1300 kg of demand, and yet request 104 is still
        // refused - because capacity split across vehicles cannot be pooled to
        // carry one indivisible load. This assertion documents that gap.
        Check.isTrue("fleet could cover total demand on paper",
                fleet.fleetCouldCover(
                        requests.totalQuantity(requests.eligible(FakeData.allRequests(), FakeData.TODAY)),
                        FakeData.availableVehicles()));
        Check.isTrue("...and yet some requests are still unassigned",
                full.getUnassignedCount() > 0);
        Check.note("Capacity in total is not capacity in the right place - that is why bin packing is hard.");

        Check.note(fleet.describe(FakeData.allVehicles()));
        Check.note(requests.describe(FakeData.allRequests(), FakeData.TODAY));

        System.exit(Check.report());
    }
}

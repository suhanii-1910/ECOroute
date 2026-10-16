package ecoroute.service;

import ecoroute.BackendSmokeTest;
import ecoroute.exception.InvalidRequestException;
import ecoroute.model.PickupRequest;
import ecoroute.model.Route;
import ecoroute.model.RouteStop;
import ecoroute.planning.DayPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static ecoroute.db.DatabaseContract.*;
import static org.junit.jupiter.api.Assertions.*;

public class RoutePlanningAdapterTest extends BackendSmokeTest {

    private RoutePlanningAdapterService adapter;

    @BeforeEach
    void initAdapter() {
        adapter = new RoutePlanningAdapterService(database);
    }

    @Test
    void testPreviewPlanWithPendingRequests() {
        long reqId = request(generatorId, categoryId, "40.000");

        DayPlan plan = adapter.previewPlan(operator, LocalDate.now(), zoneId);

        assertNotNull(plan);
        assertEquals(LocalDate.now(), plan.getRouteDate());
        assertEquals(1, plan.getRouteCount());
        assertEquals(1, plan.getTotalStops());
        assertEquals(reqId, plan.getRoutes().get(0).getStops().get(0).getRequestId());
        assertEquals(1, plan.getRoutes().get(0).getStops().get(0).getStopSequence());
        assertEquals(0, plan.getUnassignedCount());
    }

    @Test
    void testPlanAndSaveAtomicallyPersistsRoutesAndStops() {
        long reqId1 = request(generatorId, categoryId, "30.000");
        long reqId2 = requests.create(admin, generatorId, LocalDate.now(), "Second request",
                List.of(new PickupRequestService.WasteLine(secondCategoryId, new BigDecimal("25.000"))));

        List<Long> routeIds = adapter.planAndSave(operator, LocalDate.now(), zoneId);

        assertEquals(1, routeIds.size());
        long routeId = routeIds.get(0);

        // Verify route persisted
        Route persistedRoute = routes.findById(operator, routeId).orElseThrow();
        assertEquals(PLANNED, persistedRoute.getStatus());
        assertEquals(vehicleId, persistedRoute.getVehicleId());
        assertEquals(zoneId, persistedRoute.getZoneId());

        // Verify stops persisted
        List<RouteStop> stops = routes.findStops(operator, routeId);
        assertEquals(2, stops.size());
        assertEquals(1, stops.get(0).getStopSequence());
        assertEquals(2, stops.get(1).getStopSequence());

        // Verify requests transitioned to ASSIGNED
        PickupRequest r1 = requests.findById(generatorUser, reqId1).orElseThrow();
        PickupRequest r2 = requests.findById(admin, reqId2).orElseThrow();
        assertEquals(ASSIGNED, r1.getStatus());
        assertEquals(ASSIGNED, r2.getStatus());
    }

    @Test
    void testDoubleBookingPreventionExcludesVehiclesWithActiveRoutes() {
        request(generatorId, categoryId, "30.000");

        // Plan and save first route -> commits vehicleId to a PLANNED route
        List<Long> saved = adapter.planAndSave(operator, LocalDate.now(), zoneId);
        assertFalse(saved.isEmpty());

        // Create a new pending request
        long reqId2 = requests.create(admin, generatorId, LocalDate.now(), "Another request",
                List.of(new PickupRequestService.WasteLine(categoryId, new BigDecimal("20.000"))));

        // Preview again: since the only vehicle is now PLANNED, no vehicle is available
        DayPlan secondPlan = adapter.previewPlan(operator, LocalDate.now(), zoneId);
        assertEquals(0, secondPlan.getRouteCount());
        assertEquals(1, secondPlan.getUnassignedCount());
        assertEquals(reqId2, secondPlan.getUnassigned().get(0).getRequestId());
    }

    @Test
    void testZoneScopingOnlyPlansSpecifiedZone() {
        long reqZone1 = request(generatorId, categoryId, "20.000");
        assertTrue(reqZone1 > 0);
        long reqZone2 = requests.create(admin, otherGeneratorId, LocalDate.now(), "Zone 2 req",
                List.of(new PickupRequestService.WasteLine(categoryId, new BigDecimal("25.000"))));

        // Plan specifically for otherZoneId where no vehicle is assigned
        DayPlan planOtherZone = adapter.previewPlan(operator, LocalDate.now(), otherZoneId);

        // Vehicle TEST-1 is assigned to zoneId (North), not otherZoneId (South)
        // So in otherZoneId, reqZone2 cannot be assigned to TEST-1
        assertEquals(0, planOtherZone.getRouteCount());
        assertEquals(1, planOtherZone.getUnassignedCount());
        assertEquals(reqZone2, planOtherZone.getUnassigned().get(0).getRequestId());
    }

    @Test
    void testOversizedRequestReportedAsUnassigned() {
        // Vehicle TEST-1 has capacity 100 kg. Create 150 kg request.
        long oversizedReq = request(generatorId, categoryId, "150.000");

        DayPlan plan = adapter.previewPlan(operator, LocalDate.now(), zoneId);
        assertEquals(0, plan.getRouteCount());
        assertEquals(1, plan.getUnassignedCount());
        assertEquals(oversizedReq, plan.getUnassigned().get(0).getRequestId());
        assertEquals("EXCEEDS_LARGEST_VEHICLE", plan.getUnassigned().get(0).getReasonCode());
    }

    @Test
    void testGeneratorDeniedPlanningAccess() {
        assertThrows(InvalidRequestException.class, () -> adapter.previewPlan(generatorUser, LocalDate.now(), zoneId));
        assertThrows(InvalidRequestException.class, () -> adapter.planAndSave(generatorUser, LocalDate.now(), zoneId));
    }
}

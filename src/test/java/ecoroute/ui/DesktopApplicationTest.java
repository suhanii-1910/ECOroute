package ecoroute.ui;

import ecoroute.BackendSmokeTest;
import ecoroute.service.UserSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

import static ecoroute.db.DatabaseContract.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration and sanity tests for the Swing desktop application:
 * - AppContext DI container and session listeners
 * - MainFrame construction and view registration
 * - Role-based sidebar navigation and access control
 * - Form validation and view rendering on Swing Event Dispatch Thread (EDT)
 */
public class DesktopApplicationTest extends BackendSmokeTest {

    private AppContext context;

    private UserSession driverSession;

    @BeforeEach
    void initContext() {
        context = new AppContext(database, "Test H2 Fixture");
        seedUser("driver_test", DRIVER, null);
        driverSession = new ecoroute.service.AuthService(database).login("driver_test", PASSWORD.toCharArray());
    }

    private void runOnEdt(Runnable r) throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeAndWait(r);
        }
    }

    @Test
    void testMainFrameInitializesAllViewsOnEdt() throws Exception {
        runOnEdt(() -> {
            UITheme.setupFlatLaf();
            MainFrame frame = new MainFrame(context);
            assertNotNull(frame);
            assertEquals("ECOroute — Waste Routing & Management System", frame.getTitle());
            assertTrue(frame.getMinimumSize().width >= 1024);

            // Set Admin session
            context.setCurrentSession(admin);
            assertEquals(admin, context.getCurrentSession());

            // Switch between views
            frame.showView("dashboard");
            frame.showView("generator");
            frame.showView("admin");
            frame.showView("planning");
            frame.showView("driver");
            frame.showView("disposal");
            frame.showView("reports");

            frame.dispose();
        });
    }

    @Test
    void testRoleBasedNavigationFiltering() throws Exception {
        runOnEdt(() -> {
            MainFrame frame = new MainFrame(context);

            // 1. Driver Session
            context.setCurrentSession(driverSession);
            assertEquals(DRIVER, context.getCurrentSession().getRole());

            // 2. Generator Session
            context.setCurrentSession(generatorUser);
            assertEquals(GENERATOR, context.getCurrentSession().getRole());
            assertEquals(generatorId, context.getCurrentSession().getGeneratorId());

            // 3. Operator Session
            context.setCurrentSession(operator);
            assertEquals(OPERATOR, context.getCurrentSession().getRole());

            // 4. Admin Session
            context.setCurrentSession(admin);
            assertEquals(ADMIN, context.getCurrentSession().getRole());

            frame.dispose();
        });
    }

    @Test
    void testLoginDialogAuthenticationFlow() throws Exception {
        runOnEdt(() -> {
            LoginDialog dialog = new LoginDialog((Frame) null, context);
            assertNotNull(dialog);
            assertFalse(dialog.isAuthenticated());
            dialog.dispose();
        });
    }

    @Test
    void testIndividualViewsRenderWithoutExceptions() throws Exception {
        runOnEdt(() -> {
            context.setCurrentSession(admin);

            DashboardView dashboard = new DashboardView(context);
            assertNotNull(dashboard);

            GeneratorView generator = new GeneratorView(context);
            assertNotNull(generator);

            AdminView adminView = new AdminView(context);
            assertNotNull(adminView);

            OperatorPlanningView planning = new OperatorPlanningView(context);
            assertNotNull(planning);

            DriverView driver = new DriverView(context);
            assertNotNull(driver);

            DisposalView disposal = new DisposalView(context);
            assertNotNull(disposal);

            ReportsView reports = new ReportsView(context);
            assertNotNull(reports);
        });
    }

    @Test
    void testSignOutWorkflowResetsSessionAndProtectedViews() throws Exception {
        runOnEdt(() -> {
            MainFrame frame = new MainFrame(context);

            // Log in as Generator
            context.setCurrentSession(generatorUser);
            assertTrue(frame.isNavVisible("generator"));
            assertTrue(frame.isNavVisible("dashboard"));
            assertFalse(frame.isNavVisible("admin"));

            // Perform sign out / logout
            context.logout();
            assertNull(context.getCurrentSession());

            // Verify all navigation buttons are hidden for guest
            assertFalse(frame.isNavVisible("generator"));
            assertFalse(frame.isNavVisible("admin"));
            assertFalse(frame.isNavVisible("planning"));
            assertFalse(frame.isNavVisible("driver"));
            assertFalse(frame.isNavVisible("disposal"));
            assertFalse(frame.isNavVisible("reports"));
            assertFalse(frame.isNavVisible("dashboard"));

            // Verify resetAllViews executes cleanly without exception
            assertDoesNotThrow(frame::resetAllViews);

            frame.dispose();
        });
    }

    @Test
    void testRoleBasedNavigationStrictRestrictions() throws Exception {
        runOnEdt(() -> {
            MainFrame frame = new MainFrame(context);

            // 1. ADMIN: Can access admin, planning, driver, disposal, reports, dashboard, but NOT generator!
            context.setCurrentSession(admin);
            assertTrue(frame.isNavVisible("admin"), "Admin view must be visible for ADMIN");
            assertTrue(frame.isNavVisible("planning"), "Planning view must be visible for ADMIN");
            assertTrue(frame.isNavVisible("driver"), "Driver view must be visible for ADMIN");
            assertTrue(frame.isNavVisible("disposal"), "Disposal view must be visible for ADMIN");
            assertTrue(frame.isNavVisible("reports"), "Reports view must be visible for ADMIN");
            assertTrue(frame.isNavVisible("dashboard"), "Dashboard view must be visible for ADMIN");
            assertFalse(frame.isNavVisible("generator"), "Generator portal must be HIDDEN for ADMIN (requires linked generator account)");

            // 2. GENERATOR: Can access generator portal and dashboard only
            context.setCurrentSession(generatorUser);
            assertTrue(frame.isNavVisible("generator"), "Generator view must be visible for GENERATOR");
            assertTrue(frame.isNavVisible("dashboard"), "Dashboard view must be visible for GENERATOR");
            assertFalse(frame.isNavVisible("admin"), "Admin view must be hidden for GENERATOR");
            assertFalse(frame.isNavVisible("planning"), "Planning view must be hidden for GENERATOR");
            assertFalse(frame.isNavVisible("driver"), "Driver view must be hidden for GENERATOR");
            assertFalse(frame.isNavVisible("disposal"), "Disposal view must be hidden for GENERATOR");
            assertFalse(frame.isNavVisible("reports"), "Reports view must be hidden for GENERATOR");

            // 3. OPERATOR: Can access planning, driver, disposal, reports, dashboard
            context.setCurrentSession(operator);
            assertTrue(frame.isNavVisible("planning"), "Planning view must be visible for OPERATOR");
            assertTrue(frame.isNavVisible("driver"), "Driver view must be visible for OPERATOR");
            assertTrue(frame.isNavVisible("disposal"), "Disposal view must be visible for OPERATOR");
            assertTrue(frame.isNavVisible("reports"), "Reports view must be visible for OPERATOR");
            assertTrue(frame.isNavVisible("dashboard"), "Dashboard view must be visible for OPERATOR");
            assertFalse(frame.isNavVisible("admin"), "Admin view must be hidden for OPERATOR");
            assertFalse(frame.isNavVisible("generator"), "Generator portal must be hidden for OPERATOR");

            // 4. DRIVER: Can access driver collection and disposal views
            context.setCurrentSession(driverSession);
            assertTrue(frame.isNavVisible("driver"), "Driver view must be visible for DRIVER");
            assertTrue(frame.isNavVisible("disposal"), "Disposal view must be visible for DRIVER");
            assertTrue(frame.isNavVisible("dashboard"), "Dashboard view must be visible for DRIVER");
            assertFalse(frame.isNavVisible("admin"), "Admin view must be hidden for DRIVER");
            assertFalse(frame.isNavVisible("planning"), "Planning view must be hidden for DRIVER");
            assertFalse(frame.isNavVisible("generator"), "Generator portal must be hidden for DRIVER");
            assertFalse(frame.isNavVisible("reports"), "Reports view must be hidden for DRIVER");

            frame.dispose();
        });
    }

    @Test
    void testEndToEndOperationalLifecycle() throws Exception {
        java.time.LocalDate routeDate = java.time.LocalDate.now();

        // 1. Generator creates pickup request
        long reqId = requests.create(
                generatorUser,
                generatorId,
                routeDate,
                "End-to-end integration pickup test",
                java.util.List.of(
                        new ecoroute.service.PickupRequestService.WasteLine(categoryId, new java.math.BigDecimal("25.000")),
                        new ecoroute.service.PickupRequestService.WasteLine(secondCategoryId, new java.math.BigDecimal("15.000"))
                )
        );
        assertTrue(reqId > 0);
        ecoroute.model.PickupRequest req = requests.findById(generatorUser, reqId).orElseThrow();
        assertEquals(PENDING, req.getStatus());

        // 2. Planning: RoutePlanningAdapterService previews and plans route for today
        ecoroute.service.RoutePlanningAdapterService adapter = new ecoroute.service.RoutePlanningAdapterService(database);
        ecoroute.planning.DayPlan plan = adapter.previewPlan(operator, routeDate, zoneId);
        assertNotNull(plan);
        assertEquals(1, plan.getRouteCount());
        assertEquals(1, plan.getTotalStops());
        assertEquals(reqId, plan.getRoutes().get(0).getStops().get(0).getRequestId());
        assertEquals(1, plan.getRoutes().get(0).getStops().get(0).getStopSequence());
        assertEquals(0, plan.getUnassignedCount());

        // 3. Atomically persist route to DB
        java.util.List<Long> routeIds = adapter.planAndSave(operator, routeDate, zoneId);
        assertEquals(1, routeIds.size());
        long savedRouteId = routeIds.get(0);

        ecoroute.model.Route loadedRoute = routes.findById(operator, savedRouteId).orElseThrow();
        assertEquals(PLANNED, loadedRoute.getStatus());

        // 4. Operator / Driver starts route -> IN_PROGRESS, vehicle -> IN_ROUTE, requests -> IN_ROUTE
        collections.startRoute(driverSession, savedRouteId);
        assertEquals(IN_PROGRESS, routes.findById(operator, savedRouteId).orElseThrow().getStatus());

        // 5. Driver collection: complete stop with actual collected weights
        java.util.List<ecoroute.model.RouteStop> stops = routes.findStops(operator, savedRouteId);
        assertEquals(1, stops.size());
        long stopId = stops.get(0).getStopId();

        java.util.Map<Long, java.math.BigDecimal> actuals = java.util.Map.of(
                categoryId, new java.math.BigDecimal("26.500"),
                secondCategoryId, new java.math.BigDecimal("14.000")
        );
        ecoroute.model.PickupRequest createdReq = requests.findById(generatorUser, reqId).orElseThrow();
        java.time.LocalDateTime arrival = createdReq.getRequestDate();
        java.time.LocalDateTime completion = java.time.LocalDateTime.now();
        if (completion.isBefore(arrival)) {
            completion = arrival;
        }
        collections.complete(driverSession, stopId, actuals, arrival, completion);

        // Verify stop and request are COMPLETED, and route transitioned to DISPATCHED_TO_DISPOSAL
        assertEquals(COMPLETED, requests.findById(generatorUser, reqId).orElseThrow().getStatus());
        assertEquals(DISPATCHED_TO_DISPOSAL, routes.findById(operator, savedRouteId).orElseThrow().getStatus());

        // 6. Disposal: Facility check-in and weigh-in
        ecoroute.service.DisposalService disposalService = new ecoroute.service.DisposalService(database);
        long facId = database.transaction(c -> {
            long fid = new ecoroute.dao.DisposalFacilityDAO(c).insert(
                    new ecoroute.model.DisposalFacility(null, "E2E Facility", "RECYCLING_PLANT", "10 Test Way", 28.7, 77.1, new java.math.BigDecimal("50.00"), ACTIVE, null)
            );
            new ecoroute.dao.FacilityAcceptedWasteDAO(c).insert(new ecoroute.model.FacilityAcceptedWaste(fid, categoryId, new java.math.BigDecimal("1.50")));
            new ecoroute.dao.FacilityAcceptedWasteDAO(c).insert(new ecoroute.model.FacilityAcceptedWaste(fid, secondCategoryId, new java.math.BigDecimal("2.00")));
            return fid;
        });
        long dispRecordId = disposalService.recordDisposal(
                admin,
                savedRouteId,
                facId,
                new java.math.BigDecimal("2040.50"),
                new java.math.BigDecimal("2000.00"),
                "RCP-E2E-" + (System.currentTimeMillis() % 100000)
        );
        assertTrue(dispRecordId > 0);

        // Verify route is COMPLETED and vehicle is AVAILABLE
        assertEquals(COMPLETED, routes.findById(operator, savedRouteId).orElseThrow().getStatus());
        ecoroute.model.Vehicle v = new ecoroute.dao.VehicleDAO(anchor).findById(vehicleId).orElseThrow();
        assertEquals(AVAILABLE, v.getStatus());

        // 7. Reporting: Query volume and verify database aggregation
        ecoroute.service.ReportService reportService = new ecoroute.service.ReportService(database);
        java.util.List<ecoroute.dao.ReportDAO.CollectionVolume> volumes = reportService.collectionVolume(admin, routeDate.minusDays(1), routeDate.plusDays(1));
        assertFalse(volumes.isEmpty(), "Reports must aggregate recorded collection volumes");
    }

    @Test
    void testGeneratorRequestCancellationWorkflow() throws Exception {
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);

        // 1. Create request
        long reqId = requests.create(
                generatorUser,
                generatorId,
                tomorrow,
                "Request to be cancelled",
                java.util.List.of(new ecoroute.service.PickupRequestService.WasteLine(categoryId, new java.math.BigDecimal("10.000")))
        );
        assertEquals(PENDING, requests.findById(generatorUser, reqId).orElseThrow().getStatus());

        // 2. Cancel request
        requests.cancel(generatorUser, reqId);
        assertEquals(CANCELLED, requests.findById(generatorUser, reqId).orElseThrow().getStatus());

        // 3. Cancelling an already cancelled request throws InvalidRequestException
        assertThrows(ecoroute.exception.InvalidRequestException.class, () -> requests.cancel(generatorUser, reqId));
    }

    @Test
    void testAdminCrudOperationsPersistToDatabase() throws Exception {
        ecoroute.service.CatalogService catalogService = new ecoroute.service.CatalogService(database);
        ecoroute.service.VehicleService vehicleService = new ecoroute.service.VehicleService(database);
        ecoroute.service.AuthService authService = new ecoroute.service.AuthService(database);

        // 1. Create Zone
        long newZoneId = catalogService.saveZone(admin, new ecoroute.model.Zone(null, "Test Admin Zone " + System.currentTimeMillis(), "Desc"));
        assertTrue(newZoneId > 0);
        assertTrue(catalogService.zones(admin).stream().anyMatch(z -> z.getZoneId().equals(newZoneId)));

        // 2. Create Category
        long newCatId = catalogService.saveCategory(admin, new ecoroute.model.WasteCategory(null, "Test Category " + System.currentTimeMillis(), "Desc", "LOW"));
        assertTrue(newCatId > 0);
        assertTrue(catalogService.categories(admin).stream().anyMatch(c -> c.getCategoryId().equals(newCatId)));

        // 3. Create Vehicle
        long newVehId = vehicleService.create(admin, "VEH-" + (System.currentTimeMillis() % 100000), new java.math.BigDecimal("1500.00"), newZoneId);
        assertTrue(newVehId > 0);
        assertTrue(vehicleService.findById(admin, newVehId).isPresent());

        // 4. Create User and Deactivate
        String testUser = "temp_user_" + (System.currentTimeMillis() % 100000);
        long newUserId = authService.createUser(admin, new ecoroute.model.User(null, testUser, "Staff", DRIVER, null, true, java.time.LocalDateTime.now()), "Password@123".toCharArray());
        assertTrue(newUserId > 0);

        // Login works
        assertNotNull(authService.login(testUser, "Password@123".toCharArray()));

        // Deactivate
        authService.deactivate(admin, newUserId);

        // Login fails for deactivated user
        assertThrows(ecoroute.exception.InvalidRequestException.class, () -> authService.login(testUser, "Password@123".toCharArray()));
    }

    @Test
    void testMixedCategoryMultiFacilityDisposalWorkflow() throws Exception {
        java.time.LocalDate routeDate = java.time.LocalDate.now();

        // 1. Create a hospital generator with biomedical authorization
        long hospGenId = database.transaction(c -> {
            long gid = new ecoroute.dao.GeneratorDAO(c).insert(
                    new ecoroute.model.WasteGenerator(null, "City General Hospital", "HOSPITAL", null,
                            null, null, "1 Hospital Rd", java.math.BigDecimal.valueOf(28.7041), java.math.BigDecimal.valueOf(77.1025), zoneId, true, java.time.LocalDateTime.now())
            );
            new ecoroute.dao.GeneratorSubtypeDAO(c).insert(new ecoroute.model.Hospital(gid, "LIC-HOSP-001", "BIO-AUTH-001"));
            return gid;
        });

        // 2. Create waste categories: General Waste (LOW) and Biomedical Waste (BIOMEDICAL)
        long generalCatId = categoryId; // From fixture (General Waste / Paper)
        long bioCatId = database.transaction(c ->
                new ecoroute.dao.CategoryDAO(c).insert(new ecoroute.model.WasteCategory(null, "Biomedical Waste", "Clinical biohazard", "BIOMEDICAL"))
        );

        // 3. Generator creates pickup request containing 50 kg General Waste and 120 kg Biomedical Waste
        long reqId = requests.create(
                admin,
                hospGenId,
                routeDate,
                "Mixed general and clinical biomedical waste collection",
                java.util.List.of(
                        new ecoroute.service.PickupRequestService.WasteLine(generalCatId, new java.math.BigDecimal("50.000")),
                        new ecoroute.service.PickupRequestService.WasteLine(bioCatId, new java.math.BigDecimal("120.000"))
                )
        );
        assertTrue(reqId > 0);

        // 4. Seed a high-capacity vehicle in zoneId (170 kg > default 100 kg test vehicle) and plan route
        long highCapVehId = database.transaction(c -> new ecoroute.dao.VehicleDAO(c).insert(
                new ecoroute.model.Vehicle(null, "BIO-TRUCK-" + (System.currentTimeMillis() % 100000), new java.math.BigDecimal("2500.00"), AVAILABLE, zoneId)
        ));
        assertTrue(highCapVehId > 0);

        ecoroute.service.RoutePlanningAdapterService adapter = new ecoroute.service.RoutePlanningAdapterService(database);
        java.util.List<Long> plannedRouteIds = adapter.planAndSave(operator, routeDate, zoneId);
        assertFalse(plannedRouteIds.isEmpty());
        long routeId = plannedRouteIds.get(0);

        // 5. Driver dispatches and starts route
        collections.startRoute(driverSession, routeId);
        assertEquals(IN_PROGRESS, routes.findById(operator, routeId).orElseThrow().getStatus());

        // 6. Complete collection stop with actual weights (50 kg general, 120 kg biomedical)
        java.util.List<ecoroute.model.RouteStop> stops = routes.findStops(operator, routeId);
        assertEquals(1, stops.size());
        long stopId = stops.get(0).getStopId();

        collections.complete(
                driverSession,
                stopId,
                java.util.Map.of(generalCatId, new java.math.BigDecimal("50.000"), bioCatId, new java.math.BigDecimal("120.000")),
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        );
        assertEquals(DISPATCHED_TO_DISPOSAL, routes.findById(operator, routeId).orElseThrow().getStatus());

        // 7. Setup distinct specialized facilities
        // Facility A: North Recycling Plant (accepts General, rejects Biomedical)
        long recyclingFacId = database.transaction(c -> {
            long fid = new ecoroute.dao.DisposalFacilityDAO(c).insert(
                    new ecoroute.model.DisposalFacility(null, "North Recycling Plant", "RECYCLING_PLANT", "10 Industrial Rd", 28.72, 77.09, new java.math.BigDecimal("50.00"), ACTIVE, null)
            );
            new ecoroute.dao.FacilityAcceptedWasteDAO(c).insert(new ecoroute.model.FacilityAcceptedWaste(fid, generalCatId, new java.math.BigDecimal("1.50")));
            return fid;
        });

        // Facility B: East Hazardous Disposal Site (accepts Biomedical, rejects General)
        long hazardousFacId = database.transaction(c -> {
            long fid = new ecoroute.dao.DisposalFacilityDAO(c).insert(
                    new ecoroute.model.DisposalFacility(null, "East Hazardous Disposal Site", "HAZARDOUS_DISPOSAL_SITE", "15 Chemical Zone", 28.61, 77.40, new java.math.BigDecimal("30.00"), ACTIVE, null)
            );
            new ecoroute.dao.FacilityAcceptedWasteDAO(c).insert(new ecoroute.model.FacilityAcceptedWaste(fid, bioCatId, new java.math.BigDecimal("12.00")));
            return fid;
        });

        // Facility C: Chemical Only Plant (accepts neither General nor Biomedical)
        long chemicalCatId = database.transaction(c ->
                new ecoroute.dao.CategoryDAO(c).insert(new ecoroute.model.WasteCategory(null, "Chemical Solvent", "Flammable solvent", "HIGH"))
        );
        long incompatibleFacId = database.transaction(c -> {
            long fid = new ecoroute.dao.DisposalFacilityDAO(c).insert(
                    new ecoroute.model.DisposalFacility(null, "Industrial Solvent Incinerator", "PROCESSING_PLANT", "80 Tech Park", 28.50, 77.30, new java.math.BigDecimal("20.00"), ACTIVE, null)
            );
            new ecoroute.dao.FacilityAcceptedWasteDAO(c).insert(new ecoroute.model.FacilityAcceptedWaste(fid, chemicalCatId, new java.math.BigDecimal("9.00")));
            return fid;
        });

        ecoroute.service.DisposalService disposalService = new ecoroute.service.DisposalService(database);

        // 8. Verify initial route remaining categories & compatibility checks
        java.util.Set<Long> remainingBefore = disposalService.getRemainingCategories(admin, routeId);
        assertEquals(java.util.Set.of(generalCatId, bioCatId), remainingBefore);

        // Incompatible facility validation
        ecoroute.service.DisposalService.FacilityCompatibility incomp = disposalService.evaluateFacilityCompatibility(admin, routeId, incompatibleFacId);
        assertFalse(incomp.compatible(), "Incompatible facility must not be accepted");
        assertThrows(ecoroute.exception.InvalidRequestException.class, () ->
                disposalService.recordDisposal(admin, routeId, incompatibleFacId, new java.math.BigDecimal("2100.00"), new java.math.BigDecimal("2000.00"), "RCP-INCOMP-1")
        );

        // Compatibility of Recycling Plant (accepts General, leaves Biomedical)
        ecoroute.service.DisposalService.FacilityCompatibility recComp = disposalService.evaluateFacilityCompatibility(admin, routeId, recyclingFacId);
        assertTrue(recComp.compatible());
        assertEquals(0, recComp.expectedNetWeightKg().compareTo(new java.math.BigDecimal("50.00")));
        assertTrue(recComp.otherRemainingCatIds().contains(bioCatId));

        // 9. Check-in First at North Recycling Plant for General Waste (50 kg)
        long dispRecId = disposalService.recordDisposal(
                admin,
                routeId,
                recyclingFacId,
                new java.math.BigDecimal("2050.00"),
                new java.math.BigDecimal("2000.00"),
                "RCP-MIXED-GEN"
        );
        assertTrue(dispRecId > 0);

        // Crucial validation: Route must NOT be COMPLETED yet, vehicle must NOT be AVAILABLE
        ecoroute.model.Route intermediateRoute = routes.findById(operator, routeId).orElseThrow();
        assertEquals(DISPATCHED_TO_DISPOSAL, intermediateRoute.getStatus(),
                "Route must remain in DISPATCHED_TO_DISPOSAL because biomedical waste is still on board");
        assertEquals(IN_ROUTE, new ecoroute.dao.VehicleDAO(anchor).findById(intermediateRoute.getVehicleId()).orElseThrow().getStatus(),
                "Vehicle must remain IN_ROUTE while un-disposed waste remains");

        // 10. Check state after first disposal
        java.util.Set<Long> remainingAfterFirst = disposalService.getRemainingCategories(admin, routeId);
        assertEquals(java.util.Set.of(bioCatId), remainingAfterFirst);

        // Recycling plant is no longer compatible (already disposed general waste)
        ecoroute.service.DisposalService.FacilityCompatibility recComp2 = disposalService.evaluateFacilityCompatibility(admin, routeId, recyclingFacId);
        assertFalse(recComp2.compatible(), "Recycling plant cannot be reused when its categories are already disposed");
        assertThrows(ecoroute.exception.InvalidRequestException.class, () ->
                disposalService.recordDisposal(admin, routeId, recyclingFacId, new java.math.BigDecimal("2050.00"), new java.math.BigDecimal("2000.00"), "RCP-DUPLICATE")
        );

        // East Hazardous is now recommended and fully compatible for remaining biomedical waste
        ecoroute.service.DisposalService.FacilityCompatibility hazComp = disposalService.evaluateFacilityCompatibility(admin, routeId, hazardousFacId);
        assertTrue(hazComp.compatible());
        assertEquals(0, hazComp.expectedNetWeightKg().compareTo(new java.math.BigDecimal("120.00")));
        assertTrue(hazComp.otherRemainingCatIds().isEmpty(), "Hazardous site should clear all remaining categories");

        java.util.Optional<ecoroute.model.DisposalFacility> recommended = disposalService.selectNearestSuitableFacility(admin, routeId, 28.7041, 77.1025);
        assertTrue(recommended.isPresent());
        assertEquals(hazardousFacId, recommended.get().getFacilityId().longValue());

        // 11. Check-in Second at East Hazardous Disposal Site for Biomedical Waste (120 kg)
        long dispBioId = disposalService.recordDisposal(
                admin,
                routeId,
                hazardousFacId,
                new java.math.BigDecimal("2120.00"),
                new java.math.BigDecimal("2000.00"),
                "RCP-MIXED-BIO"
        );
        assertTrue(dispBioId > 0);

        // 12. Verification of final route & vehicle completion
        ecoroute.model.Route finalRoute = routes.findById(operator, routeId).orElseThrow();
        assertEquals(COMPLETED, finalRoute.getStatus(), "Route must transition to COMPLETED once all categories are disposed");
        assertEquals(AVAILABLE, new ecoroute.dao.VehicleDAO(anchor).findById(finalRoute.getVehicleId()).orElseThrow().getStatus(),
                "Vehicle must be returned to AVAILABLE pool");

        // 13. Audit receipts
        java.util.List<ecoroute.model.DisposalRecord> records = disposalService.findRecordsByRoute(admin, routeId);
        assertEquals(2, records.size(), "Both disposal receipts must be preserved in DISPOSAL_RECORD");
        assertTrue(records.stream().anyMatch(r -> r.getDisposalId().equals(dispRecId) && r.getNetDisposedWeightKg().compareTo(new java.math.BigDecimal("50.00")) == 0));
        assertTrue(records.stream().anyMatch(r -> r.getDisposalId().equals(dispBioId) && r.getNetDisposedWeightKg().compareTo(new java.math.BigDecimal("120.00")) == 0));

        // Most recent record query
        assertTrue(disposalService.findRecord(admin, routeId).isPresent());
        assertEquals(dispBioId, disposalService.findRecord(admin, routeId).get().getDisposalId().longValue());
    }

    @Test
    void testDriverCollectionDialogDisplaysActualWasteCategoryNames() throws Exception {
        runOnEdt(() -> {
            context.setCurrentSession(driverSession);

            DriverView driverView = new DriverView(context);
            assertNotNull(driverView);

            // Mapped categories: General Waste and Biomedical Waste
            long genCatId = context.getCatalogService().saveCategory(admin, new ecoroute.model.WasteCategory(null, "General Waste", null, "LOW"));
            long bioCatId = context.getCatalogService().saveCategory(admin, new ecoroute.model.WasteCategory(null, "Biomedical Waste", null, "HIGH"));
            long unmappedCatId = 999999L;

            ecoroute.model.RouteStop stop = new ecoroute.model.RouteStop(101L, 1L, 501L, 1, PENDING, null, null);
            java.util.List<ecoroute.model.RequestWaste> lines = java.util.List.of(
                    new ecoroute.model.RequestWaste(501L, genCatId, new java.math.BigDecimal("50.00"), null),
                    new ecoroute.model.RequestWaste(501L, bioCatId, new java.math.BigDecimal("120.00"), null),
                    new ecoroute.model.RequestWaste(501L, unmappedCatId, new java.math.BigDecimal("15.00"), null)
            );

            JDialog dialog = driverView.createCollectionDialog(stop, lines);
            assertNotNull(dialog);
            assertEquals("Record Waste Collection — Stop #1", dialog.getTitle());

            JTable table = findTable(dialog.getContentPane());
            assertNotNull(table, "Collection dialog must contain a JTable for waste lines");
            assertEquals(3, table.getRowCount(), "Table must have 3 rows for the 3 waste lines");

            // Row 0: General Waste (mapped)
            assertEquals("General Waste", table.getValueAt(0, 0), "Mapped category ID must resolve to actual human-readable name");
            assertEquals("50.00 kg", table.getValueAt(0, 1));
            assertEquals("50.00", table.getValueAt(0, 2));

            // Row 1: Biomedical Waste (mapped)
            assertEquals("Biomedical Waste", table.getValueAt(1, 0), "Mapped category ID must resolve to actual human-readable name");
            assertEquals("120.00 kg", table.getValueAt(1, 1));
            assertEquals("120.00", table.getValueAt(1, 2));

            // Row 2: Unmapped category (graceful fallback to Category #id)
            assertEquals("Category #999999", table.getValueAt(2, 0), "Unmapped category ID must fall back gracefully to Category #id");
            assertEquals("15.00 kg", table.getValueAt(2, 1));
            assertEquals("15.00", table.getValueAt(2, 2));

            dialog.dispose();
        });
    }

    private static JTable findTable(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTable) return (JTable) c;
            if (c instanceof JScrollPane sp) {
                if (sp.getViewport().getView() instanceof JTable t) return t;
            }
            if (c instanceof Container child) {
                JTable t = findTable(child);
                if (t != null) return t;
            }
        }
        return null;
    }
}

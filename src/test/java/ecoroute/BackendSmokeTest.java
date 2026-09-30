package ecoroute;

import ecoroute.dao.*;
import ecoroute.db.*;
import ecoroute.exception.*;
import ecoroute.model.*;
import ecoroute.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static ecoroute.db.DatabaseContract.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs real JDBC against an isolated H2 fixture by default; MySqlBackendIT reuses the same checks. */
public class BackendSmokeTest {
    protected ConnectionProvider provider;
    protected Connection anchor;
    protected Database database;
    protected UserSession admin, operator, generatorUser;
    protected long zoneId, otherZoneId, generatorId, otherGeneratorId, categoryId, secondCategoryId, vehicleId;
    protected PickupRequestService requests;
    protected RoutePersistenceService routes;
    protected CollectionService collections;
    protected static final String PASSWORD = "student-test-password";
    private static final String HASH = PasswordHasher.hash(PASSWORD.toCharArray());

    protected ConnectionProvider createProvider() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;LOCK_TIMEOUT=5000";
        return () -> DriverManager.getConnection(url, "sa", "");
    }
    protected void prepareFixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/member2_fixture.sql")) {
            assertNotNull(input);
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) try (Statement s = anchor.createStatement()) { s.execute(statement); }
            }
        }
    }
    @BeforeEach
    void setUp() throws Exception {
        provider = createProvider();
        anchor = provider.open();
        database = new Database(provider);
        prepareFixture();
        zoneId = database.transaction(c -> new ZoneDAO(c).insert(new Zone(null, "North", null)));
        otherZoneId = database.transaction(c -> new ZoneDAO(c).insert(new Zone(null, "South", "Other zone")));
        generatorId = seedGenerator("Office", zoneId);
        otherGeneratorId = seedGenerator("School", otherZoneId);
        categoryId = database.transaction(c -> new CategoryDAO(c).insert(new WasteCategory(null, "Paper", null, "LOW")));
        secondCategoryId = database.transaction(c -> new CategoryDAO(c).insert(new WasteCategory(null, "Plastic", null, "LOW")));
        vehicleId = seedVehicle("TEST-1");
        sql("INSERT INTO `USER` (username,password_hash,role,generator_id,is_active,created_date) VALUES (?,?,?,?,?,?)",
                "admin", HASH, ADMIN, null, true, LocalDateTime.now());
        sql("INSERT INTO `USER` (username,password_hash,role,generator_id,is_active,created_date) VALUES (?,?,?,?,?,?)",
                "operator", HASH, OPERATOR, null, true, LocalDateTime.now());
        sql("INSERT INTO `USER` (username,password_hash,role,generator_id,is_active,created_date) VALUES (?,?,?,?,?,?)",
                "generator", HASH, GENERATOR, generatorId, true, LocalDateTime.now());
        AuthService auth = new AuthService(database);
        admin = auth.login("admin", PASSWORD.toCharArray());
        operator = auth.login("operator", PASSWORD.toCharArray());
        generatorUser = auth.login("generator", PASSWORD.toCharArray());
        requests = new PickupRequestService(database);
        routes = new RoutePersistenceService(database);
        collections = new CollectionService(database);
    }
    @AfterEach
    void tearDown() throws Exception { if (anchor != null) anchor.close(); }

    protected long seedGenerator(String name, long zone) {
        return database.transaction(c -> new GeneratorDAO(c).insert(new WasteGenerator(null, name, "OFFICE", null,
                null, null, null, null, null, zone, true, LocalDateTime.now())));
    }
    protected long seedVehicle(String number) {
        return database.transaction(c -> new VehicleDAO(c).insert(new Vehicle(null, number, new BigDecimal("100"),
                AVAILABLE, zoneId, LocalDateTime.now())));
    }
    protected void sql(String query, Object... args) {
        database.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement(query)) {
                for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
                s.executeUpdate();
            }
            return null;
        });
    }
    protected long count(String table) {
        // Only constant test-owned table names are passed here.
        return database.read(c -> {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
                r.next(); return r.getLong(1);
            }
        });
    }
    protected long request(long generator, long category, String kg) {
        return requests.create(admin, generator, null, "Test", List.of(new PickupRequestService.WasteLine(category, new BigDecimal(kg))));
    }
    protected long route(long requestId) {
        return routes.save(operator, vehicleId, zoneId, LocalDate.now(), List.of(new RoutePersistenceService.PlannedStop(requestId, 1)));
    }
    protected long firstStop(long routeId) { return routes.findStops(operator, routeId).get(0).getStopId(); }
    protected void complete(long stop, Map<Long, BigDecimal> quantities) {
        LocalDateTime now = LocalDateTime.now();
        collections.complete(operator, stop, quantities, now, now);
    }

    @Test void connectionConfigurationAndMultipleRowSelect() throws Exception {
        assertTrue(anchor.isValid(2));
        assertThrows(DatabaseOperationException.class, () -> DatabaseConnection.fromEnvironment(Map.of()));
        assertThrows(DatabaseOperationException.class, () -> new DatabaseConnection("jdbc:mysql://localhost/test", "user", null));
        assertEquals(2, database.read(c -> new ZoneDAO(c).findAll()).size());
        assertTrue(database.read(c -> new ZoneDAO(c).findById(Long.MAX_VALUE)).isEmpty());
        assertEquals(1, database.read(c -> new GeneratorDAO(c).findByZone(zoneId)).size());
    }

    @Test void crudChecksAffectedRowsAndProtectsReferences() {
        CatalogService catalog = new CatalogService(database);
        long id = catalog.saveCategory(admin, new WasteCategory(null, "Glass", null, "LOW"));
        catalog.saveCategory(admin, new WasteCategory(id, "Clean glass", "recycle", "LOW"));
        assertEquals("Clean glass", database.read(c -> new CategoryDAO(c).findById(id).orElseThrow().getCategoryName()));
        catalog.deleteUnreferencedCategory(admin, id);
        assertTrue(database.read(c -> new CategoryDAO(c).findById(id)).isEmpty());
        assertThrows(DatabaseOperationException.class, () -> catalog.deleteUnreferencedCategory(admin, id));
        assertThrows(DatabaseOperationException.class, () -> catalog.saveCategory(admin, new WasteCategory(id, "Gone", null, "LOW")));
        request(generatorId, categoryId, "10");
        assertThrows(DatabaseOperationException.class, () -> catalog.deleteUnreferencedCategory(admin, categoryId));
    }

    @Test void uniqueForeignKeyAndCompositeKeyViolationsKeepCauses() {
        DatabaseOperationException duplicate = assertThrows(DatabaseOperationException.class,
                () -> database.transaction(c -> new ZoneDAO(c).insert(new Zone(null, "North", null))));
        assertInstanceOf(SQLException.class, duplicate.getCause());
        assertThrows(DatabaseOperationException.class, () -> seedGenerator("Bad zone", Long.MAX_VALUE));
        long id = request(generatorId, categoryId, "10");
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c -> {
            new RequestWasteDAO(c).insertLine(new RequestWaste(id, categoryId, BigDecimal.ONE, null)); return null;
        }));
        assertEquals(1, count("REQUEST_WASTE"));
    }

    @Test void requestCreationIsAtomicAndNullIsNotZero() {
        long id = request(generatorId, categoryId, "10.250");
        assertNull(requests.findWaste(generatorUser, id).get(0).getActualQuantity());
        assertThrows(InvalidRequestException.class, () -> requests.create(admin, generatorId, null, null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE),
                new PickupRequestService.WasteLine(Long.MAX_VALUE, BigDecimal.ONE))));
        assertEquals(1, count("PICKUP_REQUEST"));
        assertEquals(1, count("REQUEST_WASTE"));
        // A SQL failure after the first line must roll back both tables too.
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c -> {
            long newId = new RequestDAO(c).insert(new PickupRequest(null, generatorId, LocalDateTime.now(), null, PENDING, null, null));
            RequestWasteDAO dao = new RequestWasteDAO(c);
            dao.insertLine(new RequestWaste(newId, categoryId, BigDecimal.ONE, null));
            dao.insertLine(new RequestWaste(newId, Long.MAX_VALUE, BigDecimal.ONE, null));
            return newId;
        }));
        assertEquals(1, count("PICKUP_REQUEST"));
    }

    @Test void requestValidationAndOwnership() {
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "0"));
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "-1"));
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "1.0001"));
        assertThrows(InvalidRequestException.class, () -> requests.create(admin, generatorId, null, null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE), new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN))));
        assertThrows(InvalidRequestException.class, () -> requests.create(generatorUser, otherGeneratorId, null, null,
                List.of(new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE))));
        long other = request(otherGeneratorId, categoryId, "1");
        assertThrows(InvalidRequestException.class, () -> requests.findById(generatorUser, other));
        assertThrows(InvalidRequestException.class, () -> requests.findWaste(generatorUser, other));
        assertThrows(InvalidRequestException.class, () -> requests.cancel(generatorUser, other));
        assertThrows(InvalidRequestException.class, () -> requests.findPending(generatorUser));
        new GeneratorService(database).setActive(admin, generatorId, false);
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "1"));
    }

    @Test void authenticationRejectsBadPasswordsInactiveUsersAndChangedSessions() {
        AuthService auth = new AuthService(database);
        assertThrows(InvalidRequestException.class, () -> auth.login("admin", "wrong".toCharArray()));
        assertThrows(InvalidRequestException.class, () -> auth.login("missing", PASSWORD.toCharArray()));
        assertThrows(InvalidRequestException.class, () -> auth.login("admin' OR '1'='1", PASSWORD.toCharArray()));
        sql("UPDATE `USER` SET is_active = FALSE WHERE username = ?", "generator");
        assertThrows(InvalidRequestException.class, () -> auth.login("generator", PASSWORD.toCharArray()));
        assertThrows(InvalidRequestException.class, () -> requests.findByGenerator(generatorUser, generatorId));
        assertFalse(database.read(c -> new UserDAO(c).findByUsername("admin").orElseThrow().toString()).contains(HASH));
        assertFalse(admin.toString().contains(HASH));
        sql("UPDATE `USER` SET role = ? WHERE username = ?", ADMIN, "operator");
        assertThrows(InvalidRequestException.class, () -> requests.findPending(operator));
    }

    @Test void cancellationAndAssignmentTransitions() {
        long id = request(generatorId, categoryId, "1");
        requests.cancel(generatorUser, id);
        assertEquals(CANCELLED, requests.findById(admin, id).orElseThrow().getStatus());
        assertThrows(InvalidRequestException.class, () -> requests.cancel(admin, id));
        assertThrows(InvalidRequestException.class, () -> route(id));
        long assigned = request(generatorId, categoryId, "1");
        route(assigned);
        assertThrows(InvalidRequestException.class, () -> requests.cancel(admin, assigned));
        assertThrows(VehicleUnavailableException.class, () -> new VehicleService(database).updateStatus(admin, vehicleId, MAINTENANCE));
        assertThrows(InvalidRequestException.class, () -> new GeneratorService(database).setActive(admin, generatorId, false));
    }

    @Test void routeChecksZoneCapacityOrderEligibilityAndUnavailableVehicle() {
        long id = request(generatorId, categoryId, "101");
        assertThrows(InvalidRequestException.class, () -> route(id));
        long wrongZone = request(otherGeneratorId, categoryId, "1");
        assertThrows(InvalidRequestException.class, () -> route(wrongZone));
        long valid = request(generatorId, categoryId, "10");
        assertThrows(InvalidRequestException.class, () -> routes.save(admin, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(valid, 1), new RoutePersistenceService.PlannedStop(id, 1))));
        assertThrows(InvalidRequestException.class, () -> routes.save(admin, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(valid, 1), new RoutePersistenceService.PlannedStop(valid, 2))));
        assertThrows(InvalidRequestException.class, () -> routes.save(generatorUser, vehicleId, zoneId, LocalDate.now(), List.of(new RoutePersistenceService.PlannedStop(valid, 1))));
        assertEquals(0, count("ROUTE"));
        route(valid);
        assertThrows(VehicleUnavailableException.class, () -> route(id));
    }

    @Test void invalidStopLeavesNoPartialRouteAndDatabaseConstraintsProtectStops() {
        long first = request(generatorId, categoryId, "10");
        long second = request(generatorId, categoryId, "20");
        assertThrows(InvalidRequestException.class, () -> routes.save(admin, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(first, 1), new RoutePersistenceService.PlannedStop(Long.MAX_VALUE, 2))));
        assertEquals(0, count("ROUTE"));
        // Force a failure after the route and first stop have been physically inserted.
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c -> {
            long route = new RouteDAO(c).insert(new Route(null, vehicleId, zoneId, LocalDate.now(), PLANNED, LocalDateTime.now()));
            new RouteStopDAO(c).insert(new RouteStop(null, route, first, 1, PENDING, null, null));
            new RequestDAO(c).updateStatus(first, PENDING, ASSIGNED);
            new RouteStopDAO(c).insert(new RouteStop(null, route, second, 1, PENDING, null, null));
            return route;
        }));
        assertEquals(0, count("ROUTE"));
        assertEquals(0, count("ROUTE_STOP"));
        assertEquals(PENDING, requests.findById(admin, first).orElseThrow().getStatus());
        long route = route(first);
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c ->
                new RouteStopDAO(c).insert(new RouteStop(null, route, first, 2, PENDING, null, null))));
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c ->
                new RouteStopDAO(c).insert(new RouteStop(null, route, second, 1, PENDING, null, null))));
        assertEquals(1, count("ROUTE_STOP"));
    }

    @Test void completesCollectionAndReleasesVehicleOnlyAfterLastStop() {
        long first = request(generatorId, categoryId, "10");
        long second = request(generatorId, categoryId, "20");
        long route = routes.save(operator, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(second, 1), new RoutePersistenceService.PlannedStop(first, 2)));
        List<RouteStop> stops = routes.findStops(operator, route);
        assertEquals(second, stops.get(0).getRequestId());
        complete(stops.get(0).getStopId(), Map.of(categoryId, BigDecimal.ZERO));
        assertEquals(0, requests.findWaste(admin, second).get(0).getActualQuantity().signum());
        assertEquals(IN_USE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        assertNull(requests.findWaste(admin, first).get(0).getActualQuantity());
        complete(stops.get(1).getStopId(), Map.of(categoryId, new BigDecimal("8.5")));
        assertEquals(COMPLETED, requests.findById(admin, first).orElseThrow().getStatus());
        assertNotNull(requests.findById(admin, first).orElseThrow().getCompletionDate());
        assertEquals(COMPLETED, routes.findById(admin, route).orElseThrow().getStatus());
        assertEquals(AVAILABLE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        assertThrows(InvalidRequestException.class, () -> complete(stops.get(0).getStopId(), Map.of(categoryId, BigDecimal.ONE)));
        long later = request(generatorId, categoryId, "1");
        assertTrue(route(later) > 0);
    }

    @Test void rejectsInvalidCollectionWithoutChangingQuantities() {
        long request = requests.create(admin, generatorId, null, null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN), new PickupRequestService.WasteLine(secondCategoryId, BigDecimal.ONE)));
        long stop = firstStop(route(request));
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, new BigDecimal("-1"), secondCategoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, new BigDecimal("100"), secondCategoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> collections.complete(operator, stop,
                Map.of(categoryId, BigDecimal.ONE, secondCategoryId, BigDecimal.ONE), LocalDateTime.now(), LocalDateTime.now().minusHours(1)));
        assertTrue(requests.findWaste(admin, request).stream().allMatch(w -> w.getActualQuantity() == null));
        assertEquals(ASSIGNED, requests.findById(admin, request).orElseThrow().getStatus());
        // Defend against a generator deactivated outside these services as well.
        sql("UPDATE WASTE_GENERATOR SET is_active = FALSE WHERE generator_id = ?", generatorId);
        assertThrows(InvalidRequestException.class, () -> complete(stop,
                Map.of(categoryId, BigDecimal.ONE, secondCategoryId, BigDecimal.ONE)));
        assertTrue(requests.findWaste(admin, request).stream().allMatch(w -> w.getActualQuantity() == null));
    }

    @Test void reportsDoNotMultiplyWeightsAndPreserveUncollectedNulls() {
        long first = requests.create(admin, generatorId, null, null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN), new PickupRequestService.WasteLine(secondCategoryId, new BigDecimal("20"))));
        long second = request(generatorId, categoryId, "5");
        long route = routes.save(admin, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(first, 1), new RoutePersistenceService.PlannedStop(second, 2)));
        ReportService reports = new ReportService(database);
        LocalDate from = LocalDate.now(), to = from.plusDays(1);
        ReportDAO.RouteWeight weight = reports.routeWeights(admin, from, to).get(0);
        assertEquals(0, new BigDecimal("35").compareTo(weight.estimatedKg()));
        assertNull(weight.actualKg()); assertEquals(0, weight.measuredLines()); assertEquals(3, weight.totalLines());
        assertNull(reports.vehicleUtilization(admin, from, to).get(0).actualRatio());
        assertEquals(2, reports.requestStatuses(admin, from, to).get(0).requestCount());
        complete(firstStop(route), Map.of(categoryId, BigDecimal.ZERO, secondCategoryId, new BigDecimal("15")));
        weight = reports.routeWeights(admin, from, to).get(0);
        assertEquals(0, new BigDecimal("15").compareTo(weight.actualKg())); assertEquals(2, weight.measuredLines());
        assertEquals(0, new BigDecimal("0.35").compareTo(reports.vehicleUtilization(admin, from, to).get(0).estimatedRatio()));
        assertEquals(0, new BigDecimal("0.15").compareTo(reports.vehicleUtilization(admin, from, to).get(0).actualRatio()));
        assertEquals(2, reports.collectionVolume(admin, from, to).size());
        ReportDAO.QuantityComparison paper = reports.estimatedVersusActual(admin, from, to).get(0);
        assertEquals(0, paper.actualKg().signum()); assertEquals(1, paper.measuredLines()); assertEquals(2, paper.totalLines());
        assertThrows(InvalidRequestException.class, () -> reports.routeWeights(generatorUser, from, to));
    }

    @Test void generatorAndSubtypeAreSavedTogether() {
        GeneratorService service = new GeneratorService(database);
        WasteGenerator hospital = new WasteGenerator(null, "Clinic", "HOSPITAL", null, null, null, null, null, null, zoneId, true, null);
        assertThrows(InvalidRequestException.class, () -> service.create(admin, new GeneratorService.Profile(hospital, null, null, null)));
        long id = service.create(admin, new GeneratorService.Profile(hospital,
                new Hospital(null, "license", "auth", LocalDate.now().plusYears(1)), null, null));
        assertEquals("license", service.findById(admin, id).orElseThrow().hospital().getLicenseNumber());
        assertNull(hospital.getGeneratorId());
        long before = count("WASTE_GENERATOR");
        assertThrows(DatabaseOperationException.class, () -> service.create(admin, new GeneratorService.Profile(hospital,
                new Hospital(null, "x".repeat(101), "auth", LocalDate.now()), null, null)));
        assertEquals(before, count("WASTE_GENERATOR"));
        assertEquals(1, count("HOSPITAL"));
    }

    @Test void concurrentAssignmentsCanOnlyCommitOnce() throws Exception {
        long request = request(generatorId, categoryId, "10");
        long otherVehicle = seedVehicle("TEST-2");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long vehicle : new long[]{vehicleId, otherVehicle}) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        routes.save(operator, vehicle, zoneId, LocalDate.now(), List.of(new RoutePersistenceService.PlannedStop(request, 1)));
                        return true;
                    } catch (InvalidRequestException | DatabaseOperationException e) { return false; }
                }));
            }
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) if (result.get(15, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, count("ROUTE_STOP")); assertEquals(1, count("ROUTE"));
            assertEquals(1, new VehicleService(database).findAvailable(admin).size());
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void serviceTransactionsRollbackAfterLateJdbcFailures() {
        Database failingRequestDb = new Database(FailingConnections.onStatement(provider, "INSERT INTO `REQUEST_WASTE`", 2));
        assertThrows(DatabaseOperationException.class, () -> new PickupRequestService(failingRequestDb).create(admin, generatorId, null, null,
                List.of(new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN),
                        new PickupRequestService.WasteLine(secondCategoryId, BigDecimal.ONE))));
        assertEquals(0, count("PICKUP_REQUEST")); assertEquals(0, count("REQUEST_WASTE"));
        long first = requests.create(admin, generatorId, null, null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN),
                new PickupRequestService.WasteLine(secondCategoryId, BigDecimal.ONE)));
        long second = request(generatorId, categoryId, "1");
        Database failingRouteDb = new Database(FailingConnections.onStatement(provider, "INSERT INTO `ROUTE_STOP`", 2));
        assertThrows(DatabaseOperationException.class, () -> new RoutePersistenceService(failingRouteDb).save(operator,
                vehicleId, zoneId, LocalDate.now(), List.of(new RoutePersistenceService.PlannedStop(first, 1),
                        new RoutePersistenceService.PlannedStop(second, 2))));
        assertEquals(0, count("ROUTE")); assertEquals(0, count("ROUTE_STOP"));
        assertEquals(PENDING, requests.findById(admin, first).orElseThrow().getStatus());
        assertEquals(AVAILABLE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        long stop = firstStop(route(first));
        Database failingCollectionDb = new Database(FailingConnections.onStatement(provider, "UPDATE `PICKUP_REQUEST`", 1));
        LocalDateTime now = LocalDateTime.now();
        assertThrows(DatabaseOperationException.class, () -> new CollectionService(failingCollectionDb).complete(operator,
                stop, Map.of(categoryId, BigDecimal.ZERO, secondCategoryId, BigDecimal.ONE), now, now));
        assertTrue(requests.findWaste(admin, first).stream().allMatch(w -> w.getActualQuantity() == null));
        assertEquals(ASSIGNED, requests.findById(admin, first).orElseThrow().getStatus());
        RouteStop unchanged = database.read(c -> new RouteStopDAO(c).findById(stop).orElseThrow());
        assertEquals(PENDING, unchanged.getStatus()); assertNull(unchanged.getArrivalTime()); assertNull(unchanged.getCompletionTime());
    }

    @Test void daosLeaveBorrowedConnectionOpenAndDoNotCommit() throws Exception {
        try (Connection connection = provider.open()) {
            connection.setAutoCommit(false);
            new ZoneDAO(connection).insert(new Zone(null, "Borrowed", null));
            assertFalse(connection.isClosed());
            assertEquals(2, database.read(c -> new ZoneDAO(c).findAll()).size());
            connection.rollback();
        }
        assertEquals(2, database.read(c -> new ZoneDAO(c).findAll()).size());
    }
}

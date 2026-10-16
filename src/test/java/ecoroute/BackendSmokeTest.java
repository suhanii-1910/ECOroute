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
        seedUser("admin", ADMIN, null);
        seedUser("operator", OPERATOR, null);
        seedUser("generator", GENERATOR, generatorId);
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

    protected void seedUser(String username, String role, Long generator) {
        database.transaction(c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT INTO `USER` (username,password_hash,role,generator_id,is_active,created_date) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                s.setString(1, username); s.setString(2, HASH); s.setString(3, role);
                s.setObject(4, generator); s.setBoolean(5, true); s.setObject(6, LocalDateTime.now());
                assertEquals(1, s.executeUpdate());
            }
            return null;
        });
    }

    protected long seedGenerator(String name, long zone) {
        return database.transaction(c -> new GeneratorDAO(c).insert(new WasteGenerator(null, name, "OFFICE", null,
                null, null, "Fixture address", null, null, zone, true, LocalDateTime.now())));
    }
    protected long seedVehicle(String number) {
        return database.transaction(c -> new VehicleDAO(c).insert(new Vehicle(null, number, new BigDecimal("100"),
                AVAILABLE, zoneId)));
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
        return requests.create(admin, generator, LocalDate.now(), "Test", List.of(new PickupRequestService.WasteLine(category, new BigDecimal(kg))));
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
        assertThrows(InvalidRequestException.class, () -> requests.create(admin, generatorId, LocalDate.now(), null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE),
                new PickupRequestService.WasteLine(Long.MAX_VALUE, BigDecimal.ONE))));
        assertEquals(1, count("PICKUP_REQUEST"));
        assertEquals(1, count("REQUEST_WASTE"));
        // A SQL failure after the first line must roll back both tables too.
        assertThrows(DatabaseOperationException.class, () -> database.transaction(c -> {
            long newId = new RequestDAO(c).insert(new PickupRequest(null, generatorId, LocalDateTime.now(), LocalDate.now(), PENDING, null, null));
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
        assertThrows(InvalidRequestException.class, () -> requests.create(admin, generatorId, LocalDate.now(), null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE), new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN))));
        assertThrows(InvalidRequestException.class, () -> requests.create(generatorUser, otherGeneratorId, LocalDate.now(), null,
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

    @Test void userDaoHandlesMultipleUsersWithoutDuplicateEmailCollisions() {
        database.transaction(c -> {
            UserDAO dao = new UserDAO(c);
            User u1 = new User(null, "test_multi_user1", "hash1", DRIVER, null, true, java.time.LocalDateTime.now());
            long id1 = dao.insert(u1);
            assertTrue(id1 > 0);

            User u2 = new User(null, "test_multi_user2", "hash2", OPERATOR, null, true, java.time.LocalDateTime.now());
            long id2 = dao.insert(u2);
            assertTrue(id2 > 0);

            User fetched1 = dao.findById(id1).orElseThrow();
            User fetched2 = dao.findById(id2).orElseThrow();
            assertNotNull(fetched1.getEmail());
            assertNotNull(fetched2.getEmail());
            assertNotEquals(fetched1.getEmail(), fetched2.getEmail());
            assertEquals("test_multi_user1@ecoroute.local", fetched1.getEmail());
            assertEquals("test_multi_user2@ecoroute.local", fetched2.getEmail());

            User u3 = new User(null, "test_multi_user3", "hash3", "custom@test.org", "Custom Name", ADMIN, null, true, java.time.LocalDateTime.now());
            long id3 = dao.insert(u3);
            User fetched3 = dao.findById(id3).orElseThrow();
            assertEquals("custom@test.org", fetched3.getEmail());
            assertEquals("Custom Name", fetched3.getFullName());

            return null;
        });
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

    @Test void startRouteTransitionsVehicleToInRouteAndRouteToInProgress() {
        long id = request(generatorId, categoryId, "10");
        long routeId = route(id);
        assertEquals(PLANNED, routes.findById(admin, routeId).orElseThrow().getStatus());
        assertEquals(AVAILABLE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        collections.startRoute(operator, routeId);
        assertEquals(IN_PROGRESS, routes.findById(admin, routeId).orElseThrow().getStatus());
        assertEquals(IN_ROUTE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
    }

    @Test void completesCollectionAndRouteTransitionsToDispatchedToDisposal() {
        long first = request(generatorId, categoryId, "10");
        long second = request(generatorId, categoryId, "20");
        long routeId = routes.save(operator, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(second, 1), new RoutePersistenceService.PlannedStop(first, 2)));
        collections.startRoute(operator, routeId);
        List<RouteStop> stops = routes.findStops(operator, routeId);
        assertEquals(second, stops.get(0).getRequestId());
        // After first stop completed, route is still IN_PROGRESS
        complete(stops.get(0).getStopId(), Map.of(categoryId, BigDecimal.ZERO));
        assertEquals(0, requests.findWaste(admin, second).get(0).getActualQuantity().signum());
        assertEquals(IN_ROUTE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        assertEquals(IN_PROGRESS, routes.findById(admin, routeId).orElseThrow().getStatus());
        assertNull(requests.findWaste(admin, first).get(0).getActualQuantity());
        // After last stop, route transitions to DISPATCHED_TO_DISPOSAL (not COMPLETED yet — disposal step needed)
        complete(stops.get(1).getStopId(), Map.of(categoryId, new BigDecimal("8.5")));
        assertEquals(COMPLETED, requests.findById(admin, first).orElseThrow().getStatus());
        assertNotNull(requests.findById(admin, first).orElseThrow().getCompletionDate());
        assertEquals(DISPATCHED_TO_DISPOSAL, routes.findById(admin, routeId).orElseThrow().getStatus());
        // Vehicle is still IN_ROUTE until disposal is recorded
        assertEquals(IN_ROUTE, new VehicleService(database).findById(admin, vehicleId).orElseThrow().getStatus());
        assertThrows(InvalidRequestException.class, () -> complete(stops.get(0).getStopId(), Map.of(categoryId, BigDecimal.ONE)));
    }

    @Test void rejectsInvalidCollectionWithoutChangingQuantities() {
        long request = requests.create(admin, generatorId, LocalDate.now(), null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN), new PickupRequestService.WasteLine(secondCategoryId, BigDecimal.ONE)));
        long routeId = route(request);
        collections.startRoute(operator, routeId);
        long stop = firstStop(routeId);
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, new BigDecimal("-1"), secondCategoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, new BigDecimal("100"), secondCategoryId, BigDecimal.ONE)));
        assertThrows(InvalidRequestException.class, () -> collections.complete(operator, stop,
                Map.of(categoryId, BigDecimal.ONE, secondCategoryId, BigDecimal.ONE), LocalDateTime.now(), LocalDateTime.now().minusHours(1)));
        assertTrue(requests.findWaste(admin, request).stream().allMatch(w -> w.getActualQuantity() == null));
        // After startRoute, request is IN_ROUTE
        assertEquals(IN_ROUTE, requests.findById(admin, request).orElseThrow().getStatus());
        // Defend against a generator deactivated outside these services as well.
        sql("UPDATE WASTE_GENERATOR SET is_active = FALSE WHERE generator_id = ?", generatorId);
        assertThrows(InvalidRequestException.class, () -> complete(stop,
                Map.of(categoryId, BigDecimal.ONE, secondCategoryId, BigDecimal.ONE)));
        assertTrue(requests.findWaste(admin, request).stream().allMatch(w -> w.getActualQuantity() == null));
    }

    @Test void reportsDoNotMultiplyWeightsAndPreserveUncollectedNulls() {
        long first = requests.create(admin, generatorId, LocalDate.now(), null, List.of(
                new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN), new PickupRequestService.WasteLine(secondCategoryId, new BigDecimal("20"))));
        long second = request(generatorId, categoryId, "5");
        long routeId = routes.save(admin, vehicleId, zoneId, LocalDate.now(), List.of(
                new RoutePersistenceService.PlannedStop(first, 1), new RoutePersistenceService.PlannedStop(second, 2)));
        ReportService reports = new ReportService(database);
        LocalDate from = LocalDate.now(), to = from.plusDays(1);
        ReportDAO.RouteWeight weight = reports.routeWeights(admin, from, to).get(0);
        assertEquals(0, new BigDecimal("35").compareTo(weight.estimatedKg()));
        assertNull(weight.actualKg()); assertEquals(0, weight.measuredLines()); assertEquals(3, weight.totalLines());
        assertNull(reports.vehicleUtilization(admin, from, to).get(0).actualRatio());
        assertEquals(2, reports.requestStatuses(admin, from, to).get(0).requestCount());
        collections.startRoute(admin, routeId);
        complete(firstStop(routeId), Map.of(categoryId, BigDecimal.ZERO, secondCategoryId, new BigDecimal("15")));
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
        WasteGenerator hospital = new WasteGenerator(null, "Clinic", "HOSPITAL", null, null, null, "Clinic address", null, null, zoneId, true, null);
        assertThrows(InvalidRequestException.class, () -> service.create(admin, new GeneratorService.Profile(hospital, null, null, null)));
        long id = service.create(admin, new GeneratorService.Profile(hospital,
                new Hospital(null, "license", "auth"), null, null));
        assertEquals("license", service.findById(admin, id).orElseThrow().hospital().getLicenseNumber());
        assertNull(hospital.getGeneratorId());
        long before = count("WASTE_GENERATOR");
        assertThrows(DatabaseOperationException.class, () -> service.create(admin, new GeneratorService.Profile(hospital,
                new Hospital(null, "x".repeat(101), "auth"), null, null)));
        assertEquals(before, count("WASTE_GENERATOR"));
        assertEquals(1, count("HOSPITAL"));
    }

    @Test void nullableGeneratorSubtypeDetailsAndSevenDigitCoordinatesRoundTrip() {
        GeneratorService service = new GeneratorService(database);
        WasteGenerator base = new WasteGenerator(null, "Nullable details", "HOSPITAL", null, null, null,
                "Required address", new BigDecimal("28.1234567"), new BigDecimal("77.1234567"), zoneId, true, null);
        long hospital = service.create(admin, new GeneratorService.Profile(base, new Hospital(), null, null));
        assertNull(service.findById(admin, hospital).orElseThrow().hospital().getLicenseNumber());
        assertEquals(base.getLongitude(), service.findById(admin, hospital).orElseThrow().generator().getLongitude());
        base.setGeneratorType("HOUSING_SOCIETY");
        long housing = service.create(admin, new GeneratorService.Profile(base, null, new HousingSociety(), null));
        assertNull(service.findById(admin, housing).orElseThrow().housingSociety().getRegistrationNumber());
        base.setGeneratorType("FACTORY");
        long factory = service.create(admin, new GeneratorService.Profile(base, null, null, new Factory()));
        assertNull(service.findById(admin, factory).orElseThrow().factory().getPollutionConsentNo());
        base.setAddress(null);
        assertThrows(InvalidRequestException.class, () -> service.create(admin, new GeneratorService.Profile(base, null, null, new Factory())));
    }

    @Test void disposalFacilityRetrievalPreservesAllFields() {
        CatalogService catalog = new CatalogService(database);
        // Insert a disposal facility directly for testing catalog retrieval
        sql("INSERT INTO DISPOSAL_FACILITY (facility_name,facility_type,address,latitude,longitude,capacity_tonnes_per_day,status) VALUES (?,?,?,?,?,?,?)",
                "Test recycling plant", "RECYCLING_PLANT", "Fixture facility address",
                new BigDecimal("28.1234567"), new BigDecimal("77.1234567"), new BigDecimal("10.00"), ACTIVE);
        List<DisposalFacility> facilities = catalog.disposalFacilities(operator);
        assertEquals(1, facilities.size());
        DisposalFacility facility = facilities.get(0);
        assertTrue(facility.getFacilityId() > 0);
        assertEquals("Test recycling plant", facility.getFacilityName());
        assertEquals("RECYCLING_PLANT", facility.getFacilityType());
        assertEquals("ACTIVE", facility.getStatus());
        assertTrue(catalog.disposalFacility(admin, facility.getFacilityId()).isPresent());
        assertTrue(catalog.disposalFacility(operator, 999999L).isEmpty());
        assertThrows(InvalidRequestException.class, () -> catalog.disposalFacilities(generatorUser));
    }

    @Test void frozenDecimalLimitsRejectRoundingAndDistinguishVehicleCapacity() {
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "1.001"));
        assertThrows(InvalidRequestException.class, () -> request(generatorId, categoryId, "10000000000"));
        long large = request(generatorId, categoryId, "9999999999.99");
        assertEquals(new BigDecimal("9999999999.99"), requests.findWaste(admin, large).get(0).getEstimatedQuantity());
        VehicleService vehicles = new VehicleService(database);
        assertThrows(InvalidRequestException.class, () -> vehicles.create(admin, "TOO-LARGE", new BigDecimal("100000000"), null));
        assertThrows(InvalidRequestException.class, () -> vehicles.create(admin, "TOO-PRECISE", new BigDecimal("1.001"), null));
        long vehicle = vehicles.create(admin, "MAX-CAPACITY", new BigDecimal("99999999.99"), null);
        assertEquals(new BigDecimal("99999999.99"), vehicles.findById(admin, vehicle).orElseThrow().getCapacityKg());
        long req = request(generatorId, categoryId, "1.23");
        long routeId = route(req);
        collections.startRoute(operator, routeId);
        long stop = firstStop(routeId);
        assertThrows(InvalidRequestException.class, () -> complete(stop, Map.of(categoryId, new BigDecimal("1.001"))));
        assertNull(requests.findWaste(admin, req).get(0).getActualQuantity());
        complete(stop, Map.of(categoryId, new BigDecimal("1.23")));
        assertEquals(new BigDecimal("1.23"), requests.findWaste(admin, req).get(0).getActualQuantity());
    }

    @Test void preferredPickupDateIsRequiredByServiceDaoAndFixture() {
        assertThrows(InvalidRequestException.class, () -> requests.create(admin, generatorId, null, null,
                List.of(new PickupRequestService.WasteLine(categoryId, BigDecimal.ONE))));
        assertThrows(InvalidRequestException.class, () -> database.transaction(c -> new RequestDAO(c).insert(
                new PickupRequest(null, generatorId, LocalDateTime.now(), null, PENDING, null, null))));
        assertThrows(DatabaseOperationException.class, () -> sql(
                "INSERT INTO PICKUP_REQUEST (generator_id,request_date,preferred_pickup_date,status) VALUES (?,?,?,?)",
                generatorId, LocalDateTime.now(), null, PENDING));
        assertEquals(0, count("PICKUP_REQUEST")); assertEquals(0, count("REQUEST_WASTE"));
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
            long createdRoute = database.read(c -> new RouteDAO(c).findByStatus(PLANNED).get(0).getRouteId());
            collections.startRoute(operator, createdRoute);
            assertEquals(1, new VehicleService(database).findAvailable(admin).size());
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void serviceTransactionsRollbackAfterLateJdbcFailures() {
        Database failingRequestDb = new Database(FailingConnections.onStatement(provider, "INSERT INTO `REQUEST_WASTE`", 2));
        assertThrows(DatabaseOperationException.class, () -> new PickupRequestService(failingRequestDb).create(admin, generatorId, LocalDate.now(), null,
                List.of(new PickupRequestService.WasteLine(categoryId, BigDecimal.TEN),
                        new PickupRequestService.WasteLine(secondCategoryId, BigDecimal.ONE))));
        assertEquals(0, count("PICKUP_REQUEST")); assertEquals(0, count("REQUEST_WASTE"));
        long first = requests.create(admin, generatorId, LocalDate.now(), null, List.of(
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
        long routeId = route(first);
        collections.startRoute(operator, routeId);
        long stop = firstStop(routeId);
        Database failingCollectionDb = new Database(FailingConnections.onStatement(provider, "UPDATE `PICKUP_REQUEST`", 1));
        LocalDateTime now = LocalDateTime.now();
        assertThrows(DatabaseOperationException.class, () -> new CollectionService(failingCollectionDb).complete(operator,
                stop, Map.of(categoryId, BigDecimal.ZERO, secondCategoryId, BigDecimal.ONE), now, now));
        assertTrue(requests.findWaste(admin, first).stream().allMatch(w -> w.getActualQuantity() == null));
        assertEquals(IN_ROUTE, requests.findById(admin, first).orElseThrow().getStatus());
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

    @Test void skipStopResetsRequestToPendingAndTransitionsRouteWhenAllDone() {
        long first = request(generatorId, categoryId, "10");
        long routeId = route(first);
        collections.startRoute(operator, routeId);
        long stop = firstStop(routeId);
        collections.skipStop(operator, stop, "inaccessible");
        assertEquals(PENDING, requests.findById(admin, first).orElseThrow().getStatus());
        assertEquals(DISPATCHED_TO_DISPOSAL, routes.findById(admin, routeId).orElseThrow().getStatus());
    }
}

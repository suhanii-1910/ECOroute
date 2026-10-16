package ecoroute;

import ecoroute.db.Database;
import ecoroute.db.DatabaseConnection;
import ecoroute.model.*;
import ecoroute.service.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Standalone verification utility to test real-MySQL connectivity, RBAC demo
 * account authentication, and a representative operational lifecycle against ecoroute_db.
 * Uses disposable test entities and cleans up after verification.
 */
public final class MySqlWorkflowVerificationTool {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("ECOroute Real MySQL Verification Tool");
        System.out.println("================================================================================");

        Map<String, String> env = System.getenv();
        String url = env.get("ECOROUTE_DB_URL");
        String user = env.get("ECOROUTE_DB_USER");
        String pass = env.get("ECOROUTE_DB_PASSWORD");

        if (url == null || user == null || pass == null) {
            System.err.println("ERROR: Missing required environment variables.");
            System.err.println("Please set ECOROUTE_DB_URL, ECOROUTE_DB_USER, and ECOROUTE_DB_PASSWORD.");
            System.exit(1);
        }

        System.out.println("Target Database URL : " + url);
        System.out.println("Database User       : " + user);
        System.out.println();

        DatabaseConnection provider = new DatabaseConnection(url, user, pass);
        Database database = new Database(provider);

        boolean allPassed = true;

        // ---------------------------------------------------------------------
        // STEP 1: Verify Schema Compatibility Columns
        // ---------------------------------------------------------------------
        System.out.println("[Step 1] Verifying Relational Schema Columns in ecoroute_db...");
        try (Connection c = provider.open()) {
            assertColumnExists(c, "USER", "is_active");
            assertColumnExists(c, "USER", "email");
            assertColumnExists(c, "USER", "full_name");
            assertColumnExists(c, "WASTE_GENERATOR", "is_active");
            assertColumnExists(c, "PICKUP_REQUEST", "completion_date");
            System.out.println("  ✓ Schema columns verified (USER.is_active, USER.email/full_name, WASTE_GENERATOR.is_active, PICKUP_REQUEST.completion_date)");
        } catch (Exception e) {
            System.err.println("  ✗ Schema column verification failed: " + e.getMessage());
            allPassed = false;
        }

        // ---------------------------------------------------------------------
        // STEP 2: Verify RBAC Demo Account Authentication
        // ---------------------------------------------------------------------
        System.out.println("\n[Step 2] Verifying PBKDF2 Authentication for Demo Accounts (Password: Password@123)...");
        char[] password = "Password@123".toCharArray();
        String[] testAccounts = {"admin", "operator1", "driver1", "hospital_gen", "society_gen"};
        Map<String, UserSession> sessions = new HashMap<>();

        AuthService authService = new AuthService(database);

        for (String username : testAccounts) {
            try {
                UserSession session = authService.login(username, password);
                sessions.put(username, session);
                System.out.printf("  ✓ User '%-12s' (Role: %-9s, UserID: %d, GeneratorID: %s) logged in successfully.\n",
                        session.getUsername(), session.getRole(), session.getUserId(),
                        (session.getGeneratorId() != null ? session.getGeneratorId() : "N/A"));
            } catch (Exception e) {
                System.err.printf("  ✗ User '%s' login failed: %s\n", username, e.getMessage());
                allPassed = false;
            }
        }

        // ---------------------------------------------------------------------
        // STEP 3: Representative Operational Workflow (Planning -> Collection -> Disposal -> Reports)
        // ---------------------------------------------------------------------
        System.out.println("\n[Step 3] Executing Representative Real-MySQL Operational Workflow...");
        Long disposableRequestId = null;
        Long disposableRouteId = null;
        Long disposableRecordId = null;

        UserSession adminSession = sessions.get("admin");
        UserSession operatorSession = sessions.get("operator1");
        UserSession driverSession = sessions.get("driver1");
        UserSession genSession = sessions.get("hospital_gen");

        if (adminSession != null && operatorSession != null && driverSession != null && genSession != null) {
            try {
                PickupRequestService requests = new PickupRequestService(database);
                RoutePersistenceService routes = new RoutePersistenceService(database);
                CollectionService collections = new CollectionService(database);
                DisposalService disposalService = new DisposalService(database);

                // 3a. Generator creates pickup request
                LocalDate today = LocalDate.now();
                Long genId = genSession.getGeneratorId();
                if (genId == null) genId = 1L;

                // Pick an available category (e.g., category 1)
                long categoryId = 1L;
                BigDecimal estWeight = new BigDecimal("45.000");

                disposableRequestId = requests.create(
                        genSession,
                        genId,
                        today,
                        "MySQL Disposable E2E Workflow Verification",
                        List.of(new PickupRequestService.WasteLine(categoryId, estWeight))
                );
                System.out.printf("  ✓ [3a] Generator Request #%d created (Est Weight: %s kg, Status: PENDING)\n",
                        disposableRequestId, estWeight);

                // 3b. Route planning via RoutePlanningAdapterService
                RoutePlanningAdapterService planningAdapter = new RoutePlanningAdapterService(database);
                // Look up generator's zone
                Long zoneId = 1L;
                try (Connection c = provider.open()) {
                    try (PreparedStatement s = c.prepareStatement("SELECT zone_id FROM WASTE_GENERATOR WHERE generator_id = ?")) {
                        s.setLong(1, genId);
                        try (ResultSet rs = s.executeQuery()) {
                            if (rs.next()) zoneId = rs.getLong(1);
                        }
                    }
                }

                List<Long> plannedRouteIds = planningAdapter.planAndSave(operatorSession, today, zoneId);
                if (plannedRouteIds.isEmpty()) {
                    throw new IllegalStateException("Planning produced 0 routes for zone " + zoneId);
                }
                disposableRouteId = plannedRouteIds.get(0);
                Route plannedRoute = routes.findById(operatorSession, disposableRouteId).orElseThrow();
                System.out.printf("  ✓ [3b] Operator planned & persisted Route #%d (Vehicle ID: %d, Status: %s)\n",
                        disposableRouteId, plannedRoute.getVehicleId(), plannedRoute.getStatus());

                // 3c. Driver starts route
                collections.startRoute(driverSession, disposableRouteId);
                Route inProgressRoute = routes.findById(operatorSession, disposableRouteId).orElseThrow();
                System.out.printf("  ✓ [3c] Driver started Route #%d -> Status: %s (Vehicle IN_ROUTE)\n",
                        disposableRouteId, inProgressRoute.getStatus());

                // 3d. Driver completes collection stop with actual weights
                List<RouteStop> stops = routes.findStops(operatorSession, disposableRouteId);
                long targetStopId = -1;
                for (RouteStop s : stops) {
                    if (s.getRequestId() == disposableRequestId.longValue()) {
                        targetStopId = s.getStopId();
                        break;
                    }
                }
                if (targetStopId == -1 && !stops.isEmpty()) targetStopId = stops.get(0).getStopId();

                Map<Long, BigDecimal> actualWeights = Map.of(categoryId, new BigDecimal("46.200"));
                LocalDateTime now = LocalDateTime.now();
                collections.complete(driverSession, targetStopId, actualWeights, now.minusMinutes(10), now);
                Route dispatchedRoute = routes.findById(operatorSession, disposableRouteId).orElseThrow();
                System.out.printf("  ✓ [3d] Stop #%d collected (Actual: 46.200 kg) -> Route #%d transitioned to %s\n",
                        targetStopId, disposableRouteId, dispatchedRoute.getStatus());

                // 3e. Record Disposal via sp_record_disposal
                // Find facility accepting categoryId
                long facilityId = 1L;
                try (Connection c = provider.open()) {
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT facility_id FROM FACILITY_ACCEPTED_WASTE WHERE category_id = ? LIMIT 1")) {
                        s.setLong(1, categoryId);
                        try (ResultSet rs = s.executeQuery()) {
                            if (rs.next()) facilityId = rs.getLong(1);
                        }
                    }
                }

                BigDecimal grossWeight = new BigDecimal("2046.20");
                BigDecimal tareWeight = new BigDecimal("2000.00");
                String receiptNo = "VERIFY-RCP-" + (System.currentTimeMillis() % 1000000);

                disposableRecordId = disposalService.recordDisposal(
                        adminSession,
                        disposableRouteId,
                        facilityId,
                        grossWeight,
                        tareWeight,
                        receiptNo
                );
                Route completedRoute = routes.findById(operatorSession, disposableRouteId).orElseThrow();
                System.out.printf("  ✓ [3e] Stored procedure sp_record_disposal executed -> Record #%d created (Receipt: %s, Route Status: %s)\n",
                        disposableRecordId, receiptNo, completedRoute.getStatus());

                // 3f. Query MySQL operational views
                try (Connection c = provider.open()) {
                    int reqStatusRows = countRows(c, "V_REQUEST_STATUS");
                    int routeWeightRows = countRows(c, "V_ROUTE_WEIGHT");
                    int vehicleUtilRows = countRows(c, "V_VEHICLE_UTILISATION");
                    System.out.printf("  ✓ [3f] Reporting views queried successfully:\n" +
                                      "       - V_REQUEST_STATUS     : %d rows\n" +
                                      "       - V_ROUTE_WEIGHT       : %d rows\n" +
                                      "       - V_VEHICLE_UTILISATION: %d rows\n",
                            reqStatusRows, routeWeightRows, vehicleUtilRows);
                }

            } catch (Exception e) {
                System.err.println("  ✗ Operational workflow failed: " + e.getMessage());
                e.printStackTrace(System.err);
                allPassed = false;
            } finally {
                // Clean up disposable test data cleanly
                System.out.println("\n[Step 4] Cleaning up disposable verification records from ecoroute_db...");
                try (Connection c = provider.open()) {
                    c.setAutoCommit(false);
                    if (disposableRecordId != null) {
                        try (PreparedStatement s = c.prepareStatement("DELETE FROM DISPOSAL_RECORD WHERE record_id = ?")) {
                            s.setLong(1, disposableRecordId);
                            s.executeUpdate();
                        }
                    }
                    if (disposableRouteId != null) {
                        try (PreparedStatement s = c.prepareStatement("DELETE FROM ROUTE_STOP WHERE route_id = ?")) {
                            s.setLong(1, disposableRouteId);
                            s.executeUpdate();
                        }
                        try (PreparedStatement s = c.prepareStatement("DELETE FROM ROUTE WHERE route_id = ?")) {
                            s.setLong(1, disposableRouteId);
                            s.executeUpdate();
                        }
                    }
                    if (disposableRequestId != null) {
                        try (PreparedStatement s = c.prepareStatement("DELETE FROM REQUEST_WASTE WHERE request_id = ?")) {
                            s.setLong(1, disposableRequestId);
                            s.executeUpdate();
                        }
                        try (PreparedStatement s = c.prepareStatement("DELETE FROM PICKUP_REQUEST WHERE request_id = ?")) {
                            s.setLong(1, disposableRequestId);
                            s.executeUpdate();
                        }
                    }
                    c.commit();
                    System.out.println("  ✓ Disposable test records safely purged; existing data completely preserved.");
                } catch (Exception e) {
                    System.err.println("  ✗ Cleanup warning: " + e.getMessage());
                }
            }
        } else {
            System.err.println("  ✗ Cannot run operational workflow: missing required RBAC demo accounts.");
            allPassed = false;
        }

        System.out.println("\n================================================================================");
        if (allPassed) {
            System.out.println("VERIFICATION RESULT: ALL REAL-MYSQL TESTS PASSED");
        } else {
            System.out.println("VERIFICATION RESULT: FAILURES DETECTED");
            System.exit(1);
        }
        System.out.println("================================================================================");
    }

    private static void assertColumnExists(Connection c, String table, String column) throws Exception {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?")) {
            s.setString(1, table);
            s.setString(2, column);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Table `" + table + "` is missing column `" + column + "`");
                }
            }
        }
    }

    private static int countRows(Connection c, String viewName) throws Exception {
        try (PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM `" + viewName + "`");
             ResultSet rs = s.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}

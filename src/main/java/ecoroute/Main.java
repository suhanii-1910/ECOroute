package ecoroute;

import ecoroute.dao.*;
import ecoroute.db.ConnectionProvider;
import ecoroute.db.Database;
import ecoroute.db.DatabaseConnection;
import ecoroute.model.WasteCategory;
import ecoroute.model.Zone;
import ecoroute.service.PasswordHasher;
import ecoroute.ui.AppContext;
import ecoroute.ui.LoginDialog;
import ecoroute.ui.MainFrame;
import ecoroute.ui.UITheme;

import javax.swing.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static ecoroute.db.DatabaseContract.*;

/**
 * Main application entry point for ECOroute.
 * - Initializes FlatLaf modern desktop look-and-feel
 * - Establishes database connection (MySQL with fallback to embedded fixture)
 * - Sets up dependency injection context
 * - Launches LoginDialog and MainFrame on the Swing Event Dispatch Thread (EDT)
 */
public final class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    static {
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
    }

    public static void main(String[] args) {
        // Initialize Look and Feel
        UITheme.setupFlatLaf();

        // Connect to database
        DatabaseHolder dbHolder = initializeDatabase();

        // Launch UI on Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            try {
                AppContext context = new AppContext(dbHolder.database, dbHolder.info);
                MainFrame mainFrame = new MainFrame(context);

                LoginDialog loginDialog = new LoginDialog(mainFrame, context);
                loginDialog.setVisible(true);

                if (loginDialog.isAuthenticated()) {
                    mainFrame.setVisible(true);
                } else {
                    LOGGER.info("Authentication cancelled; exiting ECOroute.");
                    System.exit(0);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to launch ECOroute desktop application", ex);
                JOptionPane.showMessageDialog(null,
                        "Critical error launching ECOroute:\n" + ex.getMessage(),
                        "Launch Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static DatabaseHolder initializeDatabase() {
        // 1. Check environment variables
        Map<String, String> env = System.getenv();
        String envUrl = env.get("ECOROUTE_DB_URL");
        String envUser = env.get("ECOROUTE_DB_USER");
        String envPass = env.get("ECOROUTE_DB_PASSWORD");

        if (envUrl != null && !envUrl.isBlank() && envUser != null && envPass != null) {
            try {
                DatabaseConnection dbConn = DatabaseConnection.fromEnvironment(env);
                try (Connection c = dbConn.open()) {
                    LOGGER.info("Connected to configured MySQL at " + envUrl);
                    return new DatabaseHolder(new Database(dbConn), "MySQL (" + envUrl + ")");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Failed connecting to configured MySQL environment: " + ex.getMessage());
            }
        }

        // 2. Try default localhost MySQL configurations
        String[] candidateUrls = {
                "jdbc:mysql://localhost:3306/ecoroute?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "jdbc:mysql://localhost:3306/ecoroute_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        };
        String[][] candidateCredentials = {
                {"ecoroute_app", "replace-locally"},
                {"root", "root"},
                {"root", ""},
                {"ecoroute_app", "Password@123"}
        };

        for (String url : candidateUrls) {
            for (String[] creds : candidateCredentials) {
                try {
                    ConnectionProvider provider = () -> DriverManager.getConnection(url, creds[0], creds[1]);
                    try (Connection conn = provider.open();
                         Statement st = conn.createStatement()) {
                        // Quick check if USER table exists
                        st.executeQuery("SELECT 1 FROM `USER` LIMIT 1");
                        LOGGER.info("Connected to local MySQL at " + url + " as " + creds[0]);
                        return new DatabaseHolder(new Database(provider), "MySQL (" + creds[0] + "@localhost)");
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Fallback to self-contained embedded in-memory database with full fixture
        LOGGER.info("Local MySQL not reachable; initializing self-contained embedded demo database.");
        try {
            String h2Url = "jdbc:h2:mem:ecoroute_standalone;MODE=MySQL;DATABASE_TO_UPPER=false;LOCK_TIMEOUT=5000;DB_CLOSE_DELAY=-1";
            ConnectionProvider h2Provider = () -> DriverManager.getConnection(h2Url, "sa", "");

            try (Connection conn = h2Provider.open()) {
                // Load and execute member2_fixture.sql
                try (InputStream in = Main.class.getResourceAsStream("/member2_fixture.sql")) {
                    if (in != null) {
                        String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
                        for (String stmt : sql.split(";")) {
                            if (!stmt.isBlank()) {
                                try (Statement s = conn.createStatement()) {
                                    s.execute(stmt);
                                } catch (Exception ex) {
                                    // Ignore harmless duplicate/unsupported statements in H2
                                }
                            }
                        }
                    }
                }

                // Ensure demo seed data is present in embedded instance
                Database db = new Database(h2Provider);
                seedEmbeddedDemoData(db);

                return new DatabaseHolder(db, "Embedded Demo Database (H2 MySQL-Mode)");
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize embedded database", ex);
            throw new RuntimeException("Could not initialize any database connection: " + ex.getMessage(), ex);
        }
    }

    private static void seedEmbeddedDemoData(Database database) {
        try {
            database.transaction(c -> {
                ZoneDAO zoneDao = new ZoneDAO(c);
                if (zoneDao.findAll().isEmpty()) {
                    LOGGER.info("Seeding realistic demo dataset (zones, categories, generators, vehicles, facilities, requests)...");

                    // 1. Zones
                    long z1 = zoneDao.insert(new Zone(null, "North Zone", "Northern residential and commercial area"));
                    long z2 = zoneDao.insert(new Zone(null, "South Zone", "Southern industrial belt"));
                    long z3 = zoneDao.insert(new Zone(null, "East Zone", "Eastern mixed-use corridor"));
                    long z4 = zoneDao.insert(new Zone(null, "West Zone", "Western hospital and institutional cluster"));
                    long z5 = zoneDao.insert(new Zone(null, "Central Zone", "City centre — high density"));

                    // 2. Waste Categories
                    CategoryDAO catDao = new CategoryDAO(c);
                    long c1 = catDao.insert(new WasteCategory(null, "General Waste", "Everyday non-hazardous rubbish", "LOW"));
                    long c2 = catDao.insert(new WasteCategory(null, "Organic Waste", "Food scraps and garden waste", "LOW"));
                    long c3 = catDao.insert(new WasteCategory(null, "Recyclable Waste", "Paper, plastic, glass, metal", "LOW"));
                    long c4 = catDao.insert(new WasteCategory(null, "Electronic Waste", "Discarded electronics and e-waste", "MEDIUM"));
                    long c5 = catDao.insert(new WasteCategory(null, "Chemical Waste", "Industrial chemicals and solvents", "HIGH"));
                    long c6 = catDao.insert(new WasteCategory(null, "Biomedical Waste", "Clinical and biomedical waste", "BIOMEDICAL"));

                    // 3. Generators
                    GeneratorDAO genDao = new GeneratorDAO(c);
                    GeneratorSubtypeDAO subDao = new GeneratorSubtypeDAO(c);
                    LocalDateTime now = LocalDateTime.now();

                    long g1 = genDao.insert(new ecoroute.model.WasteGenerator(
                            null, "City General Hospital", "HOSPITAL", "Dr. Patel",
                            "1000000001", "hospital@city.gov", "1 Hospital Road, North",
                            java.math.BigDecimal.valueOf(28.7041), java.math.BigDecimal.valueOf(77.1025),
                            z1, true, now
                    ));
                    subDao.insert(new ecoroute.model.Hospital(g1, "LIC-HOSP-2020-001", "BIO-AUTH-2020-001"));

                    long g2 = genDao.insert(new ecoroute.model.WasteGenerator(
                            null, "Green Valley Society", "HOUSING_SOCIETY", "Mr. Sharma",
                            "1000000002", "gvs@society.org", "12 Green Avenue, South",
                            java.math.BigDecimal.valueOf(28.6741), java.math.BigDecimal.valueOf(77.2273),
                            z2, true, now
                    ));
                    subDao.insert(new ecoroute.model.HousingSociety(g2, "REG-HS-2021-002"));

                    long g3 = genDao.insert(new ecoroute.model.WasteGenerator(
                            null, "Prestige Industries Ltd", "FACTORY", "Ms. Rao",
                            "1000000003", "prestige@factory.com", "5 Industrial Area, East",
                            java.math.BigDecimal.valueOf(28.6448), java.math.BigDecimal.valueOf(77.2167),
                            z3, true, now
                    ));
                    subDao.insert(new ecoroute.model.Factory(g3, "PCN-FAC-2022-003"));

                    long g4 = genDao.insert(new ecoroute.model.WasteGenerator(
                            null, "Sunrise Hotel", "HOTEL", "Mr. Mehta",
                            "1000000004", "gm@sunrise.hotel", "88 MG Road, Central",
                            java.math.BigDecimal.valueOf(28.6517), java.math.BigDecimal.valueOf(77.2219),
                            z5, true, now
                    ));

                    long g5 = genDao.insert(new ecoroute.model.WasteGenerator(
                            null, "Delhi Public School", "SCHOOL", "Principal",
                            "1000000005", "dps@school.edu", "7 Sector-12, West",
                            java.math.BigDecimal.valueOf(28.6692), java.math.BigDecimal.valueOf(77.1543),
                            z4, true, now
                    ));

                    // 4. Users
                    String hash = PasswordHasher.hash("Password@123".toCharArray());
                    insertUser(c, "admin", hash, ADMIN, null);
                    insertUser(c, "operator1", hash, OPERATOR, null);
                    insertUser(c, "driver1", hash, DRIVER, null);
                    insertUser(c, "hospital_gen", hash, GENERATOR, g1);
                    insertUser(c, "society_gen", hash, GENERATOR, g2);
                    insertUser(c, "gen_hospital", hash, GENERATOR, g1); // alias for backwards compatibility

                    // 5. Vehicles
                    try (PreparedStatement s = c.prepareStatement(
                            "INSERT INTO VEHICLE (vehicle_number, vehicle_type, capacity_kg, assigned_zone_id, status) VALUES (?, ?, ?, ?, ?)")) {
                        s.setString(1, "DL-01-GA-0001"); s.setString(2, "Compactor Truck"); s.setBigDecimal(3, java.math.BigDecimal.valueOf(2000.00)); s.setLong(4, z1); s.setString(5, AVAILABLE); s.executeUpdate();
                        s.setString(1, "DL-02-GA-0002"); s.setString(2, "Tipper Truck"); s.setBigDecimal(3, java.math.BigDecimal.valueOf(3000.00)); s.setLong(4, z2); s.setString(5, AVAILABLE); s.executeUpdate();
                        s.setString(1, "DL-03-GA-0003"); s.setString(2, "Mini Van"); s.setBigDecimal(3, java.math.BigDecimal.valueOf(1000.00)); s.setLong(4, z3); s.setString(5, AVAILABLE); s.executeUpdate();
                        s.setString(1, "DL-04-GA-0004"); s.setString(2, "Hazmat Truck"); s.setBigDecimal(3, java.math.BigDecimal.valueOf(1500.00)); s.setLong(4, z4); s.setString(5, AVAILABLE); s.executeUpdate();
                        s.setString(1, "DL-05-GA-0005"); s.setString(2, "Compactor Truck"); s.setBigDecimal(3, java.math.BigDecimal.valueOf(2500.00)); s.setLong(4, z5); s.setString(5, MAINTENANCE); s.executeUpdate();
                    }

                    // 6. Disposal Facilities
                    try (PreparedStatement s = c.prepareStatement(
                            "INSERT INTO DISPOSAL_FACILITY (facility_name, facility_type, address, latitude, longitude, capacity_tonnes_per_day, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        s.setString(1, "North Recycling Plant"); s.setString(2, "RECYCLING_PLANT"); s.setString(3, "10 Industrial Rd North"); s.setDouble(4, 28.7200); s.setDouble(5, 77.0900); s.setBigDecimal(6, java.math.BigDecimal.valueOf(50.00)); s.setString(7, ACTIVE); s.executeUpdate();
                        long f1 = getGeneratedKey(s);
                        try (PreparedStatement sub = c.prepareStatement("INSERT INTO RECYCLING_PLANT (facility_id, recycling_license_no) VALUES (?, ?)")) {
                            sub.setLong(1, f1); sub.setString(2, "RPLAN-LIC-2019-001"); sub.executeUpdate();
                        }

                        s.setString(1, "South Processing Plant"); s.setString(2, "PROCESSING_PLANT"); s.setString(3, "30 MIDC South"); s.setDouble(4, 28.6200); s.setDouble(5, 77.3000); s.setBigDecimal(6, java.math.BigDecimal.valueOf(80.00)); s.setString(7, ACTIVE); s.executeUpdate();
                        long f2 = getGeneratedKey(s);
                        try (PreparedStatement sub = c.prepareStatement("INSERT INTO PROCESSING_PLANT (facility_id, processing_type) VALUES (?, ?)")) {
                            sub.setLong(1, f2); sub.setString(2, "Organic Composting"); sub.executeUpdate();
                        }

                        s.setString(1, "East Hazardous Disposal Site"); s.setString(2, "HAZARDOUS_DISPOSAL_SITE"); s.setString(3, "15 Chemical Zone East"); s.setDouble(4, 28.6100); s.setDouble(5, 77.4000); s.setBigDecimal(6, java.math.BigDecimal.valueOf(30.00)); s.setString(7, ACTIVE); s.executeUpdate();
                        long f3 = getGeneratedKey(s);
                        try (PreparedStatement sub = c.prepareStatement("INSERT INTO HAZARDOUS_DISPOSAL_SITE (facility_id, environmental_clearance_no) VALUES (?, ?)")) {
                            sub.setLong(1, f3); sub.setString(2, "ENV-CLR-2021-003"); sub.executeUpdate();
                        }

                        s.setString(1, "West Landfill"); s.setString(2, "LANDFILL"); s.setString(3, "99 Outskirts West"); s.setDouble(4, 28.6800); s.setDouble(5, 76.9500); s.setBigDecimal(6, java.math.BigDecimal.valueOf(200.00)); s.setString(7, ACTIVE); s.executeUpdate();
                        long f4 = getGeneratedKey(s);

                        // Accepted Waste
                        try (PreparedStatement aw = c.prepareStatement(
                                "INSERT INTO FACILITY_ACCEPTED_WASTE (facility_id, category_id, processing_rate_per_kg) VALUES (?, ?, ?)")) {
                            aw.setLong(1, f1); aw.setLong(2, c1); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(1.50)); aw.executeUpdate();
                            aw.setLong(1, f1); aw.setLong(2, c2); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(1.00)); aw.executeUpdate();
                            aw.setLong(1, f1); aw.setLong(2, c3); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(2.00)); aw.executeUpdate();
                            aw.setLong(1, f1); aw.setLong(2, c4); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(3.50)); aw.executeUpdate();

                            aw.setLong(1, f2); aw.setLong(2, c1); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(1.20)); aw.executeUpdate();
                            aw.setLong(1, f2); aw.setLong(2, c2); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(0.80)); aw.executeUpdate();

                            aw.setLong(1, f3); aw.setLong(2, c5); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(8.00)); aw.executeUpdate();
                            aw.setLong(1, f3); aw.setLong(2, c6); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(12.00)); aw.executeUpdate();

                            aw.setLong(1, f4); aw.setLong(2, c1); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(0.50)); aw.executeUpdate();
                            aw.setLong(1, f4); aw.setLong(2, c2); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(0.50)); aw.executeUpdate();
                            aw.setLong(1, f4); aw.setLong(2, c3); aw.setBigDecimal(3, java.math.BigDecimal.valueOf(0.50)); aw.executeUpdate();
                        }
                    }

                    // 7. Initial Pending Pickup Requests (for Today and Tomorrow)
                    java.time.LocalDate today = java.time.LocalDate.now();
                    java.time.LocalDate tomorrow = today.plusDays(1);
                    java.time.LocalDate yesterday = today.minusDays(1);

                    try (PreparedStatement reqStmt = c.prepareStatement(
                            "INSERT INTO PICKUP_REQUEST (generator_id, request_date, preferred_pickup_date, status, notes) VALUES (?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
                         PreparedStatement wasteStmt = c.prepareStatement(
                                 "INSERT INTO REQUEST_WASTE (request_id, category_id, estimated_quantity_kg) VALUES (?, ?, ?)")) {

                        // Req 1: Hospital (Zone 1) - Today
                        reqStmt.setLong(1, g1); reqStmt.setObject(2, now.minusHours(4)); reqStmt.setObject(3, today); reqStmt.setString(4, PENDING); reqStmt.setString(5, "Priority clinical and biomedical waste"); reqStmt.executeUpdate();
                        long r1 = getGeneratedKey(reqStmt);
                        wasteStmt.setLong(1, r1); wasteStmt.setLong(2, c6); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(120.00)); wasteStmt.executeUpdate();
                        wasteStmt.setLong(1, r1); wasteStmt.setLong(2, c1); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(50.00)); wasteStmt.executeUpdate();

                        // Req 2: Society (Zone 2) - Today
                        reqStmt.setLong(1, g2); reqStmt.setObject(2, now.minusHours(3)); reqStmt.setObject(3, today); reqStmt.setString(4, PENDING); reqStmt.setString(5, "Regular weekly residential collection"); reqStmt.executeUpdate();
                        long r2 = getGeneratedKey(reqStmt);
                        wasteStmt.setLong(1, r2); wasteStmt.setLong(2, c1); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(200.00)); wasteStmt.executeUpdate();
                        wasteStmt.setLong(1, r2); wasteStmt.setLong(2, c2); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(80.00)); wasteStmt.executeUpdate();

                        // Req 3: Factory (Zone 3) - Today
                        reqStmt.setLong(1, g3); reqStmt.setObject(2, now.minusHours(2)); reqStmt.setObject(3, today); reqStmt.setString(4, PENDING); reqStmt.setString(5, "Industrial chemical clearance batch"); reqStmt.executeUpdate();
                        long r3 = getGeneratedKey(reqStmt);
                        wasteStmt.setLong(1, r3); wasteStmt.setLong(2, c5); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(300.00)); wasteStmt.executeUpdate();

                        // Req 4: Hotel (Zone 5) - Today
                        reqStmt.setLong(1, g4); reqStmt.setObject(2, now.minusHours(1)); reqStmt.setObject(3, today); reqStmt.setString(4, PENDING); reqStmt.setString(5, "Dining and kitchen organic waste"); reqStmt.executeUpdate();
                        long r4 = getGeneratedKey(reqStmt);
                        wasteStmt.setLong(1, r4); wasteStmt.setLong(2, c1); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(60.00)); wasteStmt.executeUpdate();
                        wasteStmt.setLong(1, r4); wasteStmt.setLong(2, c2); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(40.00)); wasteStmt.executeUpdate();

                        // Req 5: School (Zone 4) - Tomorrow
                        reqStmt.setLong(1, g5); reqStmt.setObject(2, now); reqStmt.setObject(3, tomorrow); reqStmt.setString(4, PENDING); reqStmt.setString(5, "Paper and packaging waste"); reqStmt.executeUpdate();
                        long r5 = getGeneratedKey(reqStmt);
                        wasteStmt.setLong(1, r5); wasteStmt.setLong(2, c1); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(90.00)); wasteStmt.executeUpdate();
                        wasteStmt.setLong(1, r5); wasteStmt.setLong(2, c3); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(30.00)); wasteStmt.executeUpdate();
                    }

                    // 8. Completed Request, Route, RouteStop, and DisposalRecord (for immediate non-zero Dashboard metrics)
                    try (PreparedStatement reqStmt = c.prepareStatement(
                            "INSERT INTO PICKUP_REQUEST (generator_id, request_date, preferred_pickup_date, status, notes, completion_date) VALUES (?, ?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS);
                         PreparedStatement wasteStmt = c.prepareStatement(
                                 "INSERT INTO REQUEST_WASTE (request_id, category_id, estimated_quantity_kg, actual_quantity_kg) VALUES (?, ?, ?, ?)");
                         PreparedStatement routeStmt = c.prepareStatement(
                                 "INSERT INTO ROUTE (vehicle_id, zone_id, route_date, status, created_date) VALUES (?, ?, ?, ?, ?)",
                                 Statement.RETURN_GENERATED_KEYS);
                         PreparedStatement stopStmt = c.prepareStatement(
                                 "INSERT INTO ROUTE_STOP (route_id, request_id, stop_sequence, status, arrival_time, completion_time, notes) VALUES (?, ?, ?, ?, ?, ?, ?)");
                         PreparedStatement dispStmt = c.prepareStatement(
                                 "INSERT INTO DISPOSAL_RECORD (route_id, facility_id, disposal_date, gross_weight_kg, tare_weight_kg, net_disposed_weight_kg, receipt_number, status, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                        // Historical completed request
                        reqStmt.setLong(1, g1);
                        reqStmt.setObject(2, now.minusDays(1).minusHours(2));
                        reqStmt.setObject(3, yesterday);
                        reqStmt.setString(4, COMPLETED);
                        reqStmt.setString(5, "Priority morning medical waste");
                        reqStmt.setObject(6, now.minusDays(1));
                        reqStmt.executeUpdate();
                        long r6 = getGeneratedKey(reqStmt);

                        wasteStmt.setLong(1, r6); wasteStmt.setLong(2, c6); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(100.00)); wasteStmt.setBigDecimal(4, java.math.BigDecimal.valueOf(110.00)); wasteStmt.executeUpdate();
                        wasteStmt.setLong(1, r6); wasteStmt.setLong(2, c1); wasteStmt.setBigDecimal(3, java.math.BigDecimal.valueOf(40.00)); wasteStmt.setBigDecimal(4, java.math.BigDecimal.valueOf(40.00)); wasteStmt.executeUpdate();

                        // Route 1 (Vehicle 1 in Zone 1)
                        routeStmt.setLong(1, 1L);
                        routeStmt.setLong(2, z1);
                        routeStmt.setObject(3, today);
                        routeStmt.setString(4, COMPLETED);
                        routeStmt.setObject(5, now.minusDays(1));
                        routeStmt.executeUpdate();
                        long rt1 = getGeneratedKey(routeStmt);

                        // Stop 1
                        stopStmt.setLong(1, rt1);
                        stopStmt.setLong(2, r6);
                        stopStmt.setInt(3, 1);
                        stopStmt.setString(4, COMPLETED);
                        stopStmt.setObject(5, now.minusHours(5));
                        stopStmt.setObject(6, now.minusHours(4));
                        stopStmt.setString(7, "Collected safely and sealed");
                        stopStmt.executeUpdate();

                        // Disposal Record 1 (Facility 3 - East Hazardous)
                        dispStmt.setLong(1, rt1);
                        dispStmt.setLong(2, 3L);
                        dispStmt.setObject(3, now.minusHours(3));
                        dispStmt.setBigDecimal(4, java.math.BigDecimal.valueOf(2150.00));
                        dispStmt.setBigDecimal(5, java.math.BigDecimal.valueOf(2000.00));
                        dispStmt.setBigDecimal(6, java.math.BigDecimal.valueOf(150.00));
                        dispStmt.setString(7, "RCP-DEMO-" + (System.currentTimeMillis() % 100000));
                        dispStmt.setString(8, "VERIFIED");
                        dispStmt.setString(9, "Automated demo disposal check-in");
                        dispStmt.executeUpdate();
                    }

                    LOGGER.info("Realistic demo dataset seeded successfully.");
                }
                return null;
            });
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to seed demo data: " + ex.getMessage(), ex);
        }
    }

    private static long getGeneratedKey(PreparedStatement s) throws java.sql.SQLException {
        try (var rs = s.getGeneratedKeys()) {
            if (rs.next()) return rs.getLong(1);
            throw new java.sql.SQLException("No generated key returned.");
        }
    }

    private static void insertUser(Connection c, String username, String hash, String role, Long generatorId) throws java.sql.SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO `USER` (username, password_hash, role, generator_id, is_active, created_date) VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setString(1, username);
            s.setString(2, hash);
            s.setString(3, role);
            s.setObject(4, generatorId);
            s.setBoolean(5, true);
            s.setObject(6, LocalDateTime.now());
            s.executeUpdate();
        }
    }

    private static class DatabaseHolder {
        final Database database;
        final String info;

        DatabaseHolder(Database database, String info) {
            this.database = database;
            this.info = info;
        }
    }
}

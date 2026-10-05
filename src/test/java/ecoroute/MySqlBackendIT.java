package ecoroute;

import ecoroute.db.ConnectionProvider;
import ecoroute.db.DatabaseConnection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.regex.Pattern;

/** Opt-in only. Requires an explicitly acknowledged, dedicated, initially empty database. */
public class MySqlBackendIT extends BackendSmokeTest {
    private static boolean initialized;
    private static final Pattern TEST_URL = Pattern.compile(
            "jdbc:mysql://[a-zA-Z0-9.\\-]+(?::[0-9]+)?/(ecoroute_member2_test_[a-z0-9_]+)(?:\\?[^#]*)?");

    static ConnectionProvider testProvider(Map<String, String> env) {
        String url = env.get("ECOROUTE_TEST_DB_URL");
        String user = env.get("ECOROUTE_TEST_DB_USER");
        String password = env.get("ECOROUTE_TEST_DB_PASSWORD");
        if (url == null || user == null || user.isBlank() || password == null
                || !"YES".equals(env.get("ECOROUTE_TEST_DB_DISPOSABLE"))) {
            throw new IllegalStateException("Set ECOROUTE_TEST_DB_URL, ECOROUTE_TEST_DB_USER, ECOROUTE_TEST_DB_PASSWORD and ECOROUTE_TEST_DB_DISPOSABLE=YES.");
        }
        var match = TEST_URL.matcher(url);
        if (!match.matches()) throw new IllegalStateException("Test URL must name ecoroute_member2_test_<suffix> on a single MySQL host.");
        String appUrl = env.getOrDefault("ECOROUTE_DB_URL", "");
        if (appUrl.contains("/" + match.group(1))) throw new IllegalStateException("Application and test database names must differ.");
        if (url.toLowerCase(java.util.Locale.ROOT).contains("useaffectedrows=true")) {
            throw new IllegalStateException("Tests require Connector/J matched-row counts; omit useAffectedRows=true.");
        }
        return new DatabaseConnection(url, user, password);
    }
    @Override protected ConnectionProvider createProvider() { return testProvider(System.getenv()); }

    @Override protected void prepareFixture() throws Exception {
        if (!initialized) {
            // Refuse to touch an existing schema, even if its name looks like a test database.
            try (ResultSet tables = anchor.getMetaData().getTables(anchor.getCatalog(), null, "%", null)) {
                if (tables.next()) throw new IllegalStateException("MySQL test database must initially be empty. Provision a new disposable database.");
            }
            super.prepareFixture();
            initialized = true;
        } else {
            // Only tables created by this test process, after the empty-schema guard, are cleared.
            String[] tables = {"ROUTE_STOP", "REQUEST_WASTE", "PICKUP_REQUEST", "ROUTE", "VEHICLE",
                    "DISPOSAL_SITE", "GENERATOR_USER", "STAFF_USER", "USER", "HOSPITAL", "HOUSING_SOCIETY", "FACTORY", "WASTE_GENERATOR", "WASTE_CATEGORY", "ZONE"};
            try (Statement statement = anchor.createStatement()) {
                for (String table : tables) statement.executeUpdate("DELETE FROM `" + table + "`");
            }
        }
    }
}

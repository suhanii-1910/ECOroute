package ecoroute.db;

import ecoroute.exception.DatabaseOperationException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

/** Reads environment configuration; it does not load .env files. */
public final class DatabaseConnection implements ConnectionProvider {
    private final String url;
    private final String user;
    private final String password;

    public DatabaseConnection(String url, String user, String password) {
        if (url == null || url.isBlank() || user == null || user.isBlank() || password == null) {
            throw new DatabaseOperationException("Set ECOROUTE_DB_URL, ECOROUTE_DB_USER and ECOROUTE_DB_PASSWORD.");
        }
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DatabaseConnection fromEnvironment() { return fromEnvironment(System.getenv()); }

    public static DatabaseConnection fromEnvironment(Map<String, String> environment) {
        return new DatabaseConnection(environment.get("ECOROUTE_DB_URL"),
                environment.get("ECOROUTE_DB_USER"), environment.get("ECOROUTE_DB_PASSWORD"));
    }

    @Override
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}

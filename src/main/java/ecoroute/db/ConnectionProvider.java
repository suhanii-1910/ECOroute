package ecoroute.db;
import java.sql.Connection;
import java.sql.SQLException;

/** Each call must return a new, owned connection. */
@FunctionalInterface
public interface ConnectionProvider {
    Connection open() throws SQLException;
}

package ecoroute.db;

import ecoroute.exception.DatabaseOperationException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Owns connections and transaction boundaries. DAOs only borrow connections. */
public final class Database {
    private final ConnectionProvider provider;
    public Database(ConnectionProvider provider) { this.provider = Objects.requireNonNull(provider); }

    @FunctionalInterface
    public interface Work<T> { T run(Connection connection) throws SQLException; }

    public <T> T read(Work<T> work) {
        try (Connection connection = provider.open()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DatabaseOperationException("Database read failed. Check configuration and schema compatibility.", e);
        }
    }

    public <T> T transaction(Work<T> work) {
        try (Connection connection = provider.open()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { e.addSuppressed(rollbackFailure); }
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseOperationException("Database transaction failed; changes were not confirmed.", e);
        }
    }
}

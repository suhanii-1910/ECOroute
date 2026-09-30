package ecoroute.dao;

import ecoroute.exception.DatabaseOperationException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC helpers. Never commits, rolls back or closes a borrowed connection. */
final class Jdbc {
    private Jdbc() {}
    @FunctionalInterface interface Row<T> { T map(ResultSet result) throws SQLException; }

    static void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) statement.setNull(i + 1, Types.NULL);
            else statement.setObject(i + 1, values[i]);
        }
    }

    static <T> List<T> list(Connection connection, String sql, Row<T> row, Object... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (result.next()) rows.add(row.map(result));
                return rows;
            }
        } catch (SQLException e) { throw failure(e); }
    }

    static <T> Optional<T> one(Connection connection, String sql, Row<T> row, Object... values) {
        List<T> rows = list(connection, sql, row, values);
        if (rows.size() > 1) throw new DatabaseOperationException("Expected at most one database row.");
        return rows.stream().findFirst();
    }

    static void change(Connection connection, String sql, Object... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            requireOne(statement.executeUpdate());
        } catch (SQLException e) { throw failure(e); }
    }

    static long insert(Connection connection, String sql, Object... values) {
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            requireOne(statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new DatabaseOperationException("Insert returned no generated ID; confirm the ID contract.");
                return keys.getLong(1);
            }
        } catch (SQLException e) { throw failure(e); }
    }

    private static void requireOne(int count) {
        if (count != 1) throw new DatabaseOperationException("Expected one affected row; found " + count + ". Record missing or state changed.");
    }

    private static DatabaseOperationException failure(SQLException e) {
        return new DatabaseOperationException("Database operation failed (SQLState " + e.getSQLState() + ").", e);
    }

    static Long nullableLong(ResultSet r, String column) throws SQLException {
        long value = r.getLong(column);
        return r.wasNull() ? null : value;
    }
}

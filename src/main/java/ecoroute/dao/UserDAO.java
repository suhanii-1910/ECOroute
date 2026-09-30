package ecoroute.dao;

import ecoroute.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class UserDAO {
    private final Connection connection;
    private static final String SELECT = "SELECT user_id, username, password_hash, role, generator_id, is_active, created_date FROM `USER`";
    public UserDAO(Connection connection) { this.connection = connection; }
    private static User map(ResultSet r) throws SQLException {
        return new User(
                Jdbc.nullableLong(r, "user_id"),
                r.getString("username"),
                r.getString("password_hash"),
                r.getString("role"),
                Jdbc.nullableLong(r, "generator_id"),
                r.getBoolean("is_active"),
                r.getObject("created_date", java.time.LocalDateTime.class));
    }

    public Optional<User> findById(long id) {
        return Jdbc.one(connection, SELECT + " WHERE user_id = ?", UserDAO::map, id);
    }

    public Optional<User> findByUsername(String username) {
        return Jdbc.one(connection, SELECT + " WHERE username = ?", UserDAO::map, username);
    }
}

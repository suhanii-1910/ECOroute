package ecoroute.dao;

import ecoroute.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Uses a borrowed connection; caller owns its lifetime and transactions. */
public final class UserDAO {
    private final Connection connection;
    private static final String SELECT =
            "SELECT user_id, username, password_hash, email, full_name, role, generator_id, is_active, created_date FROM `USER`";
    public UserDAO(Connection connection) { this.connection = connection; }
    private static User map(ResultSet r) throws SQLException {
        return new User(
                Jdbc.nullableLong(r, "user_id"),
                r.getString("username"),
                r.getString("password_hash"),
                r.getString("email"),
                r.getString("full_name"),
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

    public java.util.List<User> findAll() {
        return Jdbc.list(connection, SELECT + " ORDER BY user_id ASC", UserDAO::map);
    }

    public long insert(User user) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            email = user.getUsername() != null ? user.getUsername() + "@ecoroute.local" : null;
        }
        String fullName = user.getFullName();
        if (fullName == null || fullName.isBlank()) {
            fullName = user.getUsername() != null ? user.getUsername() : "";
        }
        return Jdbc.insert(connection,
                "INSERT INTO `USER` (username, password_hash, email, full_name, role, generator_id, is_active, created_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                user.getUsername(), user.getPasswordHash(), email, fullName, user.getRole(), user.getGeneratorId(), user.isActive(),
                user.getCreatedDate() != null ? user.getCreatedDate() : java.time.LocalDateTime.now());
    }

    public void updateActive(long userId, boolean active) {
        Jdbc.change(connection, "UPDATE `USER` SET is_active = ? WHERE user_id = ?", active, userId);
    }
}

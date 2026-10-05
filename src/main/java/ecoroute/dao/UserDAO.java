package ecoroute.dao;

import ecoroute.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    /** Marker tables have no extra domain fields. Preserve the existing role-to-subtype mapping. */
    public boolean hasValidSubtype(User user) {
        return Jdbc.one(connection, """
                SELECT g.user_id AS generator_user_id, s.user_id AS staff_user_id
                FROM `USER` u
                LEFT JOIN GENERATOR_USER g ON g.user_id = u.user_id
                LEFT JOIN STAFF_USER s ON s.user_id = u.user_id
                WHERE u.user_id = ?
                """, r -> {
            boolean generator = Jdbc.nullableLong(r, "generator_user_id") != null;
            boolean staff = Jdbc.nullableLong(r, "staff_user_id") != null;
            return ecoroute.db.DatabaseContract.GENERATOR.equals(user.getRole())
                    ? generator && !staff : staff && !generator;
        }, user.getUserId()).orElse(false);
    }
}

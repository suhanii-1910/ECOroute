package ecoroute.service;

import ecoroute.dao.UserDAO;
import ecoroute.db.Database;
import ecoroute.model.User;
import ecoroute.exception.InvalidRequestException;
import static ecoroute.db.DatabaseContract.*;

public final class AuthService {
    private final Database database;
    // Used for unknown usernames too, so password derivation still happens.
    private final String dummyHash = PasswordHasher.hash("unused-dummy-password".toCharArray());
    public AuthService(Database database) { this.database = database; }

    /** Caller should clear its char[] after this call. */
    public UserSession login(String username, char[] password) {
        Validation.text(username, "Username");
        User user = database.read(c -> {
            UserDAO users = new UserDAO(c);
            return users.findByUsername(username).filter(users::hasValidSubtype).orElse(null);
        });
        boolean valid = PasswordHasher.verify(password, user == null ? dummyHash : user.getPasswordHash());
        if (!valid || user == null || !user.isActive() || user.getRole() == null || !ROLES.contains(user.getRole())
                || (GENERATOR.equals(user.getRole()) ? user.getGeneratorId() == null : user.getGeneratorId() != null)) {
            throw new InvalidRequestException("Invalid credentials or inactive account.");
        }
        return new UserSession(user.getUserId(), user.getUsername(), user.getRole(), user.getGeneratorId());
    }
}

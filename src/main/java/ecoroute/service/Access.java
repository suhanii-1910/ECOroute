package ecoroute.service;

import ecoroute.dao.UserDAO;
import ecoroute.model.User;
import ecoroute.exception.InvalidRequestException;
import java.sql.Connection;
import java.util.Objects;
import static ecoroute.db.DatabaseContract.*;

final class Access {
    private Access() {}
    static User current(Connection connection, UserSession session) {
        Validation.require(session != null, "Authentication is required.");
        User user = new UserDAO(connection).findById(session.getUserId())
                .orElseThrow(() -> new InvalidRequestException("Session user no longer exists."));
        Validation.require(user.isActive() && user.getRole() != null && ROLES.contains(user.getRole()), "User is inactive or has an invalid role.");
        Validation.require(new UserDAO(connection).hasValidSubtype(user), "User subtype is missing or inconsistent.");
        Validation.require(user.getRole().equals(session.getRole())
                && Objects.equals(user.getGeneratorId(), session.getGeneratorId()), "User permissions changed; sign in again.");
        Validation.require(GENERATOR.equals(user.getRole()) ? user.getGeneratorId() != null : user.getGeneratorId() == null,
                "User role and generator association are inconsistent.");
        return user;
    }
    static void staff(Connection connection, UserSession session) {
        User user = current(connection, session);
        Validation.require(ADMIN.equals(user.getRole()) || OPERATOR.equals(user.getRole()), "Staff access is required.");
    }
    static void admin(Connection connection, UserSession session) {
        Validation.require(ADMIN.equals(current(connection, session).getRole()), "Admin access is required.");
    }
    static void generator(Connection connection, UserSession session, long generatorId) {
        User user = current(connection, session);
        Validation.require(!GENERATOR.equals(user.getRole()) || Objects.equals(user.getGeneratorId(), generatorId),
                "You cannot access another generator's requests.");
    }
}

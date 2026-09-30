package ecoroute.service;

/** Immutable, hash-free context issued by AuthService. Do not construct from GUI input. */
public final class UserSession {
    private final long userId;
    private final String username;
    private final String role;
    private final Long generatorId;
    UserSession(long userId, String username, String role, Long generatorId) {
        this.userId = userId; this.username = username; this.role = role; this.generatorId = generatorId;
    }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public Long getGeneratorId() { return generatorId; }
    @Override public String toString() { return "UserSession{" + userId + ", " + username + ", " + role + "}"; }
}

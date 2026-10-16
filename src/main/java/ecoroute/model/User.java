package ecoroute.model;

import java.time.LocalDateTime;

/** JDBC data holder. A null surrogate ID means it has not been inserted yet. */
public class User {
    private Long userId;
    private String username;
    private String passwordHash;
    private String email;
    private String fullName;
    private String role;
    private Long generatorId;
    private boolean active;
    private LocalDateTime createdDate;

    public User() {}

    public User(Long userId, String username, String passwordHash, String role, Long generatorId, boolean active, LocalDateTime createdDate) {
        this(userId, username, passwordHash, username != null ? username + "@ecoroute.local" : null, username != null ? username : "", role, generatorId, active, createdDate);
    }

    public User(Long userId, String username, String passwordHash, String email, String fullName, String role, Long generatorId, boolean active, LocalDateTime createdDate) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.generatorId = generatorId;
        this.active = active;
        this.createdDate = createdDate;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Long getGeneratorId() { return generatorId; }
    public void setGeneratorId(Long generatorId) { this.generatorId = generatorId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    @Override
    public String toString() {
        return "User{" + userId + ", " + username + "}";
    }
}

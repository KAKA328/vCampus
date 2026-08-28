package cn.vcampus.user;

import cn.vcampus.common.User;
import java.time.Instant;
import java.util.Objects;

/** Stored account data; passwordHash never contains a clear-text password. */
public final class UserAccount {
    private final User user;
    private final String passwordHash;
    private final boolean active;
    private final String createdBy;
    private final Instant createdAt;
    private final String importBatchId;

    public UserAccount(User user, String passwordHash, boolean active) {
        this(user, passwordHash, active, null, Instant.now(), null);
    }

    public UserAccount(User user, String passwordHash, boolean active,
                       String createdBy, Instant createdAt, String importBatchId) {
        this.user = Objects.requireNonNull(user, "user");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.active = active;
        this.createdBy = optionalText(createdBy);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.importBatchId = optionalText(importBatchId);
    }

    public User getUser() { return user; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public String getImportBatchId() { return importBatchId; }

    public UserAccount deactivate() {
        return new UserAccount(user, passwordHash, false, createdBy, createdAt, importBatchId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

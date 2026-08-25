package cn.vcampus.user;

import cn.vcampus.common.User;
import java.util.Objects;

/** Stored account data; passwordHash never contains a clear-text password. */
public final class UserAccount {
    private final User user;
    private final String passwordHash;
    private final boolean active;

    public UserAccount(User user, String passwordHash, boolean active) {
        this.user = Objects.requireNonNull(user, "user");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.active = active;
    }

    public User getUser() { return user; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }

    public UserAccount deactivate() {
        return new UserAccount(user, passwordHash, false);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

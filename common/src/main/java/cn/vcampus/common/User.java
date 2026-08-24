package cn.vcampus.common;

import java.io.Serializable;
import java.util.Objects;

/** Shared user value object. Passwords are never stored in this object. */
public final class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String displayName;
    private final Role role;

    public User(String userId, String displayName, Role role) {
        this.userId = requireText(userId, "userId");
        this.displayName = requireText(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role");
    }

    /** Constructor used by registration tests and adapters; password is validated but not retained. */
    public User(String userId, String password, String displayName, Role role) {
        this(userId, displayName, role);
        requireText(password, "password");
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof User)) return false;
        User that = (User) other;
        return userId.equals(that.userId) && displayName.equals(that.displayName) && role == that.role;
    }

    @Override public int hashCode() { return Objects.hash(userId, displayName, role); }
}

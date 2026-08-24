package cn.vcampus.user;

import java.io.Serializable;
import java.util.Objects;

/** Registration/login input. The password is kept only for the service call. */
public final class UserCredentials implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId; private final String password; private final String displayName; private final String roleCode;
    public UserCredentials(String userId, String password, String displayName, String roleCode) {
        this.userId = require(userId, "userId"); this.password = requirePassword(password);
        this.displayName = require(displayName, "displayName"); this.roleCode = require(roleCode, "roleCode");
    }
    public String getUserId() { return userId; } public String getPassword() { return password; }
    public String getDisplayName() { return displayName; } public String getRoleCode() { return roleCode; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserCredentials)) return false;
        UserCredentials that = (UserCredentials) other;
        return userId.equals(that.userId) && password.equals(that.password)
                && displayName.equals(that.displayName) && roleCode.equals(that.roleCode);
    }

    @Override public int hashCode() {
        return Objects.hash(userId, password, displayName, roleCode);
    }
    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }

    private static String requirePassword(String value) {
        if (value == null || value.length() < 6 || value.length() > 16) {
            throw new IllegalArgumentException("password must contain 6 to 16 characters");
        }
        return value;
    }
}

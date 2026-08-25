package cn.vcampus.user;

import java.io.Serializable;
import java.util.Objects;

/** Registration/login input. The password is kept only for the service call. */
public final class UserCredentials implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_USER_ID_LENGTH = 32;
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;
    private final String userId; private final String password; private final String displayName; private final String roleCode;
    public UserCredentials(String userId, String password, String displayName, String roleCode) {
        this.userId = requireUserId(userId); this.password = requirePassword(password);
        this.displayName = requireDisplayName(displayName); this.roleCode = require(roleCode, "roleCode");
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

    private static String requireUserId(String value) {
        String text = require(value, "userId");
        if (text.length() > MAX_USER_ID_LENGTH || !text.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("账号需为 1-32 位字母、数字或下划线");
        }
        return text;
    }

    private static String requireDisplayName(String value) {
        String text = require(value, "displayName");
        if (text.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("姓名需为 1-64 位中文或英文");
        }
        return text;
    }

    private static String requirePassword(String value) {
        if (value == null || value.length() < 6 || value.length() > 16) {
            throw new IllegalArgumentException("密码需为 6-16 位");
        }
        return value;
    }
}

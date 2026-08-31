package cn.vcampus.user;

import java.io.Serializable;

/** Administrator request to change another user's role. */
public final class UserRoleChangeCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String userId;
    private final String roleCode;

    public UserRoleChangeCommand(String token, String userId, String roleCode) {
        this.token = require(token, "token");
        this.userId = require(userId, "userId");
        this.roleCode = require(roleCode, "roleCode");
    }

    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

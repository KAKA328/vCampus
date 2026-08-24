package cn.vcampus.user;

import java.io.Serializable;

/** Payload for a server-side permission check. */
public final class AuthorizationRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String permission;

    public AuthorizationRequest(String token, String permission) {
        this.token = require(token, "token");
        this.permission = require(permission, "permission");
    }

    public String getToken() { return token; }
    public String getPermission() { return permission; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

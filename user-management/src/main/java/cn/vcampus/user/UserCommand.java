package cn.vcampus.user;

import java.io.Serializable;

/** Payload for user operations that need an authenticated subject. */
public final class UserCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;
    private final String token;

    public UserCommand(String userId, String token) {
        this.userId = require(userId, "userId");
        this.token = require(token, "token");
    }

    public String getUserId() { return userId; }
    public String getToken() { return token; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

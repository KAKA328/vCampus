package cn.vcampus.user;

import java.io.Serializable;

/** Admin decision for a pending password reset application. */
public final class PasswordResetReviewCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String userId;
    private final boolean approved;

    public PasswordResetReviewCommand(String token, String userId, boolean approved) {
        this.token = require(token, "token");
        this.userId = require(userId, "userId");
        this.approved = approved;
    }

    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public boolean isApproved() { return approved; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

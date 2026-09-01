package cn.vcampus.user;

import java.io.Serializable;

/** Authenticated password-change request used after forced temporary-password login. */
public final class PasswordChangeCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String newPassword;

    public PasswordChangeCommand(String token, String newPassword) {
        this.token = require(token, "token");
        this.newPassword = requirePassword(newPassword);
    }

    public String getToken() { return token; }
    public String getNewPassword() { return newPassword; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requirePassword(String value) {
        if (value == null || value.length() < 6 || value.length() > 16) {
            throw new IllegalArgumentException("密码需为 6-16 位");
        }
        return value;
    }
}

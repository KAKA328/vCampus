package cn.vcampus.user;

import java.io.Serializable;

/** Result visible only to the administrator after reviewing a reset application. */
public final class PasswordResetReviewResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String temporaryPassword;

    public PasswordResetReviewResult(String userId, String temporaryPassword) {
        this.userId = userId == null ? "" : userId.trim();
        this.temporaryPassword = temporaryPassword == null ? "" : temporaryPassword;
    }

    public String getUserId() { return userId; }
    public String getTemporaryPassword() { return temporaryPassword; }
}

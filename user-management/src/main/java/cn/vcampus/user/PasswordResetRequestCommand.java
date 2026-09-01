package cn.vcampus.user;

import java.io.Serializable;

/** Public password reset request submitted before login. */
public final class PasswordResetRequestCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_USER_ID_LENGTH = 32;
    private final String userId;
    private final String reason;
    private final String contactInfo;

    public PasswordResetRequestCommand(String userId, String reason) {
        this(userId, reason, "");
    }

    public PasswordResetRequestCommand(String userId, String reason, String contactInfo) {
        this.userId = requireUserId(userId);
        this.reason = requireReason(reason);
        this.contactInfo = contactInfo == null ? "" : contactInfo.trim();
    }

    public String getUserId() { return userId; }
    public String getReason() { return reason; }
    public String getContactInfo() { return contactInfo; }

    private static String requireUserId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        String text = value.trim();
        if (text.length() > MAX_USER_ID_LENGTH || !text.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("账号需为 1-32 位字母、数字或下划线");
        }
        return text;
    }

    private static String requireReason(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 120) {
            throw new IllegalArgumentException("申请原因需为 1-120 个字符");
        }
        return value.trim();
    }
}

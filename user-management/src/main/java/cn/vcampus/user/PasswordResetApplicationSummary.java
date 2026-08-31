package cn.vcampus.user;

import java.io.Serializable;
import java.time.Instant;

/** Admin-visible password reset application without any password hash. */
public final class PasswordResetApplicationSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;
    private final String displayName;
    private final String roleCode;
    private final String profileId;
    private final String reason;
    private final String contactInfo;
    private final Instant submittedAt;
    private final PasswordResetStatus status;

    public PasswordResetApplicationSummary(String userId, Instant submittedAt, PasswordResetStatus status) {
        this(userId, "", "", "", "", "", submittedAt, status);
    }

    public PasswordResetApplicationSummary(String userId, String displayName, String roleCode,
                                           String profileId, String reason, String contactInfo,
                                           Instant submittedAt, PasswordResetStatus status) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        this.userId = userId.trim();
        this.displayName = displayName == null ? "" : displayName.trim();
        this.roleCode = roleCode == null ? "" : roleCode.trim();
        this.profileId = profileId == null ? "" : profileId.trim();
        this.reason = reason == null ? "" : reason.trim();
        this.contactInfo = contactInfo == null ? "" : contactInfo.trim();
        this.submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        this.status = status == null ? PasswordResetStatus.PENDING : status;
    }

    public String getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getRoleCode() { return roleCode; }
    public String getProfileId() { return profileId; }
    public String getReason() { return reason; }
    public String getContactInfo() { return contactInfo; }
    public Instant getSubmittedAt() { return submittedAt; }
    public PasswordResetStatus getStatus() { return status; }
}

package cn.vcampus.user;

import java.time.Instant;

/** Stored password reset application. No user-provided new password is stored. */
public final class PasswordResetApplication {
    private final String userId;
    private final String reason;
    private final String contactInfo;
    private final Instant submittedAt;
    private final PasswordResetStatus status;
    private final String reviewedBy;
    private final Instant reviewedAt;

    public PasswordResetApplication(String userId, String reason, String contactInfo, Instant submittedAt,
                                    PasswordResetStatus status, String reviewedBy, Instant reviewedAt) {
        this.userId = require(userId, "userId");
        this.reason = require(reason, "reason");
        this.contactInfo = optional(contactInfo);
        this.submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        this.status = status == null ? PasswordResetStatus.PENDING : status;
        this.reviewedBy = optional(reviewedBy);
        this.reviewedAt = reviewedAt;
    }

    public String getUserId() { return userId; }
    public String getReason() { return reason; }
    public String getContactInfo() { return contactInfo == null ? "" : contactInfo; }
    public Instant getSubmittedAt() { return submittedAt; }
    public PasswordResetStatus getStatus() { return status; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }

    public PasswordResetApplication reviewed(PasswordResetStatus newStatus, String reviewerUserId, Instant time) {
        if (newStatus == PasswordResetStatus.PENDING) {
            throw new IllegalArgumentException("review status must be final");
        }
        return new PasswordResetApplication(userId, reason, contactInfo, submittedAt,
                newStatus, reviewerUserId, time);
    }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

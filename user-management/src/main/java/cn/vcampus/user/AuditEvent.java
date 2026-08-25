package cn.vcampus.user;

import java.time.Instant;

/** Audit event for sensitive user-management operations. */
public final class AuditEvent {
    private final String actorUserId;
    private final String action;
    private final String targetType;
    private final String targetId;
    private final Instant createdAt;

    public AuditEvent(String actorUserId, String action, String targetType, String targetId, Instant createdAt) {
        this.actorUserId = require(actorUserId, "actorUserId");
        this.action = require(action, "action");
        this.targetType = require(targetType, "targetType");
        this.targetId = require(targetId, "targetId");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public String getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Instant getCreatedAt() { return createdAt; }

    private static String require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

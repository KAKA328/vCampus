package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 一次成绩提交、通过或退回操作的不可变审计记录。 */
public final class GradeSubmissionAuditRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String auditId;
    private final String submissionId;
    private final GradeSubmissionAuditAction action;
    private final String actorId;
    private final LocalDateTime occurredAt;
    private final String remark;

    public GradeSubmissionAuditRecord(String auditId, String submissionId,
            GradeSubmissionAuditAction action, String actorId, LocalDateTime occurredAt,
            String remark) {
        this.auditId = requireText(auditId, "auditId");
        this.submissionId = requireText(submissionId, "submissionId");
        if (action == null || occurredAt == null) {
            throw new IllegalArgumentException("action and occurredAt must not be null");
        }
        this.action = action;
        this.actorId = requireText(actorId, "actorId");
        this.occurredAt = occurredAt;
        this.remark = normalize(remark);
    }

    public String getAuditId() { return auditId; }
    public String getSubmissionId() { return submissionId; }
    public GradeSubmissionAuditAction getAction() { return action; }
    public String getActorId() { return actorId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getRemark() { return remark; }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

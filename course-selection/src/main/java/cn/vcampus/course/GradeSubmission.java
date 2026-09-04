package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一个教学班的一次完整成绩提交单。
 *
 * <p>草稿和审核过程独立于学籍的正式课程结果；只有审核通过后，后续流程才会写入
 * {@code tblCourseResult}。</p>
 */
public final class GradeSubmission implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String submissionId;
    private final String offeringId;
    private final String teacherId;
    private final GradeSubmissionStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public GradeSubmission(String submissionId, String offeringId, String teacherId,
            GradeSubmissionStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.submissionId = requireText(submissionId, "submissionId");
        this.offeringId = requireText(offeringId, "offeringId");
        this.teacherId = requireText(teacherId, "teacherId");
        if (status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("status, createdAt and updatedAt must not be null");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static GradeSubmission draft(String submissionId, String offeringId, String teacherId,
            LocalDateTime createdAt) {
        return new GradeSubmission(submissionId, offeringId, teacherId, GradeSubmissionStatus.DRAFT,
                createdAt, createdAt);
    }

    public String getSubmissionId() { return submissionId; }
    public String getOfferingId() { return offeringId; }
    public String getTeacherId() { return teacherId; }
    public GradeSubmissionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** 录入或修改单个学生成绩后更新草稿的最后修改时间。 */
    public GradeSubmission withUpdatedAt(LocalDateTime newUpdatedAt) {
        return new GradeSubmission(submissionId, offeringId, teacherId, status, createdAt,
                newUpdatedAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

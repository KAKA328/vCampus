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
    /** 任课教师显示名称，仅用于回传界面展示。 */
    private final String teacherName;
    private final GradeSubmissionStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final String reviewedBy;
    private final LocalDateTime reviewedAt;
    private final String reviewRemark;

    public GradeSubmission(String submissionId, String offeringId, String teacherId,
            GradeSubmissionStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(submissionId, offeringId, teacherId, status, createdAt, updatedAt, null, null, null,
                null);
    }

    public GradeSubmission(String submissionId, String offeringId, String teacherId,
            GradeSubmissionStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
            String reviewedBy, LocalDateTime reviewedAt, String reviewRemark) {
        this(submissionId, offeringId, teacherId, status, createdAt, updatedAt, reviewedBy,
                reviewedAt, reviewRemark, null);
    }

    private GradeSubmission(String submissionId, String offeringId, String teacherId,
            GradeSubmissionStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
            String reviewedBy, LocalDateTime reviewedAt, String reviewRemark, String teacherName) {
        this.submissionId = requireText(submissionId, "submissionId");
        this.offeringId = requireText(offeringId, "offeringId");
        this.teacherId = requireText(teacherId, "teacherId");
        this.teacherName = normalize(teacherName);
        if (status == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("status, createdAt and updatedAt must not be null");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.reviewedBy = normalize(reviewedBy);
        this.reviewedAt = reviewedAt;
        this.reviewRemark = normalize(reviewRemark);
        if ((this.reviewedBy == null) != (this.reviewedAt == null)) {
            throw new IllegalArgumentException("reviewedBy and reviewedAt must be provided together");
        }
    }

    public static GradeSubmission draft(String submissionId, String offeringId, String teacherId,
            LocalDateTime createdAt) {
        return new GradeSubmission(submissionId, offeringId, teacherId, GradeSubmissionStatus.DRAFT,
                createdAt, createdAt);
    }

    public String getSubmissionId() { return submissionId; }
    public String getOfferingId() { return offeringId; }
    public String getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; }
    public String getTeacherDisplayName() { return teacherName == null ? teacherId : teacherName; }
    public GradeSubmissionStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getReviewRemark() { return reviewRemark; }

    /** 录入或修改单个学生成绩后更新草稿的最后修改时间。 */
    public GradeSubmission withUpdatedAt(LocalDateTime newUpdatedAt) {
        return new GradeSubmission(submissionId, offeringId, teacherId, status, createdAt,
                newUpdatedAt, reviewedBy, reviewedAt, reviewRemark, teacherName);
    }

    /** 教师提交或再次提交后清除上次审核意见，进入待审核状态。 */
    public GradeSubmission pendingReview(LocalDateTime newUpdatedAt) {
        return new GradeSubmission(submissionId, offeringId, teacherId,
                GradeSubmissionStatus.PENDING_REVIEW, createdAt, newUpdatedAt, null, null, null,
                teacherName);
    }

    /** 保存教务老师的审核结论和退回意见。 */
    public GradeSubmission reviewed(GradeSubmissionStatus newStatus, String newReviewedBy,
            String newReviewRemark, LocalDateTime newReviewedAt) {
        if (newStatus != GradeSubmissionStatus.APPROVED
                && newStatus != GradeSubmissionStatus.RETURNED) {
            throw new IllegalArgumentException("review outcome must be approved or returned");
        }
        return new GradeSubmission(submissionId, offeringId, teacherId, newStatus, createdAt,
                newReviewedAt, newReviewedBy, newReviewedAt, newReviewRemark, teacherName);
    }

    /** 返回附带教师档案显示名称的新成绩提交单对象。 */
    public GradeSubmission withTeacherName(String newTeacherName) {
        return new GradeSubmission(submissionId, offeringId, teacherId, status, createdAt,
                updatedAt, reviewedBy, reviewedAt, reviewRemark, newTeacherName);
    }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

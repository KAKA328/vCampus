package cn.vcampus.student;

import java.io.Serializable;
import java.time.Instant;

/** Computed summary for academic-progress and graduation-readiness checks. */
public final class AcademicReview implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String reviewId;
    private final int totalEarnedCredits;
    private final int requiredEarnedCredits;
    private final int passedCourseCount;
    private final int failedCourseCount;
    private final int retakeCourseCount;
    private final boolean graduationReady;
    private final String remark;
    private final String reviewedBy;
    private final Instant reviewedAt;

    public AcademicReview(
            String studentId,
            int totalEarnedCredits,
            int passedCourseCount,
            int failedCourseCount,
            int retakeCourseCount,
            boolean graduationReady,
            String remark
    ) {
        this(null, studentId, totalEarnedCredits, 0, passedCourseCount, failedCourseCount,
                retakeCourseCount, graduationReady, null, null, remark);
    }

    /** Complete snapshot form matching tblAcademicReview columns. */
    public AcademicReview(
            String reviewId,
            String studentId,
            int totalEarnedCredits,
            int requiredEarnedCredits,
            int passedCourseCount,
            int failedCourseCount,
            int retakeCourseCount,
            boolean graduationReady,
            String reviewedBy,
            Instant reviewedAt,
            String remark
    ) {
        this.studentId = studentId;
        this.reviewId = reviewId;
        this.totalEarnedCredits = totalEarnedCredits;
        this.requiredEarnedCredits = requiredEarnedCredits;
        this.passedCourseCount = passedCourseCount;
        this.failedCourseCount = failedCourseCount;
        this.retakeCourseCount = retakeCourseCount;
        this.graduationReady = graduationReady;
        this.remark = remark;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public String getStudentId() { return studentId; }
    public String getReviewId() { return reviewId; }
    public int getTotalEarnedCredits() { return totalEarnedCredits; }
    public int getRequiredEarnedCredits() { return requiredEarnedCredits; }
    public int getCreditShortfall() {
        return Math.max(0, requiredEarnedCredits - totalEarnedCredits);
    }
    public int getPassedCourseCount() { return passedCourseCount; }
    public int getFailedCourseCount() { return failedCourseCount; }
    public int getRetakeCourseCount() { return retakeCourseCount; }
    public boolean isGraduationReady() { return graduationReady; }
    public String getRemark() { return remark; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}

package cn.vcampus.student;

import cn.vcampus.common.CreditFormat;
import java.io.Serializable;
import java.time.Instant;
import java.math.BigDecimal;

/** Computed summary for academic-progress and graduation-readiness checks. */
public final class AcademicReview implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String reviewId;
    private final BigDecimal totalEarnedCredits;
    private final BigDecimal requiredEarnedCredits;
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
        this(studentId, BigDecimal.valueOf(totalEarnedCredits), passedCourseCount, failedCourseCount,
                retakeCourseCount, graduationReady, remark);
    }
    public AcademicReview(String studentId, BigDecimal totalEarnedCredits, int passedCourseCount,
            int failedCourseCount, int retakeCourseCount, boolean graduationReady, String remark) {
        this(null, studentId, totalEarnedCredits, BigDecimal.ZERO, passedCourseCount, failedCourseCount,
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
        this(reviewId, studentId, BigDecimal.valueOf(totalEarnedCredits),
                BigDecimal.valueOf(requiredEarnedCredits), passedCourseCount, failedCourseCount,
                retakeCourseCount, graduationReady, reviewedBy, reviewedAt, remark);
    }
    public AcademicReview(String reviewId, String studentId, BigDecimal totalEarnedCredits,
            BigDecimal requiredEarnedCredits, int passedCourseCount, int failedCourseCount,
            int retakeCourseCount, boolean graduationReady, String reviewedBy, Instant reviewedAt,
            String remark) {
        this.studentId = studentId;
        this.reviewId = reviewId;
        this.totalEarnedCredits = CreditFormat.nonNegative(totalEarnedCredits, "totalEarnedCredits");
        this.requiredEarnedCredits = CreditFormat.nonNegative(requiredEarnedCredits, "requiredEarnedCredits");
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
    public BigDecimal getTotalEarnedCredits() { return totalEarnedCredits; }
    public BigDecimal getRequiredEarnedCredits() { return requiredEarnedCredits; }
    public BigDecimal getCreditShortfall() {
        return requiredEarnedCredits.subtract(totalEarnedCredits).max(BigDecimal.ZERO);
    }
    public int getPassedCourseCount() { return passedCourseCount; }
    public int getFailedCourseCount() { return failedCourseCount; }
    public int getRetakeCourseCount() { return retakeCourseCount; }
    public boolean isGraduationReady() { return graduationReady; }
    public String getRemark() { return remark; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}

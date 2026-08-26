package cn.vcampus.student;

import java.io.Serializable;

/** Computed summary for academic-progress and graduation-readiness checks. */
public final class AcademicReview implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final int totalEarnedCredits;
    private final int passedCourseCount;
    private final int failedCourseCount;
    private final int retakeCourseCount;
    private final boolean graduationReady;
    private final String remark;

    public AcademicReview(
            String studentId,
            int totalEarnedCredits,
            int passedCourseCount,
            int failedCourseCount,
            int retakeCourseCount,
            boolean graduationReady,
            String remark
    ) {
        this.studentId = studentId;
        this.totalEarnedCredits = totalEarnedCredits;
        this.passedCourseCount = passedCourseCount;
        this.failedCourseCount = failedCourseCount;
        this.retakeCourseCount = retakeCourseCount;
        this.graduationReady = graduationReady;
        this.remark = remark;
    }

    public String getStudentId() { return studentId; }
    public int getTotalEarnedCredits() { return totalEarnedCredits; }
    public int getPassedCourseCount() { return passedCourseCount; }
    public int getFailedCourseCount() { return failedCourseCount; }
    public int getRetakeCourseCount() { return retakeCourseCount; }
    public boolean isGraduationReady() { return graduationReady; }
    public String getRemark() { return remark; }
}

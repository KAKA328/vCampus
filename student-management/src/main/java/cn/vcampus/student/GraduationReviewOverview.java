package cn.vcampus.student;

import java.io.Serializable;

/** Read-only data needed to render one student's graduation-review workspace. */
public final class GraduationReviewOverview implements Serializable {
    private static final long serialVersionUID = 1L;

    private final StudentRecord student;
    private final CreditSummary credits;
    private final GraduationCreditRequirement requirement;
    private final AcademicAssessment latestAssessment;
    private final boolean latestAssessmentCurrent;

    public GraduationReviewOverview(StudentRecord student, CreditSummary credits,
            GraduationCreditRequirement requirement, AcademicAssessment latestAssessment,
            boolean latestAssessmentCurrent) {
        if (student == null || credits == null || requirement == null) {
            throw new IllegalArgumentException("student, credits and requirement are required");
        }
        if (!student.getStudentId().equals(credits.getStudentId())) {
            throw new IllegalArgumentException("overview student mismatch");
        }
        if (latestAssessment != null
                && !student.getStudentId().equals(latestAssessment.getStudentId())) {
            throw new IllegalArgumentException("overview assessment mismatch");
        }
        if (latestAssessment == null && latestAssessmentCurrent) {
            throw new IllegalArgumentException("missing assessment cannot be current");
        }
        this.student = student;
        this.credits = credits;
        this.requirement = requirement;
        this.latestAssessment = latestAssessment;
        this.latestAssessmentCurrent = latestAssessmentCurrent;
    }

    public StudentRecord getStudent() { return student; }
    public CreditSummary getCredits() { return credits; }
    public GraduationCreditRequirement getRequirement() { return requirement; }
    public AcademicAssessment getLatestAssessment() { return latestAssessment; }
    public boolean isLatestAssessmentCurrent() { return latestAssessmentCurrent; }
}

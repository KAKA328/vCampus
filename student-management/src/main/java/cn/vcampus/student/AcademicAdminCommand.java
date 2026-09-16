package cn.vcampus.student;

/** Common view of versioned academic-administration commands. */
public interface AcademicAdminCommand {
    void validate();
    String getToken();
    AcademicAdminCommandV1.Action getAction();
    String getStudentId();
    String getAssessmentId();
    String getNote();
    boolean isOtherRequirementsConfirmed();
}

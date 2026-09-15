package cn.vcampus.student;

import java.io.Serializable;

/** Academic administration command whose graduation credits are derived by the server. */
public final class AcademicAdminCommandV2 implements AcademicAdminCommand, Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final AcademicAdminCommandV1.Action action;
    private final String studentId;
    private final String assessmentId;
    private final String note;
    private final boolean otherRequirementsConfirmed;

    public AcademicAdminCommandV2(String token, AcademicAdminCommandV1.Action action,
            String studentId, String assessmentId, String note,
            boolean otherRequirementsConfirmed) {
        this.token = token;
        this.action = action;
        this.studentId = studentId;
        this.assessmentId = assessmentId;
        this.note = note;
        this.otherRequirementsConfirmed = otherRequirementsConfirmed;
        validate();
    }

    @Override
    public void validate() {
        require(token, "token");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (action != AcademicAdminCommandV1.Action.STUDENTS
                && action != AcademicAdminCommandV1.Action.TEACHERS) {
            require(studentId, "studentId");
        }
        if (action == AcademicAdminCommandV1.Action.GRADUATE) {
            require(assessmentId, "assessmentId");
            if (!otherRequirementsConfirmed) {
                throw new IllegalArgumentException("请确认已核查其他毕业条件");
            }
        }
        if (note != null && note.length() > 255) {
            throw new IllegalArgumentException("说明不得超过255字");
        }
    }

    private static void require(String text, String field) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
    }

    @Override public String getToken() { return token; }
    @Override public AcademicAdminCommandV1.Action getAction() { return action; }
    @Override public String getStudentId() { return studentId == null ? null : studentId.trim(); }
    @Override public String getAssessmentId() { return assessmentId; }
    @Override public String getNote() { return note == null ? "" : note.trim(); }
    @Override public boolean isOtherRequirementsConfirmed() { return otherRequirementsConfirmed; }
}

package cn.vcampus.student;

import java.io.Serializable;

/** Targets are administrative business parameters; actor identity always comes from the session. */
public final class AcademicAdminCommandV1 implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Action { STUDENTS, TEACHERS, HISTORY, CREDITS, ASSESSMENTS, REVIEW, GRADUATE }
    private final String token;
    private final Action action;
    private final String studentId;
    private final int requiredCredits;
    private final String assessmentId;
    private final String note;
    private final boolean otherRequirementsConfirmed;

    public AcademicAdminCommandV1(String token, Action action, String studentId, int requiredCredits,
            String assessmentId, String note, boolean otherRequirementsConfirmed) {
        this.token = token; this.action = action; this.studentId = studentId;
        this.requiredCredits = requiredCredits; this.assessmentId = assessmentId;
        this.note = note; this.otherRequirementsConfirmed = otherRequirementsConfirmed;
        validate();
    }
    public void validate() {
        require(token, "token");
        if (action == null) throw new IllegalArgumentException("action is required");
        if (action != Action.STUDENTS && action != Action.TEACHERS) require(studentId, "studentId");
        if (action == Action.REVIEW) {
            if (requiredCredits <= 0) throw new IllegalArgumentException("要求学分必须大于零");
            require(note, "审查依据");
        }
        if (action == Action.GRADUATE) {
            require(assessmentId, "assessmentId"); require(note, "毕业核查说明");
            if (!otherRequirementsConfirmed) throw new IllegalArgumentException("请确认已核查其他毕业条件");
        }
        if (note != null && note.length() > 255) throw new IllegalArgumentException("说明不得超过255字");
    }
    private static void require(String text, String field) {
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException(field + "不能为空");
    }
    public String getToken() { return token; }
    public Action getAction() { return action; }
    public String getStudentId() { return studentId == null ? null : studentId.trim(); }
    public int getRequiredCredits() { return requiredCredits; }
    public String getAssessmentId() { return assessmentId; }
    public String getNote() { return note == null ? null : note.trim(); }
}

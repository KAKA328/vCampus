package cn.vcampus.course;

import java.io.Serializable;

/** V2 protocol request: query a student's selection records for a term. */
public final class CourseSelectionRecordQueryCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String token;
    private final String studentId;
    private final String term;

    public CourseSelectionRecordQueryCommand(String token, String studentId, String term) {
        this.token = CourseProtocolText.requireText(token, "token");
        this.studentId = CourseProtocolText.requireText(studentId, "studentId");
        this.term = CourseProtocolText.requireText(term, "term");
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public String getTerm() { return term; }
}

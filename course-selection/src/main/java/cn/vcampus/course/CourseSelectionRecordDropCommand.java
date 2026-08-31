package cn.vcampus.course;

import java.io.Serializable;

/** V2 protocol request: drop a selected course by selection record id. */
public final class CourseSelectionRecordDropCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String token;
    private final String studentId;
    private final String recordId;

    public CourseSelectionRecordDropCommand(String token, String studentId, String recordId) {
        this.token = CourseProtocolText.requireText(token, "token");
        this.studentId = CourseProtocolText.requireText(studentId, "studentId");
        this.recordId = CourseProtocolText.requireText(recordId, "recordId");
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public String getRecordId() { return recordId; }
}

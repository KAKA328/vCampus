package cn.vcampus.course;

import java.io.Serializable;

/** V2 protocol request: select a concrete offering within a selection round. */
public final class CourseOfferingSelectionCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String token;
    private final String studentId;
    private final String roundId;
    private final String offeringId;

    public CourseOfferingSelectionCommand(String token, String studentId, String roundId, String offeringId) {
        this.token = CourseProtocolText.requireText(token, "token");
        this.studentId = CourseProtocolText.requireText(studentId, "studentId");
        this.roundId = CourseProtocolText.requireText(roundId, "roundId");
        this.offeringId = CourseProtocolText.requireText(offeringId, "offeringId");
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public String getRoundId() { return roundId; }
    public String getOfferingId() { return offeringId; }
}

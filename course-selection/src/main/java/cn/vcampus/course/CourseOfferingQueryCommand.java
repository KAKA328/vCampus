package cn.vcampus.course;

import java.io.Serializable;

/** V2 protocol request: query concrete course offerings in a selection round. */
public final class CourseOfferingQueryCommand implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String token;
    private final String roundId;
    private final String courseId;

    public CourseOfferingQueryCommand(String token, String roundId) {
        this(token, roundId, null);
    }

    public CourseOfferingQueryCommand(String token, String roundId, String courseId) {
        this.token = CourseProtocolText.requireText(token, "token");
        this.roundId = CourseProtocolText.requireText(roundId, "roundId");
        this.courseId = CourseProtocolText.optionalText(courseId);
    }

    public String getToken() { return token; }
    public String getRoundId() { return roundId; }
    public String getCourseId() { return courseId; }
}

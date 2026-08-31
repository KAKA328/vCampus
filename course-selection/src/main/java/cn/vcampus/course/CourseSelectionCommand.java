package cn.vcampus.course;

import java.io.Serializable;

/**
 * A request to select or drop a course for an authenticated student.
 *
 * <p>This object is serializable so a client can place it in a message payload
 * and send it to the server.</p>
 */
public final class CourseSelectionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String studentId;
    private final String courseId;

    public CourseSelectionCommand(String token, String studentId, String courseId) {
        this.token = requireText(token, "token");
        this.studentId = requireText(studentId, "studentId");
        this.courseId = requireText(courseId, "courseId");
    }

    public String getToken() {
        return token;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getCourseId() {
        return courseId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

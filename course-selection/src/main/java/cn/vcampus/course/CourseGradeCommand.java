package cn.vcampus.course;

import java.io.Serializable;

/** Command for teacher grade entry. */
public final class CourseGradeCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String teacherId;
    private final String studentId;
    private final String courseId;
    private final int score;

    public CourseGradeCommand(String token, String teacherId, String studentId, String courseId, int score) {
        this.token = requireText(token, "token");
        this.teacherId = requireText(teacherId, "teacherId");
        this.studentId = requireText(studentId, "studentId");
        this.courseId = requireText(courseId, "courseId");
        this.score = score;
    }

    public String getToken() { return token; }
    public String getTeacherId() { return teacherId; }
    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
    public int getScore() { return score; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

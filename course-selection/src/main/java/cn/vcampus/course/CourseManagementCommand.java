package cn.vcampus.course;

import java.io.Serializable;

/** Command for academic-admin course create/update/deactivate operations. */
public final class CourseManagementCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final Course course;
    private final String courseId;

    private CourseManagementCommand(String token, Course course, String courseId) {
        this.token = requireText(token, "token");
        this.course = course;
        this.courseId = courseId == null ? null : requireText(courseId, "courseId");
    }

    public static CourseManagementCommand forCourse(String token, Course course) {
        if (course == null) {
            throw new IllegalArgumentException("course must not be null");
        }
        return new CourseManagementCommand(token, course, null);
    }

    public static CourseManagementCommand forCourseId(String token, String courseId) {
        return new CourseManagementCommand(token, null, courseId);
    }

    public String getToken() { return token; }
    public Course getCourse() { return course; }
    public String getCourseId() { return courseId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

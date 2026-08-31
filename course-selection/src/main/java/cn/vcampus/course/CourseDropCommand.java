package cn.vcampus.course;

import java.io.Serializable;

/**
 * 早期课程级退选请求。
 *
 * <p>完整选课流程已经升级为 V2 协议，请使用 {@link CourseDropRecordV2Command}。
 */
@Deprecated
public final class CourseDropCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId;
    private final String courseId;

    public CourseDropCommand(String studentId, String courseId) {
        this.studentId = requireText(studentId, "studentId");
        this.courseId = requireText(courseId, "courseId");
    }

    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

package cn.vcampus.course;

import java.io.Serializable;

/**
 * 早期课程级查询请求。
 *
 * <p>完整选课流程已经升级为 V2 协议，请使用 {@link CourseSelectionQueryV2Command}。
 */
@Deprecated
public final class CourseQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType {
        ALL_COURSES,
        SELECTED_COURSES
    }

    private final QueryType queryType;
    private final String studentId;

    private CourseQueryCommand(QueryType queryType, String studentId) {
        this.queryType = queryType;
        this.studentId = studentId;
    }

    public static CourseQueryCommand allCourses() {
        return new CourseQueryCommand(QueryType.ALL_COURSES, null);
    }

    public static CourseQueryCommand selectedCourses(String studentId) {
        return new CourseQueryCommand(QueryType.SELECTED_COURSES,
                requireText(studentId, "studentId"));
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public String getStudentId() {
        return studentId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

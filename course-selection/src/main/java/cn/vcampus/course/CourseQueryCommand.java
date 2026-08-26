package cn.vcampus.course;

import java.io.Serializable;

/**
 * 课程查询请求，用于区分查询全部课程和查询某学生已选课程。
 */
public final class CourseQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前请求要查询的课程范围。 */
    public enum QueryType {
        ALL_COURSES,
        SELECTED_COURSES
    }

    private final QueryType queryType;
    private final String token;
    private final String studentId;

    private CourseQueryCommand(QueryType queryType, String token, String studentId) {
        this.queryType = queryType;
        this.token = token;
        this.studentId = studentId;
    }

    /** 创建查询全部课程的请求；该查询不需要携带学生身份。 */
    public static CourseQueryCommand allCourses() {
        return new CourseQueryCommand(QueryType.ALL_COURSES, null, null);
    }

    /** 创建查询某学生已选课程的请求，需要登录凭证和学生学号。 */
    public static CourseQueryCommand selectedCourses(String token, String studentId) {
        return new CourseQueryCommand(QueryType.SELECTED_COURSES,
                requireText(token, "token"), requireText(studentId, "studentId"));
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public String getToken() {
        return token;
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

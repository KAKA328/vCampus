package cn.vcampus.course;

import java.io.Serializable;

/**
 * 课程查询请求。学生身份由服务端 token 推导，客户端不再提交 studentId。
 */
public final class CourseQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 当前请求要查询的课程范围。 */
    public enum QueryType {
        AVAILABLE_ROUNDS,
        AVAILABLE_OFFERINGS,
        SELECTED_OFFERINGS
    }

    private final QueryType queryType;
    private final String token;
    private final String roundId;

    private CourseQueryCommand(QueryType queryType, String token, String roundId) {
        this.queryType = queryType;
        this.token = requireText(token, "token");
        this.roundId = roundId;
    }

    public static CourseQueryCommand availableRounds(String token) {
        return new CourseQueryCommand(QueryType.AVAILABLE_ROUNDS, token, null);
    }

    public static CourseQueryCommand availableOfferings(String token, String roundId) {
        return new CourseQueryCommand(QueryType.AVAILABLE_OFFERINGS, token,
                requireText(roundId, "roundId"));
    }

    public static CourseQueryCommand selectedOfferings(String token) {
        return new CourseQueryCommand(QueryType.SELECTED_OFFERINGS, token, null);
    }

    public QueryType getQueryType() {
        return queryType;
    }

    public String getToken() {
        return token;
    }

    public String getRoundId() {
        return roundId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

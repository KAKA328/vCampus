package cn.vcampus.course;

import java.io.Serializable;

/**
 * 完整选课流程 V2 查询请求。
 *
 * <p>学生身份由服务端根据 token 推导，客户端只提交选课轮次等业务参数。
 */
public final class CourseSelectionQueryV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType {
        AVAILABLE_ROUNDS,
        AVAILABLE_OFFERINGS,
        SELECTED_OFFERINGS
    }

    private final QueryType queryType;
    private final String token;
    private final String roundId;

    private CourseSelectionQueryV2Command(QueryType queryType, String token, String roundId) {
        this.queryType = queryType;
        this.token = requireText(token, "token");
        this.roundId = roundId;
    }

    public static CourseSelectionQueryV2Command availableRounds(String token) {
        return new CourseSelectionQueryV2Command(QueryType.AVAILABLE_ROUNDS, token, null);
    }

    public static CourseSelectionQueryV2Command availableOfferings(String token, String roundId) {
        return new CourseSelectionQueryV2Command(QueryType.AVAILABLE_OFFERINGS, token,
                requireText(roundId, "roundId"));
    }

    public static CourseSelectionQueryV2Command selectedOfferings(String token) {
        return new CourseSelectionQueryV2Command(QueryType.SELECTED_OFFERINGS, token, null);
    }

    public QueryType getQueryType() { return queryType; }
    public String getToken() { return token; }
    public String getRoundId() { return roundId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

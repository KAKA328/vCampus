package cn.vcampus.course;

import java.io.Serializable;

/**
 * 教师端教学班与名单查询命令。
 *
 * <p>命令不含 teacherId 或 studentId；服务器必须由 token 对应的登录账号查询教师档案，
 * 再据此限制可访问的教学班。</p>
 */
public final class CourseTeachingQueryV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType {
        MY_OFFERINGS,
        OFFERING_ROSTER
    }

    private final String token;
    private final QueryType queryType;
    private final String term;
    private final String offeringId;

    private CourseTeachingQueryV2Command(String token, QueryType queryType, String term,
            String offeringId) {
        this.token = requireText(token, "token");
        if (queryType == null) throw new IllegalArgumentException("queryType must not be null");
        this.queryType = queryType;
        this.term = normalize(term);
        this.offeringId = normalize(offeringId);
        if (queryType == QueryType.MY_OFFERINGS && this.term == null) {
            throw new IllegalArgumentException("term must not be blank");
        }
        if (queryType == QueryType.OFFERING_ROSTER && this.offeringId == null) {
            throw new IllegalArgumentException("offeringId must not be blank");
        }
    }

    public static CourseTeachingQueryV2Command myOfferings(String token, String term) {
        return new CourseTeachingQueryV2Command(token, QueryType.MY_OFFERINGS, term, null);
    }

    public static CourseTeachingQueryV2Command offeringRoster(String token, String offeringId) {
        return new CourseTeachingQueryV2Command(token, QueryType.OFFERING_ROSTER, null, offeringId);
    }

    public String getToken() { return token; }
    public QueryType getQueryType() { return queryType; }
    public String getTerm() { return term; }
    public String getOfferingId() { return offeringId; }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

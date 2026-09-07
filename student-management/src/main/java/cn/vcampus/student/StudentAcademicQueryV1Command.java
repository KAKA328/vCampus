package cn.vcampus.student;

import java.io.Serializable;

/** Read-only queries for the student bound to the authenticated session. */
public final class StudentAcademicQueryV1Command implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType { HISTORY, PENDING_RETAKES, CREDITS }

    private final String token;
    private final QueryType queryType;

    public StudentAcademicQueryV1Command(String token, QueryType queryType) {
        if (token == null || token.trim().isEmpty() || queryType == null) {
            throw new IllegalArgumentException("token and queryType are required");
        }
        this.token = token.trim();
        this.queryType = queryType;
    }

    public String getToken() { return token; }
    public QueryType getQueryType() { return queryType; }
}

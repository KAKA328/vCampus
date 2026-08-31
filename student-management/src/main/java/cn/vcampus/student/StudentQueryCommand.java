package cn.vcampus.student;

import java.io.Serializable;

/** Serializable query command for student records. */
public final class StudentQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType { BY_ID, BY_CLASS }

    private final QueryType queryType;
    private final String token;
    private final String value;

    private StudentQueryCommand(QueryType queryType, String token, String value) {
        this.queryType = queryType;
        this.token = requireText(token, "token");
        this.value = requireText(value, queryType == QueryType.BY_ID ? "studentId" : "classId");
    }

    public static StudentQueryCommand byId(String token, String studentId) {
        return new StudentQueryCommand(QueryType.BY_ID, token, studentId);
    }

    public static StudentQueryCommand byClass(String token, String classId) {
        return new StudentQueryCommand(QueryType.BY_CLASS, token, classId);
    }

    public QueryType getQueryType() { return queryType; }
    public String getToken() { return token; }
    public String getValue() { return value; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

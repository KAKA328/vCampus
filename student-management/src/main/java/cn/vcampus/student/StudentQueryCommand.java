package cn.vcampus.student;

import java.io.Serializable;

/** Serializable query command for student records. */
public final class StudentQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType { SELF, BY_ID, BY_CLASS, BY_MAJOR }

    private final QueryType queryType;
    private final String token;
    private final String value;

    private StudentQueryCommand(QueryType queryType, String token, String value) {
        this.queryType = queryType;
        this.token = requireText(token, "token");
        this.value = queryType == QueryType.SELF ? null : requireText(value, fieldName(queryType));
    }

    /** Creates a query resolved from the current session's userId on the server. */
    public static StudentQueryCommand self(String token) {
        return new StudentQueryCommand(QueryType.SELF, token, null);
    }

    public static StudentQueryCommand byId(String token, String studentId) {
        return new StudentQueryCommand(QueryType.BY_ID, token, studentId);
    }

    public static StudentQueryCommand byClass(String token, String classId) {
        return new StudentQueryCommand(QueryType.BY_CLASS, token, classId);
    }

    public static StudentQueryCommand byMajor(String token, String majorName) {
        return new StudentQueryCommand(QueryType.BY_MAJOR, token, majorName);
    }

    public QueryType getQueryType() { return queryType; }
    public String getToken() { return token; }
    public String getValue() { return value; }

    private static String fieldName(QueryType type) {
        if (type == QueryType.BY_ID) return "studentId";
        if (type == QueryType.BY_MAJOR) return "majorName";
        return "classId";
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

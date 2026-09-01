package cn.vcampus.student;

import java.io.Serializable;

/** Student record query command; identity is authorized by server-side token. */
public final class StudentQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum QueryType {
        SELF,
        BY_ID,
        BY_CLASS
    }

    private final QueryType queryType;
    private final String token;
    private final String studentId;
    private final String classId;

    private StudentQueryCommand(QueryType queryType, String token, String studentId, String classId) {
        this.queryType = queryType;
        this.token = requireText(token, "token");
        this.studentId = studentId;
        this.classId = classId;
    }

    public static StudentQueryCommand self(String token) {
        return new StudentQueryCommand(QueryType.SELF, token, null, null);
    }

    public static StudentQueryCommand byId(String token, String studentId) {
        return new StudentQueryCommand(QueryType.BY_ID, token, requireText(studentId, "studentId"), null);
    }

    public static StudentQueryCommand byClass(String token, String classId) {
        return new StudentQueryCommand(QueryType.BY_CLASS, token, null, requireText(classId, "classId"));
    }

    public QueryType getQueryType() { return queryType; }
    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public String getClassId() { return classId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

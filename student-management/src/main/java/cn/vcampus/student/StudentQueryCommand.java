package cn.vcampus.student;

import java.io.Serializable;

/** Command for authenticated student record query. */
public final class StudentQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String studentId;
    private final String classId;

    private StudentQueryCommand(String token, String studentId, String classId) {
        this.token = requireText(token, "token");
        this.studentId = studentId == null ? null : requireText(studentId, "studentId");
        this.classId = classId == null ? null : requireText(classId, "classId");
    }

    public static StudentQueryCommand byId(String token, String studentId) {
        return new StudentQueryCommand(token, studentId, null);
    }

    public static StudentQueryCommand byClass(String token, String classId) {
        return new StudentQueryCommand(token, null, classId);
    }

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

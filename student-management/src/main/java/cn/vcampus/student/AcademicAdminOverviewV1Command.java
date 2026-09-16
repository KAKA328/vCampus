package cn.vcampus.student;

import java.io.Serializable;

/** Read-only request for one student's graduation-review workbench snapshot. */
public final class AcademicAdminOverviewV1Command implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String studentId;

    public AcademicAdminOverviewV1Command(String token, String studentId) {
        this.token = requireText(token, "token");
        this.studentId = requireText(studentId, "studentId");
    }

    public void validate() {
        requireText(token, "token");
        requireText(studentId, "studentId");
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}

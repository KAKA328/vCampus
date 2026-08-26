package cn.vcampus.student;

import java.io.Serializable;

/** Command for authenticated academic review query. */
public final class StudentReviewCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String studentId;
    private final int requiredCredits;

    public StudentReviewCommand(String token, String studentId, int requiredCredits) {
        this.token = requireText(token, "token");
        this.studentId = requireText(studentId, "studentId");
        if (requiredCredits < 0) {
            throw new IllegalArgumentException("requiredCredits cannot be negative");
        }
        this.requiredCredits = requiredCredits;
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public int getRequiredCredits() { return requiredCredits; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

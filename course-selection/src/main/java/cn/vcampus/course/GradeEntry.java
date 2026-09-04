package cn.vcampus.course;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 成绩提交单中一名有效选课学生的成绩草稿。 */
public final class GradeEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String submissionId;
    private final String studentId;
    private final SelectionType selectionType;
    private final int score;
    private final LocalDateTime updatedAt;

    public GradeEntry(String submissionId, String studentId, SelectionType selectionType, int score,
            LocalDateTime updatedAt) {
        this.submissionId = requireText(submissionId, "submissionId");
        this.studentId = requireText(studentId, "studentId");
        if (selectionType == null || updatedAt == null) {
            throw new IllegalArgumentException("selectionType and updatedAt must not be null");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        this.selectionType = selectionType;
        this.score = score;
        this.updatedAt = updatedAt;
    }

    public String getSubmissionId() { return submissionId; }
    public String getStudentId() { return studentId; }
    public SelectionType getSelectionType() { return selectionType; }
    public int getScore() { return score; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

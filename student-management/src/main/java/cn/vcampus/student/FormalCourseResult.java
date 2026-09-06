package cn.vcampus.student;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 教务审核通过后写入学生正式学业成绩的一次课程尝试记录。 */
public final class FormalCourseResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String resultId;
    private final String studentId;
    private final String courseId;
    private final String offeringId;
    private final String semester;
    private final int attemptNo;
    private final String attemptType;
    private final int score;
    private final boolean passed;
    private final int earnedCredits;
    private final LocalDateTime recordedAt;

    public FormalCourseResult(String resultId, String studentId, String courseId, String offeringId,
            String semester, int attemptNo, String attemptType, int score, boolean passed,
            int earnedCredits, LocalDateTime recordedAt) {
        this.resultId = requireText(resultId, "resultId");
        this.studentId = requireText(studentId, "studentId");
        this.courseId = requireText(courseId, "courseId");
        this.offeringId = normalize(offeringId);
        this.semester = requireText(semester, "semester");
        if (attemptNo < 1) throw new IllegalArgumentException("attemptNo must be positive");
        this.attemptType = requireText(attemptType, "attemptType");
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (earnedCredits < 0 || recordedAt == null) {
            throw new IllegalArgumentException("earnedCredits and recordedAt are invalid");
        }
        this.attemptNo = attemptNo;
        this.score = score;
        this.passed = passed;
        this.earnedCredits = earnedCredits;
        this.recordedAt = recordedAt;
    }

    public String getResultId() { return resultId; }
    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
    public String getOfferingId() { return offeringId; }
    public String getSemester() { return semester; }
    public int getAttemptNo() { return attemptNo; }
    public String getAttemptType() { return attemptType; }
    public int getScore() { return score; }
    public boolean isPassed() { return passed; }
    public int getEarnedCredits() { return earnedCredits; }
    public LocalDateTime getRecordedAt() { return recordedAt; }

    public CourseHistoryRecord toHistoryRecord(String courseName) {
        return new CourseHistoryRecord(studentId, courseId, courseName, semester, attemptNo,
                attemptType, score, passed, earnedCredits);
    }

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

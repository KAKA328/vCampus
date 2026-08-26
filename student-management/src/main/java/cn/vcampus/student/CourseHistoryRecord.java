package cn.vcampus.student;

import java.io.Serializable;

/** One historical course attempt for academic review. */
public final class CourseHistoryRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String courseId;
    private final String courseName;
    private final String semester;
    private final int attemptNo;
    private final String attemptType;
    private final int score;
    private final boolean passed;
    private final int earnedCredits;

    public CourseHistoryRecord(
            String studentId,
            String courseId,
            String courseName,
            String semester,
            int attemptNo,
            String attemptType,
            int score,
            boolean passed,
            int earnedCredits
    ) {
        if (attemptNo < 1) {
            throw new IllegalArgumentException("attemptNo must be positive");
        }
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        if (earnedCredits < 0) {
            throw new IllegalArgumentException("earnedCredits cannot be negative");
        }
        this.studentId = studentId;
        this.courseId = courseId;
        this.courseName = courseName;
        this.semester = semester;
        this.attemptNo = attemptNo;
        this.attemptType = attemptType;
        this.score = score;
        this.passed = passed;
        this.earnedCredits = earnedCredits;
    }

    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
    public String getCourseName() { return courseName; }
    public String getSemester() { return semester; }
    public int getAttemptNo() { return attemptNo; }
    public String getAttemptType() { return attemptType; }
    public int getScore() { return score; }
    public boolean isPassed() { return passed; }
    public int getEarnedCredits() { return earnedCredits; }
}

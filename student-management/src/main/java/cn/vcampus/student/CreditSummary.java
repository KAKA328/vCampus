package cn.vcampus.student;

import java.io.Serializable;
import java.util.*;

/** Current progress only. It does not declare graduation eligibility. */
public final class CreditSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId;
    private final int earnedCredits;
    private final int passedCourses;
    private final int pendingRetakes;
    private final int historicalRetakes;

    public CreditSummary(String studentId, int earnedCredits, int passedCourses,
            int pendingRetakes, int historicalRetakes) {
        this.studentId = studentId;
        this.earnedCredits = earnedCredits;
        this.passedCourses = passedCourses;
        this.pendingRetakes = pendingRetakes;
        this.historicalRetakes = historicalRetakes;
    }

    public static CreditSummary from(String studentId, List<CourseHistoryRecord> records) {
        Map<String, Integer> credits = new HashMap<String, Integer>();
        Set<String> all = new HashSet<String>();
        Set<String> retakes = new HashSet<String>();
        for (CourseHistoryRecord record : records) {
            if (!studentId.equals(record.getStudentId())) throw new IllegalArgumentException("history owner mismatch");
            all.add(record.getCourseId());
            if (record.isPassed()) {
                Integer previous = credits.get(record.getCourseId());
                credits.put(record.getCourseId(), Math.max(previous == null ? 0 : previous,
                        record.getEarnedCredits()));
            }
            if (record.getAttemptNo() > 1 || "重修".equals(record.getAttemptType())) retakes.add(record.getCourseId());
        }
        int total = 0;
        for (Integer value : credits.values()) total = Math.addExact(total, value);
        return new CreditSummary(studentId, total, credits.size(), all.size() - credits.size(), retakes.size());
    }

    public String getStudentId() { return studentId; }
    public int getEarnedCredits() { return earnedCredits; }
    public int getPassedCourses() { return passedCourses; }
    public int getPendingRetakes() { return pendingRetakes; }
    public int getHistoricalRetakes() { return historicalRetakes; }
}

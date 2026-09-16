package cn.vcampus.student;

import cn.vcampus.common.CreditFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/** Current progress only. It does not declare graduation eligibility. */
public final class CreditSummary implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId;
    private final BigDecimal earnedCredits;
    private final int passedCourses;
    private final int pendingRetakes;
    private final int historicalRetakes;

    public CreditSummary(String studentId, int earnedCredits, int passedCourses,
            int pendingRetakes, int historicalRetakes) {
        this(studentId, BigDecimal.valueOf(earnedCredits), passedCourses, pendingRetakes,
                historicalRetakes);
    }
    public CreditSummary(String studentId, BigDecimal earnedCredits, int passedCourses,
            int pendingRetakes, int historicalRetakes) {
        this.studentId = studentId;
        this.earnedCredits = CreditFormat.nonNegative(earnedCredits, "earnedCredits");
        this.passedCourses = passedCourses;
        this.pendingRetakes = pendingRetakes;
        this.historicalRetakes = historicalRetakes;
    }

    public static CreditSummary from(String studentId, List<CourseHistoryRecord> records) {
        Map<String, BigDecimal> credits = new HashMap<String, BigDecimal>();
        Set<String> all = new HashSet<String>();
        Set<String> retakes = new HashSet<String>();
        for (CourseHistoryRecord record : records) {
            if (!studentId.equals(record.getStudentId())) throw new IllegalArgumentException("history owner mismatch");
            all.add(record.getCourseId());
            if (record.isPassed()) {
                BigDecimal previous = credits.get(record.getCourseId());
                if (previous == null || record.getEarnedCredits().compareTo(previous) > 0) {
                    credits.put(record.getCourseId(), record.getEarnedCredits());
                }
            }
            if (record.getAttemptNo() > 1 || "重修".equals(record.getAttemptType())) retakes.add(record.getCourseId());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : credits.values()) total = total.add(value);
        return new CreditSummary(studentId, total, credits.size(), all.size() - credits.size(), retakes.size());
    }

    public String getStudentId() { return studentId; }
    public BigDecimal getEarnedCredits() { return earnedCredits; }
    public int getPassedCourses() { return passedCourses; }
    public int getPendingRetakes() { return pendingRetakes; }
    public int getHistoricalRetakes() { return historicalRetakes; }
}

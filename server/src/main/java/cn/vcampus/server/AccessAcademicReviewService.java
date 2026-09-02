package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.AcademicReview;
import cn.vcampus.student.CourseHistoryRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Access-backed academic history reader shared with course selection. */
public final class AccessAcademicReviewService implements AcademicReviewService {
    private final Path databasePath;

    public AccessAcademicReviewService(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<List<CourseHistoryRecord>> historyFor(String studentId) {
        String normalized = normalize(studentId);
        if (normalized == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        String sql = "SELECT r.student_id,r.course_id,c.course_name,r.semester,r.attempt_no,"
                + "r.attempt_type,r.score,r.passed,r.earned_credits "
                + "FROM tblCourseResult AS r LEFT JOIN tblCourse AS c ON r.course_id=c.course_id "
                + "WHERE r.student_id=? ORDER BY r.semester,r.course_id,r.attempt_no";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet results = statement.executeQuery()) {
                return ServiceResult.ok(readHistory(results));
            }
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read academic history");
        }
    }

    @Override
    public ServiceResult<List<CourseHistoryRecord>> pendingRetakes(String studentId) {
        ServiceResult<List<CourseHistoryRecord>> history = historyFor(studentId);
        if (history.getStatus() != StatusCode.OK) {
            return history;
        }
        Map<String, CourseSummary> summaries = new LinkedHashMap<String, CourseSummary>();
        for (CourseHistoryRecord record : history.getData()) {
            CourseSummary summary = summaries.get(record.getCourseId());
            if (summary == null) {
                summary = new CourseSummary();
                summaries.put(record.getCourseId(), summary);
            }
            summary.passed = summary.passed || record.isPassed();
            if (!record.isPassed() && (summary.latestFailed == null
                    || record.getAttemptNo() > summary.latestFailed.getAttemptNo()
                    || (record.getAttemptNo() == summary.latestFailed.getAttemptNo()
                    && record.getSemester().compareTo(summary.latestFailed.getSemester()) > 0))) {
                summary.latestFailed = record;
            }
        }
        List<CourseHistoryRecord> pending = new ArrayList<CourseHistoryRecord>();
        for (CourseSummary summary : summaries.values()) {
            if (!summary.passed && summary.latestFailed != null) {
                pending.add(summary.latestFailed);
            }
        }
        Collections.sort(pending, (left, right) -> left.getCourseId().compareTo(right.getCourseId()));
        return ServiceResult.ok(pending);
    }

    @Override
    public ServiceResult<AcademicReview> review(String studentId, int requiredCredits) {
        if (requiredCredits < 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "requiredCredits cannot be negative");
        }
        ServiceResult<List<CourseHistoryRecord>> history = historyFor(studentId);
        if (history.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(history.getStatus(), history.getMessage());
        }
        Map<String, CourseSummary> summaries = new LinkedHashMap<String, CourseSummary>();
        for (CourseHistoryRecord record : history.getData()) {
            CourseSummary summary = summaries.get(record.getCourseId());
            if (summary == null) {
                summary = new CourseSummary();
                summaries.put(record.getCourseId(), summary);
            }
            summary.seen = true;
            summary.passed = summary.passed || record.isPassed();
            summary.maxEarnedCredits = Math.max(summary.maxEarnedCredits, record.getEarnedCredits());
            summary.retake = summary.retake || record.getAttemptNo() > 1 || "重修".equals(record.getAttemptType());
        }
        int totalCredits = 0;
        int passedCourses = 0;
        int failedCourses = 0;
        int retakeCourses = 0;
        for (CourseSummary summary : summaries.values()) {
            if (summary.passed) {
                passedCourses++;
                totalCredits += summary.maxEarnedCredits;
            } else if (summary.seen) {
                failedCourses++;
            }
            if (summary.retake) retakeCourses++;
        }
        boolean ready = totalCredits >= requiredCredits && failedCourses == 0;
        String remark = history.getData().isEmpty() ? "暂无课程成绩记录"
                : (ready ? "达到阶段学分要求" : "未达到阶段学分要求");
        return ServiceResult.ok(new AcademicReview(null, history.getData().isEmpty()
                ? normalize(studentId) : history.getData().get(0).getStudentId(), totalCredits,
                requiredCredits, passedCourses, failedCourses, retakeCourses, ready,
                null, null, remark));
    }

    @Override
    public ServiceResult<AcademicReview> latestReview(String studentId) {
        String normalized = normalize(studentId);
        if (normalized == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        String sql = "SELECT review_id,student_id,total_earned_credits,required_earned_credits,"
                + "failed_course_count,retake_course_count,graduation_ready,reviewed_by,reviewed_at,remark "
                + "FROM tblAcademicReview WHERE student_id=? ORDER BY reviewed_at DESC,review_id DESC";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "academic review not found");
                }
                Timestamp reviewedAt = results.getTimestamp("reviewed_at");
                return ServiceResult.ok(new AcademicReview(
                        results.getString("review_id"),
                        results.getString("student_id"),
                        results.getInt("total_earned_credits"),
                        results.getInt("required_earned_credits"),
                        passedCourseCount(connection, normalized),
                        results.getInt("failed_course_count"),
                        results.getInt("retake_course_count"),
                        results.getBoolean("graduation_ready"),
                        results.getString("reviewed_by"),
                        reviewedAt == null ? null : reviewedAt.toInstant(),
                        results.getString("remark")));
            }
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read academic review");
        }
    }

    private static int passedCourseCount(Connection connection, String studentId) throws SQLException {
        String sql = "SELECT course_id FROM tblCourseResult WHERE student_id=? AND passed=true GROUP BY course_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                int count = 0;
                while (results.next()) {
                    count++;
                }
                return count;
            }
        }
    }

    private static List<CourseHistoryRecord> readHistory(ResultSet results) throws SQLException {
        List<CourseHistoryRecord> history = new ArrayList<CourseHistoryRecord>();
        while (results.next()) {
            int score = results.getInt("score");
            if (results.wasNull()) {
                score = 0;
            }
            history.add(new CourseHistoryRecord(
                    results.getString("student_id"),
                    results.getString("course_id"),
                    results.getString("course_name"),
                    results.getString("semester"),
                    results.getInt("attempt_no"),
                    results.getString("attempt_type"),
                    score,
                    results.getBoolean("passed"),
                    results.getInt("earned_credits")));
        }
        return history;
    }

    private Connection open() throws SQLException {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException failure) {
            throw new SQLException("UCanAccess driver is unavailable", failure);
        }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final class CourseSummary {
        private boolean seen;
        private boolean passed;
        private boolean retake;
        private int maxEarnedCredits;
        private CourseHistoryRecord latestFailed;
    }
}

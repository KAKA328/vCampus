package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.CourseHistoryRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        private boolean passed;
        private CourseHistoryRecord latestFailed;
    }
}

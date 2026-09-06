package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.student.FormalCourseResult;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/** Access 模式下将正式成绩写入和成绩单审核通过放在同一数据库事务中。 */
final class AccessGradeApprovalWorkflow implements GradeApprovalWorkflow {
    private final Path databasePath;

    AccessGradeApprovalWorkflow(Path databasePath) {
        if (databasePath == null) throw new IllegalArgumentException("databasePath must not be null");
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<GradeSubmission> approve(String submissionId,
            List<FormalCourseResult> results, String reviewerId, String remark) {
        String normalizedSubmissionId = normalize(submissionId);
        String normalizedReviewerId = normalize(reviewerId);
        if (normalizedSubmissionId == null || normalizedReviewerId == null
                || results == null || results.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "submissionId, reviewerId and results must not be empty");
        }
        for (FormalCourseResult result : results) {
            if (result == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "formal course result must not be null");
        }
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                GradeSubmission submission = findSubmission(connection, normalizedSubmissionId);
                if (submission == null) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "grade submission not found");
                }
                if (submission.getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "only pending grade submissions can be reviewed");
                }
                LocalDateTime now = LocalDateTime.now();
                int changed = markApproved(connection, normalizedSubmissionId, normalizedReviewerId,
                        normalize(remark), now);
                if (changed != 1) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "grade submission was changed by another reviewer");
                }
                // 先用状态条件更新抢占本次审核权；若写入正式成绩失败，整个事务会回滚，
                // 成绩单仍保持待审核，其他审核人也不会看到半成品状态。
                insertFormalResults(connection, results);
                connection.commit();
                return ServiceResult.ok(submission.reviewed(GradeSubmissionStatus.APPROVED,
                        normalizedReviewerId, remark, now));
            } catch (SQLException failure) {
                rollback(connection);
                return ServiceResult.failure(isConflict(failure) ? StatusCode.CONFLICT
                        : StatusCode.SERVER_ERROR, isConflict(failure)
                        ? "grade approval conflicts with existing state"
                        : "failed to approve grade submission");
            }
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to approve grade submission");
        }
    }

    private static GradeSubmission findSubmission(Connection connection, String submissionId)
            throws SQLException {
        String sql = "SELECT submission_id,offering_id,teacher_id,status,created_at,updated_at,"
                + "reviewed_by,reviewed_at,review_remark FROM tblGradeSubmission WHERE submission_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submissionId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) return null;
                Timestamp reviewedAt = results.getTimestamp("reviewed_at");
                return new GradeSubmission(results.getString("submission_id"),
                        results.getString("offering_id"), results.getString("teacher_id"),
                        GradeSubmissionStatus.valueOf(results.getString("status")),
                        results.getTimestamp("created_at").toLocalDateTime(),
                        results.getTimestamp("updated_at").toLocalDateTime(),
                        results.getString("reviewed_by"),
                        reviewedAt == null ? null : reviewedAt.toLocalDateTime(),
                        results.getString("review_remark"));
            }
        }
    }

    private static void insertFormalResults(Connection connection, List<FormalCourseResult> results)
            throws SQLException {
        String sql = "INSERT INTO tblCourseResult(result_id,student_id,course_id,offering_id,semester,"
                + "attempt_no,attempt_type,score,passed,earned_credits,recorded_at) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (FormalCourseResult result : results) {
                statement.setString(1, result.getResultId());
                statement.setString(2, result.getStudentId());
                statement.setString(3, result.getCourseId());
                statement.setString(4, result.getOfferingId());
                statement.setString(5, result.getSemester());
                statement.setInt(6, result.getAttemptNo());
                statement.setString(7, result.getAttemptType());
                statement.setInt(8, result.getScore());
                statement.setBoolean(9, result.isPassed());
                statement.setInt(10, result.getEarnedCredits());
                statement.setTimestamp(11, Timestamp.valueOf(result.getRecordedAt()));
                statement.executeUpdate();
            }
        }
    }

    private static int markApproved(Connection connection, String submissionId, String reviewerId,
            String remark, LocalDateTime now) throws SQLException {
        String sql = "UPDATE tblGradeSubmission SET status=?,updated_at=?,reviewed_by=?,"
                + "reviewed_at=?,review_remark=? WHERE submission_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, GradeSubmissionStatus.APPROVED.name());
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setString(3, reviewerId);
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setString(5, remark);
            statement.setString(6, submissionId);
            statement.setString(7, GradeSubmissionStatus.PENDING_REVIEW.name());
            return statement.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static boolean isConflict(SQLException failure) {
        String message = failure.getMessage();
        if (message == null) return false;
        String normalized = message.toLowerCase();
        return normalized.contains("unique") || normalized.contains("duplicate")
                || normalized.contains("lock") || normalized.contains("concurrent");
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

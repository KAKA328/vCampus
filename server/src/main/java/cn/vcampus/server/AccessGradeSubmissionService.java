package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.GradeReviewDecision;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 使用 Access 保存教学班成绩草稿；本类不负责审核或写入正式课程结果。 */
public final class AccessGradeSubmissionService implements GradeSubmissionService {
    private final Path databasePath;

    public AccessGradeSubmissionService(Path databasePath) {
        if (databasePath == null) throw new IllegalArgumentException("databasePath must not be null");
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<GradeSubmission> createDraft(GradeSubmission submission) {
        if (submission == null || submission.getStatus() != GradeSubmissionStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "only a draft grade submission can be created");
        }
        String sql = "INSERT INTO tblGradeSubmission(submission_id,offering_id,teacher_id,status,"
                + "created_at,updated_at,reviewed_by,reviewed_at,review_remark) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submission.getSubmissionId());
            statement.setString(2, submission.getOfferingId());
            statement.setString(3, submission.getTeacherId());
            statement.setString(4, submission.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(submission.getCreatedAt()));
            statement.setTimestamp(6, Timestamp.valueOf(submission.getUpdatedAt()));
            statement.setString(7, submission.getReviewedBy());
            statement.setTimestamp(8, timestamp(submission.getReviewedAt()));
            statement.setString(9, submission.getReviewRemark());
            statement.executeUpdate();
            return ServiceResult.ok(submission);
        } catch (SQLException failure) {
            return isDuplicate(failure) ? ServiceResult.<GradeSubmission>failure(StatusCode.CONFLICT,
                    "a grade submission already exists for this offering") : databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<GradeSubmission> findById(String submissionId) {
        String normalized = normalize(submissionId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "submissionId must not be blank");
        String sql = selectSubmissions() + " WHERE submission_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            return readOneSubmission(statement);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<GradeSubmission> findByOffering(String offeringId) {
        String normalized = normalize(offeringId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "offeringId must not be blank");
        String sql = selectSubmissions() + " WHERE offering_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            return readOneSubmission(statement);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<GradeSubmission>> listByStatus(GradeSubmissionStatus status) {
        if (status == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "status must not be null");
        String sql = selectSubmissions() + " WHERE status=? ORDER BY updated_at,submission_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet results = statement.executeQuery()) {
                List<GradeSubmission> submissions = new ArrayList<GradeSubmission>();
                while (results.next()) submissions.add(readSubmission(results));
                return ServiceResult.ok(Collections.unmodifiableList(submissions));
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<GradeEntry>> listEntries(String submissionId) {
        String normalized = normalize(submissionId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "submissionId must not be blank");
        String sql = "SELECT submission_id,student_id,selection_type,score,updated_at "
                + "FROM tblGradeEntry WHERE submission_id=? ORDER BY student_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            try (ResultSet results = statement.executeQuery()) {
                List<GradeEntry> entries = new ArrayList<GradeEntry>();
                while (results.next()) entries.add(readEntry(results));
                return ServiceResult.ok(Collections.unmodifiableList(entries));
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<GradeEntry> saveDraftEntry(GradeEntry entry) {
        if (entry == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "grade entry must not be null");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                GradeSubmission submission = findSubmission(connection, entry.getSubmissionId());
                if (submission == null) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "grade submission not found");
                }
                if (submission.getStatus() != GradeSubmissionStatus.DRAFT
                        && submission.getStatus() != GradeSubmissionStatus.RETURNED
                        && submission.getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "approved grade entries cannot be changed until they are returned");
                }
                if (!updateEntry(connection, entry)) insertEntry(connection, entry);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tblGradeSubmission SET updated_at=? WHERE submission_id=?")) {
                    statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    statement.setString(2, entry.getSubmissionId());
                    statement.executeUpdate();
                }
                connection.commit();
                return ServiceResult.ok(entry);
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<GradeSubmission> submitForReview(String submissionId) {
        String normalized = normalize(submissionId);
        if (normalized == null) return ServiceResult.failure(StatusCode.BAD_REQUEST,
                "submissionId must not be blank");
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                GradeSubmission submission = findSubmission(connection, normalized);
                if (submission == null) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "grade submission not found");
                }
                if (submission.getStatus() == GradeSubmissionStatus.APPROVED) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "approved grade submission must be returned before it can be changed");
                }
                LocalDateTime now = LocalDateTime.now();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tblGradeSubmission SET status=?,updated_at=? WHERE submission_id=?")) {
                    statement.setString(1, GradeSubmissionStatus.PENDING_REVIEW.name());
                    statement.setTimestamp(2, Timestamp.valueOf(now));
                    statement.setString(3, normalized);
                    statement.executeUpdate();
                }
                connection.commit();
                return ServiceResult.ok(submission.pendingReview(now));
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<GradeSubmission> review(String submissionId, GradeReviewDecision decision,
            String reviewerId, String remark) {
        String normalizedSubmissionId = normalize(submissionId);
        String normalizedReviewerId = normalize(reviewerId);
        if (normalizedSubmissionId == null || normalizedReviewerId == null || decision == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "submissionId, decision and reviewerId must not be blank");
        }
        if (decision == GradeReviewDecision.RETURN && normalize(remark) == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "a return remark is required");
        }
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                GradeSubmission submission = findSubmission(connection, normalizedSubmissionId);
                if (submission == null) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "grade submission not found");
                }
                if (submission.getStatus() != GradeSubmissionStatus.PENDING_REVIEW) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "only pending grade submissions can be reviewed");
                }
                GradeSubmissionStatus status = decision == GradeReviewDecision.APPROVE
                        ? GradeSubmissionStatus.APPROVED : GradeSubmissionStatus.RETURNED;
                LocalDateTime now = LocalDateTime.now();
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tblGradeSubmission SET status=?,updated_at=?,reviewed_by=?,"
                                + "reviewed_at=?,review_remark=? WHERE submission_id=?")) {
                    statement.setString(1, status.name());
                    statement.setTimestamp(2, Timestamp.valueOf(now));
                    statement.setString(3, normalizedReviewerId);
                    statement.setTimestamp(4, Timestamp.valueOf(now));
                    statement.setString(5, normalize(remark));
                    statement.setString(6, normalizedSubmissionId);
                    statement.executeUpdate();
                }
                connection.commit();
                return ServiceResult.ok(submission.reviewed(status, normalizedReviewerId, remark, now));
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private static String selectSubmissions() {
        return "SELECT submission_id,offering_id,teacher_id,status,created_at,updated_at,"
                + "reviewed_by,reviewed_at,review_remark "
                + "FROM tblGradeSubmission";
    }

    private static ServiceResult<GradeSubmission> readOneSubmission(PreparedStatement statement)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            return results.next() ? ServiceResult.ok(readSubmission(results))
                    : ServiceResult.<GradeSubmission>failure(StatusCode.NOT_FOUND,
                            "grade submission not found");
        }
    }

    private static GradeSubmission findSubmission(Connection connection, String submissionId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                selectSubmissions() + " WHERE submission_id=?")) {
            statement.setString(1, submissionId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readSubmission(results) : null;
            }
        }
    }

    private static GradeSubmission readSubmission(ResultSet results) throws SQLException {
        return new GradeSubmission(results.getString("submission_id"), results.getString("offering_id"),
                results.getString("teacher_id"), GradeSubmissionStatus.valueOf(results.getString("status")),
                results.getTimestamp("created_at").toLocalDateTime(),
                results.getTimestamp("updated_at").toLocalDateTime(),
                results.getString("reviewed_by"), localDateTime(results.getTimestamp("reviewed_at")),
                results.getString("review_remark"));
    }

    private static GradeEntry readEntry(ResultSet results) throws SQLException {
        return new GradeEntry(results.getString("submission_id"), results.getString("student_id"),
                SelectionType.valueOf(results.getString("selection_type")), results.getInt("score"),
                results.getTimestamp("updated_at").toLocalDateTime());
    }

    private static boolean updateEntry(Connection connection, GradeEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblGradeEntry SET selection_type=?,score=?,updated_at=? "
                        + "WHERE submission_id=? AND student_id=?")) {
            statement.setString(1, entry.getSelectionType().name());
            statement.setInt(2, entry.getScore());
            statement.setTimestamp(3, Timestamp.valueOf(entry.getUpdatedAt()));
            statement.setString(4, entry.getSubmissionId());
            statement.setString(5, entry.getStudentId());
            return statement.executeUpdate() == 1;
        }
    }

    private static void insertEntry(Connection connection, GradeEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblGradeEntry(submission_id,student_id,selection_type,score,updated_at) "
                        + "VALUES(?,?,?,?,?)")) {
            statement.setString(1, entry.getSubmissionId());
            statement.setString(2, entry.getStudentId());
            statement.setString(3, entry.getSelectionType().name());
            statement.setInt(4, entry.getScore());
            statement.setTimestamp(5, Timestamp.valueOf(entry.getUpdatedAt()));
            statement.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static boolean isDuplicate(SQLException failure) {
        String message = failure.getMessage();
        return message != null && (message.toLowerCase().contains("unique")
                || message.toLowerCase().contains("duplicate"));
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> ServiceResult<T> databaseFailure(SQLException failure) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "grade submission database operation failed: " + failure.getMessage());
    }
}

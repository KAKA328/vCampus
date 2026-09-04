package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeSubmissionStatus;
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
                + "created_at,updated_at) VALUES(?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, submission.getSubmissionId());
            statement.setString(2, submission.getOfferingId());
            statement.setString(3, submission.getTeacherId());
            statement.setString(4, submission.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(submission.getCreatedAt()));
            statement.setTimestamp(6, Timestamp.valueOf(submission.getUpdatedAt()));
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
                        && submission.getStatus() != GradeSubmissionStatus.RETURNED) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "grade entries can only be changed in draft or returned status");
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

    private static String selectSubmissions() {
        return "SELECT submission_id,offering_id,teacher_id,status,created_at,updated_at "
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
                results.getTimestamp("updated_at").toLocalDateTime());
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

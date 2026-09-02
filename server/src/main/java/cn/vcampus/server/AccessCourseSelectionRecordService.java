package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.SelectionRecordStatus;
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

/** 使用 Access 保存学生选课记录的服务实现。 */
public final class AccessCourseSelectionRecordService implements CourseSelectionRecordService {
    private final Path databasePath;
    private final CourseOfferingService offerings;

    public AccessCourseSelectionRecordService(Path databasePath, CourseOfferingService offerings) {
        if (databasePath == null || offerings == null) {
            throw new IllegalArgumentException("databasePath and offerings must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.offerings = offerings;
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> create(CourseSelectionRecord record) {
        if (record == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "record must not be null");
        }
        ServiceResult<CourseSelectionRecord> existing = findById(record.getRecordId());
        if (existing.getStatus() == StatusCode.OK) {
            return ServiceResult.failure(StatusCode.CONFLICT, "selection record already exists");
        }
        if (existing.getStatus() != StatusCode.NOT_FOUND) {
            return ServiceResult.failure(existing.getStatus(), existing.getMessage());
        }
        ServiceResult<CourseOffering> offeringResult = offerings.findById(record.getOfferingId());
        if (offeringResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(offeringResult.getStatus(), offeringResult.getMessage());
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                if (hasActiveSelection(connection, record.getStudentId(), record.getOfferingId())) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "student already has an active selection for this offering");
                }
                insert(connection, record);
                connection.commit();
                return ServiceResult.ok(record);
            } catch (SQLException failure) {
                rollbackQuietly(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<CourseSelectionRecord> findById(String recordId) {
        String normalizedRecordId = normalize(recordId);
        if (normalizedRecordId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "recordId must not be blank");
        }
        String sql = selectRecords() + " WHERE selection_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedRecordId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? ServiceResult.ok(readRecord(results))
                        : ServiceResult.<CourseSelectionRecord>failure(StatusCode.NOT_FOUND,
                                "selection record not found");
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<CourseSelectionRecord>> listByStudent(String studentId) {
        return listByStudentAndStatus(studentId, null);
    }

    @Override
    public ServiceResult<List<CourseSelectionRecord>> listActiveByStudent(String studentId) {
        return listByStudentAndStatus(studentId, SelectionRecordStatus.ACTIVE);
    }

    @Override
    public ServiceResult<List<CourseSelectionRecord>> listByOffering(String offeringId) {
        return listByOfferingAndStatus(offeringId, null);
    }

    @Override
    public ServiceResult<List<CourseSelectionRecord>> listActiveByOffering(String offeringId) {
        return listByOfferingAndStatus(offeringId, SelectionRecordStatus.ACTIVE);
    }

    @Override
    public synchronized ServiceResult<CourseSelectionRecord> markDropped(String recordId,
            LocalDateTime droppedAt) {
        String normalizedRecordId = normalize(recordId);
        if (normalizedRecordId == null || droppedAt == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "recordId and droppedAt must not be null");
        }
        ServiceResult<CourseSelectionRecord> existing = findById(normalizedRecordId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        final CourseSelectionRecord dropped;
        try {
            dropped = existing.getData().withDroppedAt(droppedAt);
        } catch (IllegalArgumentException invalidTime) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidTime.getMessage());
        } catch (IllegalStateException alreadyDropped) {
            return ServiceResult.failure(StatusCode.CONFLICT, alreadyDropped.getMessage());
        }
        String sql = "UPDATE tblCourseSelection SET status=?,dropped_at=? WHERE selection_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dropped.getStatus().name());
            statement.setTimestamp(2, Timestamp.valueOf(dropped.getDroppedAt()));
            statement.setString(3, dropped.getRecordId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(dropped)
                    : ServiceResult.<CourseSelectionRecord>failure(StatusCode.NOT_FOUND,
                            "selection record not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private ServiceResult<List<CourseSelectionRecord>> listByStudentAndStatus(String studentId,
            SelectionRecordStatus requiredStatus) {
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        return list(selectRecords() + " WHERE student_id=?"
                + (requiredStatus == null ? "" : " AND status=?") + " ORDER BY selected_at,selection_id",
                normalizedStudentId, requiredStatus);
    }

    private ServiceResult<List<CourseSelectionRecord>> listByOfferingAndStatus(String offeringId,
            SelectionRecordStatus requiredStatus) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        return list(selectRecords() + " WHERE offering_id=?"
                + (requiredStatus == null ? "" : " AND status=?") + " ORDER BY selected_at,selection_id",
                normalizedOfferingId, requiredStatus);
    }

    private ServiceResult<List<CourseSelectionRecord>> list(String sql, String key,
            SelectionRecordStatus requiredStatus) {
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            if (requiredStatus != null) {
                statement.setString(2, requiredStatus.name());
            }
            try (ResultSet results = statement.executeQuery()) {
                List<CourseSelectionRecord> records = new ArrayList<CourseSelectionRecord>();
                while (results.next()) {
                    records.add(readRecord(results));
                }
                return ServiceResult.ok(Collections.unmodifiableList(records));
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private static boolean hasActiveSelection(Connection connection, String studentId,
            String offeringId) throws SQLException {
        String sql = "SELECT selection_id FROM tblCourseSelection "
                + "WHERE student_id=? AND offering_id=? AND status='ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, offeringId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static void insert(Connection connection, CourseSelectionRecord record) throws SQLException {
        String sql = "INSERT INTO tblCourseSelection(selection_id,student_id,offering_id,round_id,"
                + "selection_type,selected_at,status,dropped_at) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getRecordId());
            statement.setString(2, record.getStudentId());
            statement.setString(3, record.getOfferingId());
            statement.setString(4, record.getRoundId());
            statement.setString(5, record.getSelectionType().name());
            statement.setTimestamp(6, Timestamp.valueOf(record.getSelectedAt()));
            statement.setString(7, record.getStatus().name());
            statement.setTimestamp(8, null);
            statement.executeUpdate();
        }
    }

    private static String selectRecords() {
        return "SELECT selection_id,student_id,offering_id,round_id,selection_type,selected_at,"
                + "status,dropped_at FROM tblCourseSelection";
    }

    private static CourseSelectionRecord readRecord(ResultSet results) throws SQLException {
        CourseSelectionRecord record = new CourseSelectionRecord(results.getString("selection_id"),
                results.getString("student_id"), results.getString("offering_id"),
                results.getString("round_id"),
                SelectionType.valueOf(results.getString("selection_type")),
                results.getTimestamp("selected_at").toLocalDateTime());
        if (SelectionRecordStatus.DROPPED.name().equals(results.getString("status"))) {
            return record.withDroppedAt(results.getTimestamp("dropped_at").toLocalDateTime());
        }
        return record;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 原始数据库错误会由调用方返回；回滚失败不能覆盖它。
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> ServiceResult<T> databaseFailure(SQLException failure) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course selection record database operation failed: " + failure.getMessage());
    }
}

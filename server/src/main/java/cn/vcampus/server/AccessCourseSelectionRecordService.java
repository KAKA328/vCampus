package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.CapacityBucket;
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
    public ServiceResult<CourseSelectionRecord> create(CourseSelectionRecord record) {
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
                reserveActiveSelection(connection, record);
                if (!reserveCapacity(connection, record, offeringResult.getData())) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "course offering capacity is full");
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
    public ServiceResult<CourseSelectionRecord> markDropped(String recordId,
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
        String sql = "UPDATE tblCourseSelection SET status=?,dropped_at=? "
                + "WHERE selection_id=? AND status='ACTIVE'";
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, dropped.getStatus().name());
                statement.setTimestamp(2, Timestamp.valueOf(dropped.getDroppedAt()));
                statement.setString(3, dropped.getRecordId());
                if (statement.executeUpdate() != 1) {
                    rollbackQuietly(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT,
                            "selection record is no longer active");
                }
                releaseActiveSelection(connection, dropped);
                releaseCapacity(connection, dropped);
                connection.commit();
                return ServiceResult.ok(dropped);
            } catch (SQLException failure) {
                rollbackQuietly(connection);
                return databaseFailure(failure);
            }
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

    /**
     * 先插入有效选课占用键。复合主键由数据库跨服务实例保证，不依赖 JVM 内的 synchronized。
     */
    private static void reserveActiveSelection(Connection connection, CourseSelectionRecord record)
            throws SQLException {
        String sql = "INSERT INTO tblActiveCourseSelection(student_id,offering_id) VALUES(?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getStudentId());
            statement.setString(2, record.getOfferingId());
            statement.executeUpdate();
        }
    }

    /** 使用条件更新原子占用容量；更新行数为零说明该容量池已满。 */
    private static boolean reserveCapacity(Connection connection, CourseSelectionRecord record,
            CourseOffering offering) throws SQLException {
        int capacity = capacityFor(offering, record.getSelectionType().getCapacityBucket());
        String sql = "UPDATE tblCourseOfferingCapacityUsage SET used_count=used_count+1 "
                + "WHERE offering_id=? AND capacity_bucket=? AND used_count<?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getOfferingId());
            statement.setString(2, record.getSelectionType().getCapacityBucket().name());
            statement.setInt(3, capacity);
            return statement.executeUpdate() == 1;
        }
    }

    /** 在退选事务中释放唯一占用键，保留 tblCourseSelection 的历史记录。 */
    private static void releaseActiveSelection(Connection connection, CourseSelectionRecord record)
            throws SQLException {
        String sql = "DELETE FROM tblActiveCourseSelection WHERE student_id=? AND offering_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getStudentId());
            statement.setString(2, record.getOfferingId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("active course selection lock is missing");
            }
        }
    }

    /** 在退选事务中释放对应容量池的一席。 */
    private static void releaseCapacity(Connection connection, CourseSelectionRecord record)
            throws SQLException {
        String sql = "UPDATE tblCourseOfferingCapacityUsage SET used_count=used_count-1 "
                + "WHERE offering_id=? AND capacity_bucket=? AND used_count>0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getOfferingId());
            statement.setString(2, record.getSelectionType().getCapacityBucket().name());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("course offering capacity usage is missing");
            }
        }
    }

    private static int capacityFor(CourseOffering offering, CapacityBucket bucket) {
        switch (bucket) {
            case REQUIRED: return offering.getRequiredCapacity();
            case ELECTIVE: return offering.getElectiveCapacity();
            case CROSS_MAJOR: return offering.getCrossMajorCapacity();
            default: throw new IllegalArgumentException("unsupported capacity bucket");
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
        if (isConstraintViolation(failure)) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "course selection record conflicts with existing data");
        }
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course selection record database operation failed: " + failure.getMessage());
    }

    /** UCanAccess 会在不同 JDBC 驱动层包装唯一键异常，因此按异常链统一识别。 */
    private static boolean isConstraintViolation(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("unique") || normalized.contains("duplicate")
                        || normalized.contains("primary key") || normalized.contains("constraint")) {
                    return true;
                }
            }
        }
        return false;
    }
}

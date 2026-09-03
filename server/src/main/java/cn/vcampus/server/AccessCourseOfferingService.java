package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseMeeting;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingService;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSchedule;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionRecordService;
import cn.vcampus.course.SelectionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 使用 Access 保存教学班的服务实现。 */
public final class AccessCourseOfferingService implements CourseOfferingService {
    private final Path databasePath;
    private final CourseCatalogService courseCatalog;
    private final CourseSelectionRecordService selectionRecords;

    public AccessCourseOfferingService(Path databasePath, CourseCatalogService courseCatalog) {
        this(databasePath, courseCatalog, null);
    }

    /**
     * 创建教学班持久化服务。
     *
     * <p>选课记录服务接入后，降低容量时会校验已有的有效选课人数；在它尚未接入前，
     * 为避免把容量调低到已选人数以下，本服务只允许增加或保持各容量池的容量。</p>
     */
    public AccessCourseOfferingService(Path databasePath, CourseCatalogService courseCatalog,
            CourseSelectionRecordService selectionRecords) {
        if (databasePath == null || courseCatalog == null) {
            throw new IllegalArgumentException("databasePath and courseCatalog must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.courseCatalog = courseCatalog;
        this.selectionRecords = selectionRecords;
    }

    @Override
    public synchronized ServiceResult<CourseOffering> create(CourseOffering offering) {
        if (offering == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offering must not be null");
        }
        ServiceResult<Void> courseResult = requireActiveCourse(offering.getCourseId());
        if (courseResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
        }
        ServiceResult<CourseOffering> existing = findById(offering.getOfferingId());
        if (existing.getStatus() == StatusCode.OK) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course offering already exists");
        }
        if (existing.getStatus() != StatusCode.NOT_FOUND) {
            return ServiceResult.failure(existing.getStatus(), existing.getMessage());
        }

        String sql = "INSERT INTO tblCourseOffering(offering_id,course_id,term,teacher_id,"
                + "schedule,location,required_capacity,elective_capacity,cross_major_capacity,status) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                writeOffering(statement, offering);
                statement.executeUpdate();
                writeMeetingSchedule(connection, offering);
                connection.commit();
                return ServiceResult.ok(offering);
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<CourseOffering> findById(String offeringId) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        String sql = "SELECT offering_id,course_id,term,teacher_id,schedule,location,"
                + "required_capacity,elective_capacity,cross_major_capacity,status "
                + "FROM tblCourseOffering WHERE offering_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedOfferingId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? ServiceResult.ok(readOffering(results, connection))
                        : ServiceResult.<CourseOffering>failure(StatusCode.NOT_FOUND,
                                "course offering not found");
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<CourseOffering>> listByTerm(String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term must not be blank");
        }
        String sql = selectOfferings() + " WHERE term=? ORDER BY offering_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedTerm);
            return readOfferings(statement, connection);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<List<CourseOffering>> listByCourse(String courseId, String term) {
        return listByCourseAndStatus(courseId, term, null);
    }

    @Override
    public ServiceResult<List<CourseOffering>> listOpenByCourse(String courseId, String term) {
        return listByCourseAndStatus(courseId, term, CourseOfferingStatus.OPEN);
    }

    @Override
    public synchronized ServiceResult<CourseOffering> changeStatus(String offeringId,
            CourseOfferingStatus status) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "offeringId and status must not be null");
        }
        ServiceResult<CourseOffering> existing = findById(normalizedOfferingId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        CourseOffering changed = existing.getData().withStatus(status);
        String sql = "UPDATE tblCourseOffering SET status=? WHERE offering_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changed.getStatus().name());
            statement.setString(2, changed.getOfferingId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<CourseOffering>failure(StatusCode.NOT_FOUND,
                            "course offering not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public synchronized ServiceResult<CourseOffering> changeCapacities(String offeringId,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        String normalizedOfferingId = normalize(offeringId);
        if (normalizedOfferingId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offeringId must not be blank");
        }
        ServiceResult<CourseOffering> existing = findById(normalizedOfferingId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        final CourseOffering changed;
        try {
            changed = existing.getData().withCapacities(requiredCapacity, electiveCapacity,
                    crossMajorCapacity);
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        ServiceResult<Void> capacityResult = verifyCapacityNotBelowActiveSelections(
                existing.getData(), changed);
        if (capacityResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(capacityResult.getStatus(), capacityResult.getMessage());
        }

        String sql = "UPDATE tblCourseOffering SET required_capacity=?,elective_capacity=?,"
                + "cross_major_capacity=? WHERE offering_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, changed.getRequiredCapacity());
            statement.setInt(2, changed.getElectiveCapacity());
            statement.setInt(3, changed.getCrossMajorCapacity());
            statement.setString(4, changed.getOfferingId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<CourseOffering>failure(StatusCode.NOT_FOUND,
                            "course offering not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public synchronized ServiceResult<CourseOffering> updateTeachingInfo(String offeringId,
            String teacherId, String location) {
        String normalizedOfferingId = normalize(offeringId);
        String normalizedTeacherId = normalize(teacherId);
        String normalizedLocation = normalize(location);
        if (normalizedOfferingId == null || normalizedTeacherId == null || normalizedLocation == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "offeringId, teacherId and location must not be blank");
        }
        ServiceResult<CourseOffering> existing = findById(normalizedOfferingId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        CourseOffering changed = existing.getData().withTeachingInfo(normalizedTeacherId,
                normalizedLocation);
        String sql = "UPDATE tblCourseOffering SET teacher_id=?,location=? WHERE offering_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changed.getTeacherId());
            statement.setString(2, changed.getLocation());
            statement.setString(3, changed.getOfferingId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<CourseOffering>failure(StatusCode.NOT_FOUND,
                            "course offering not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private ServiceResult<List<CourseOffering>> listByCourseAndStatus(String courseId, String term,
            CourseOfferingStatus requiredStatus) {
        String normalizedCourseId = normalize(courseId);
        String normalizedTerm = normalize(term);
        if (normalizedCourseId == null || normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "courseId and term must not be blank");
        }
        String sql = selectOfferings() + " WHERE course_id=? AND term=?"
                + (requiredStatus == null ? "" : " AND status=?") + " ORDER BY offering_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedCourseId);
            statement.setString(2, normalizedTerm);
            if (requiredStatus != null) {
                statement.setString(3, requiredStatus.name());
            }
            return readOfferings(statement, connection);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private ServiceResult<Void> requireActiveCourse(String courseId) {
        ServiceResult<Course> result = courseCatalog.findActiveById(courseId);
        return result.getStatus() == StatusCode.OK ? ServiceResult.ok(null)
                : ServiceResult.<Void>failure(result.getStatus(), result.getMessage());
    }

    private ServiceResult<Void> verifyCapacityNotBelowActiveSelections(CourseOffering existing,
            CourseOffering changed) {
        if (selectionRecords == null) {
            return verifyCapacityFromDatabase(existing, changed);
        }
        ServiceResult<List<CourseSelectionRecord>> recordsResult = selectionRecords
                .listActiveByOffering(existing.getOfferingId());
        if (recordsResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(recordsResult.getStatus(), recordsResult.getMessage());
        }
        int requiredUsed = 0;
        int electiveUsed = 0;
        int crossMajorUsed = 0;
        for (CourseSelectionRecord record : recordsResult.getData()) {
            switch (record.getSelectionType().getCapacityBucket()) {
                case REQUIRED: requiredUsed++; break;
                case ELECTIVE: electiveUsed++; break;
                case CROSS_MAJOR: crossMajorUsed++; break;
                default: throw new IllegalStateException("unsupported capacity bucket");
            }
        }
        if (changed.getRequiredCapacity() < requiredUsed
                || changed.getElectiveCapacity() < electiveUsed
                || changed.getCrossMajorCapacity() < crossMajorUsed) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "capacity must not be lower than active selection count");
        }
        return ServiceResult.ok(null);
    }

    /** 使用同一个 Access 数据库中的有效记录统计已占用容量。 */
    private ServiceResult<Void> verifyCapacityFromDatabase(CourseOffering existing,
            CourseOffering changed) {
        String sql = "SELECT selection_type FROM tblCourseSelection "
                + "WHERE offering_id=? AND status='ACTIVE'";
        int requiredUsed = 0;
        int electiveUsed = 0;
        int crossMajorUsed = 0;
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, existing.getOfferingId());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    SelectionType type = SelectionType.valueOf(results.getString("selection_type"));
                    switch (type.getCapacityBucket()) {
                        case REQUIRED: requiredUsed++; break;
                        case ELECTIVE: electiveUsed++; break;
                        case CROSS_MAJOR: crossMajorUsed++; break;
                        default: throw new IllegalStateException("unsupported capacity bucket");
                    }
                }
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
        if (changed.getRequiredCapacity() < requiredUsed
                || changed.getElectiveCapacity() < electiveUsed
                || changed.getCrossMajorCapacity() < crossMajorUsed) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "capacity must not be lower than active selection count");
        }
        return ServiceResult.ok(null);
    }

    private static String selectOfferings() {
        return "SELECT offering_id,course_id,term,teacher_id,schedule,location,required_capacity,"
                + "elective_capacity,cross_major_capacity,status FROM tblCourseOffering";
    }

    private static ServiceResult<List<CourseOffering>> readOfferings(PreparedStatement statement,
            Connection connection)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<CourseOffering> offerings = new ArrayList<CourseOffering>();
            while (results.next()) {
                offerings.add(readOffering(results, connection));
            }
            return ServiceResult.ok(Collections.unmodifiableList(offerings));
        }
    }

    private static void writeOffering(PreparedStatement statement, CourseOffering offering)
            throws SQLException {
        statement.setString(1, offering.getOfferingId());
        statement.setString(2, offering.getCourseId());
        statement.setString(3, offering.getTerm());
        statement.setString(4, offering.getTeacherId());
        statement.setString(5, offering.getSchedule());
        statement.setString(6, offering.getLocation());
        statement.setInt(7, offering.getRequiredCapacity());
        statement.setInt(8, offering.getElectiveCapacity());
        statement.setInt(9, offering.getCrossMajorCapacity());
        statement.setString(10, offering.getStatus().name());
    }

    private static CourseOffering readOffering(ResultSet results, Connection connection)
            throws SQLException {
        String offeringId = results.getString("offering_id");
        CourseOffering offering = new CourseOffering(offeringId, results.getString("course_id"),
                results.getString("term"), results.getString("teacher_id"),
                results.getString("schedule"), results.getString("location"),
                results.getInt("required_capacity"), results.getInt("elective_capacity"),
                results.getInt("cross_major_capacity"),
                CourseOfferingStatus.valueOf(results.getString("status")));
        return offering.withMeetingSchedule(readMeetingSchedule(connection, offeringId));
    }

    /** 将一门教学班的全部结构化上课时间写入同一事务。 */
    private static void writeMeetingSchedule(Connection connection, CourseOffering offering)
            throws SQLException {
        List<CourseMeeting> meetings = offering.getMeetingSchedule().getMeetings();
        if (meetings.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO tblCourseMeeting(offering_id,day_of_week,start_period,end_period,"
                + "location) VALUES(?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CourseMeeting meeting : meetings) {
                statement.setString(1, offering.getOfferingId());
                statement.setInt(2, meeting.getDayOfWeek().getValue());
                statement.setInt(3, meeting.getStartPeriod());
                statement.setInt(4, meeting.getEndPeriod());
                statement.setString(5, meeting.getLocation());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 按星期和节次恢复教学班的结构化上课时间。 */
    private static CourseSchedule readMeetingSchedule(Connection connection, String offeringId)
            throws SQLException {
        String sql = "SELECT day_of_week,start_period,end_period,location FROM tblCourseMeeting "
                + "WHERE offering_id=? ORDER BY day_of_week,start_period";
        List<CourseMeeting> meetings = new ArrayList<CourseMeeting>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, offeringId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    meetings.add(new CourseMeeting(DayOfWeek.of(results.getInt("day_of_week")),
                            results.getInt("start_period"), results.getInt("end_period"),
                            results.getString("location")));
                }
            }
        }
        return meetings.isEmpty() ? CourseSchedule.empty() : new CourseSchedule(meetings);
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 原始数据库异常会作为本次操作失败的原因返回。
        }
    }

    private Connection open() throws SQLException {
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

    private static <T> ServiceResult<T> databaseFailure(SQLException failure) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course offering database operation failed: " + failure.getMessage());
    }
}

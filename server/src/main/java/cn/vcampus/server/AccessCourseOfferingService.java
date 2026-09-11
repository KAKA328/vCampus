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
import cn.vcampus.course.CapacityBucket;
import cn.vcampus.course.SelectionType;
import cn.vcampus.student.DefaultTeacherProfileService;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 使用 Access 保存教学班的服务实现。 */
public final class AccessCourseOfferingService implements CourseOfferingService {
    private final Path databasePath;
    private final CourseCatalogService courseCatalog;
    private final CourseSelectionRecordService selectionRecords;
    private final TeacherProfileService teacherProfiles;

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
        this(databasePath, courseCatalog, selectionRecords,
                defaultTeacherProfiles(databasePath));
    }

    AccessCourseOfferingService(Path databasePath, CourseCatalogService courseCatalog,
            CourseSelectionRecordService selectionRecords, TeacherProfileService teacherProfiles) {
        if (databasePath == null || courseCatalog == null || teacherProfiles == null) {
            throw new IllegalArgumentException(
                    "databasePath, courseCatalog and teacherProfiles must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.courseCatalog = courseCatalog;
        this.selectionRecords = selectionRecords;
        this.teacherProfiles = teacherProfiles;
    }

    @Override
    public synchronized ServiceResult<CourseOffering> create(CourseOffering offering) {
        if (offering == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "offering must not be null");
        }
        ServiceResult<Void> scheduleResult = requireStructuredSchedule(offering.getMeetingSchedule());
        if (scheduleResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scheduleResult.getStatus(), scheduleResult.getMessage());
        }
        ServiceResult<Void> courseResult = requireActiveCourse(offering.getCourseId());
        if (courseResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
        }
        ServiceResult<Void> teacherResult = requireActiveTeacher(offering.getTeacherId());
        if (teacherResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(teacherResult.getStatus(), teacherResult.getMessage());
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
                initializeCapacityUsage(connection, offering);
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
    public ServiceResult<List<CourseOffering>> listByTeacher(String teacherId, String term) {
        String normalizedTeacherId = normalize(teacherId);
        String normalizedTerm = normalize(term);
        if (normalizedTeacherId == null || normalizedTerm == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "teacherId and term must not be blank");
        }
        String sql = selectOfferings() + " WHERE teacher_id=? AND term=? ORDER BY offering_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedTeacherId);
            statement.setString(2, normalizedTerm);
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
        ServiceResult<Void> teacherResult = requireActiveTeacher(normalizedTeacherId);
        if (teacherResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(teacherResult.getStatus(), teacherResult.getMessage());
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

    @Override
    public synchronized ServiceResult<CourseOffering> updateSchedule(String offeringId,
            String schedule, CourseSchedule meetingSchedule) {
        String normalizedOfferingId = normalize(offeringId);
        String normalizedSchedule = normalize(schedule);
        if (normalizedOfferingId == null || normalizedSchedule == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "offeringId and schedule must not be blank");
        }
        ServiceResult<Void> scheduleResult = requireStructuredSchedule(meetingSchedule);
        if (scheduleResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scheduleResult.getStatus(), scheduleResult.getMessage());
        }
        ServiceResult<CourseOffering> existing = findById(normalizedOfferingId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        CourseOffering changed = existing.getData().withSchedule(normalizedSchedule, meetingSchedule);
        String sql = "UPDATE tblCourseOffering SET schedule=? WHERE offering_id=?";
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, changed.getSchedule());
                statement.setString(2, changed.getOfferingId());
                if (statement.executeUpdate() != 1) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "course offering not found");
                }
                replaceMeetingSchedule(connection, changed);
                connection.commit();
                return ServiceResult.ok(changed);
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public synchronized ServiceResult<CourseOffering> updateDetails(CourseOffering offering) {
        return offering == null ? ServiceResult.<CourseOffering>failure(StatusCode.BAD_REQUEST,
                "offering must not be null") : updateDetails(offering.getOfferingId(), offering);
    }

    @Override
    public synchronized ServiceResult<CourseOffering> updateDetails(String originalOfferingId,
            CourseOffering offering) {
        String normalizedOriginalId = normalize(originalOfferingId);
        if (normalizedOriginalId == null || offering == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "originalOfferingId and offering must not be null");
        }
        ServiceResult<Void> scheduleResult = requireStructuredSchedule(offering.getMeetingSchedule());
        if (scheduleResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scheduleResult.getStatus(), scheduleResult.getMessage());
        }
        ServiceResult<CourseOffering> existing = findById(normalizedOriginalId);
        if (existing.getStatus() != StatusCode.OK) return existing;
        CourseOffering current = existing.getData();
        if (!current.getTerm().equals(offering.getTerm())) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "term cannot be changed for an existing offering");
        }
        if (!current.getCourseId().equals(offering.getCourseId())) {
            ServiceResult<Void> courseResult = requireActiveCourse(offering.getCourseId());
            if (courseResult.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
            }
        }
        if (!normalizedOriginalId.equals(offering.getOfferingId())) {
            ServiceResult<CourseOffering> duplicate = findById(offering.getOfferingId());
            if (duplicate.getStatus() == StatusCode.OK) {
                return ServiceResult.failure(StatusCode.CONFLICT, "course offering already exists");
            }
            if (duplicate.getStatus() != StatusCode.NOT_FOUND) {
                return ServiceResult.failure(duplicate.getStatus(), duplicate.getMessage());
            }
        }
        ServiceResult<Void> teacherResult = requireActiveTeacher(offering.getTeacherId());
        if (teacherResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(teacherResult.getStatus(), teacherResult.getMessage());
        }
        final CourseOffering changed;
        try {
            changed = new CourseOffering(offering.getOfferingId(), offering.getCourseId(),
                    current.getTerm(), offering.getTeacherId(), offering.getSchedule(),
                    offering.getLocation(), offering.getRequiredCapacity(),
                    offering.getElectiveCapacity(), offering.getCrossMajorCapacity(),
                    current.getStatus()).withMeetingSchedule(offering.getMeetingSchedule());
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        ServiceResult<Void> capacityResult = verifyCapacityNotBelowActiveSelections(current, changed);
        if (capacityResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(capacityResult.getStatus(), capacityResult.getMessage());
        }
        String sql = "UPDATE tblCourseOffering SET offering_id=?,course_id=?,teacher_id=?,schedule=?,location=?,"
                + "required_capacity=?,elective_capacity=?,cross_major_capacity=? WHERE offering_id=?";
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, changed.getOfferingId());
                statement.setString(2, changed.getCourseId());
                statement.setString(3, changed.getTeacherId());
                statement.setString(4, changed.getSchedule());
                statement.setString(5, changed.getLocation());
                statement.setInt(6, changed.getRequiredCapacity());
                statement.setInt(7, changed.getElectiveCapacity());
                statement.setInt(8, changed.getCrossMajorCapacity());
                statement.setString(9, normalizedOriginalId);
                if (statement.executeUpdate() != 1) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "course offering not found");
                }
                updateOfferingReferences(connection, normalizedOriginalId, changed);
                replaceMeetingSchedule(connection, changed);
                connection.commit();
                return ServiceResult.ok(changed);
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
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

    private static ServiceResult<Void> requireStructuredSchedule(CourseSchedule meetingSchedule) {
        if (meetingSchedule == null || meetingSchedule.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "meeting schedule must contain at least one meeting");
        }
        return ServiceResult.ok(null);
    }

    private ServiceResult<Void> requireActiveTeacher(String teacherId) {
        ServiceResult<TeacherProfile> result = teacherProfiles.findById(teacherId);
        if (result.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(result.getStatus(), result.getMessage());
        }
        if (!result.getData().isActive()) {
            return ServiceResult.failure(StatusCode.CONFLICT, "teacher is not active");
        }
        return ServiceResult.ok(null);
    }

    private static TeacherProfileService defaultTeacherProfiles(Path databasePath) {
        return new DefaultTeacherProfileService(new AccessTeacherRepository(databasePath));
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
                offerings.add(readOfferingFields(results));
            }
            if (offerings.isEmpty()) {
                return ServiceResult.ok(Collections.<CourseOffering>emptyList());
            }
            Map<String, CourseSchedule> schedules = readMeetingSchedules(connection, offerings);
            List<CourseOffering> hydrated = new ArrayList<CourseOffering>();
            for (CourseOffering offering : offerings) {
                CourseSchedule schedule = schedules.get(offering.getOfferingId());
                hydrated.add(offering.withMeetingSchedule(schedule == null
                        ? CourseSchedule.empty() : schedule));
            }
            return ServiceResult.ok(Collections.unmodifiableList(hydrated));
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
        CourseOffering offering = readOfferingFields(results);
        return offering.withMeetingSchedule(readMeetingSchedule(connection, offering.getOfferingId()));
    }

    private static CourseOffering readOfferingFields(ResultSet results) throws SQLException {
        return new CourseOffering(results.getString("offering_id"), results.getString("course_id"),
                results.getString("term"), results.getString("teacher_id"),
                results.getString("schedule"), results.getString("location"),
                results.getInt("required_capacity"), results.getInt("elective_capacity"),
                results.getInt("cross_major_capacity"),
                CourseOfferingStatus.valueOf(results.getString("status")));
    }

    /** 将一门教学班的全部结构化上课时间写入同一事务。 */
    private static void writeMeetingSchedule(Connection connection, CourseOffering offering)
            throws SQLException {
        List<CourseMeeting> meetings = offering.getMeetingSchedule().getMeetings();
        if (meetings.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO tblCourseMeeting(offering_id,day_of_week,start_period,end_period,"
                + "start_week,end_week,location) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CourseMeeting meeting : meetings) {
                statement.setString(1, offering.getOfferingId());
                statement.setInt(2, meeting.getDayOfWeek().getValue());
                statement.setInt(3, meeting.getStartPeriod());
                statement.setInt(4, meeting.getEndPeriod());
                statement.setInt(5, meeting.getStartWeek());
                statement.setInt(6, meeting.getEndWeek());
                statement.setString(7, meeting.getLocation());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 删除旧排课后写入新排课，调用方必须处于同一数据库事务中。 */
    private static void replaceMeetingSchedule(Connection connection, CourseOffering offering)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM tblCourseMeeting WHERE offering_id=?")) {
            statement.setString(1, offering.getOfferingId());
            statement.executeUpdate();
        }
        writeMeetingSchedule(connection, offering);
    }

    /**
     * 教学班编号变更后，在同一事务内迁移所有直接关联数据。
     *
     * <p>完整演示库包含这些表；精简的单元测试数据库可能只创建其中一部分，因此逐表检测。</p>
     */
    private static void updateOfferingReferences(Connection connection, String originalOfferingId,
            CourseOffering changed) throws SQLException {
        updateOfferingReference(connection, "tblCourseMeeting", originalOfferingId,
                changed.getOfferingId());
        updateOfferingReference(connection, "tblCourseSelection", originalOfferingId,
                changed.getOfferingId());
        updateOfferingReference(connection, "tblActiveCourseSelection", originalOfferingId,
                changed.getOfferingId());
        updateOfferingReference(connection, "tblCourseOfferingCapacityUsage", originalOfferingId,
                changed.getOfferingId());
        updateOfferingReference(connection, "tblGradeSubmission", originalOfferingId,
                changed.getOfferingId());
        if (tableExists(connection, "tblCourseResult")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE tblCourseResult SET offering_id=?,course_id=? WHERE offering_id=?")) {
                statement.setString(1, changed.getOfferingId());
                statement.setString(2, changed.getCourseId());
                statement.setString(3, originalOfferingId);
                statement.executeUpdate();
            }
        }
    }

    private static void updateOfferingReference(Connection connection, String tableName,
            String originalOfferingId, String updatedOfferingId) throws SQLException {
        if (!tableExists(connection, tableName)) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + tableName + " SET offering_id=? WHERE offering_id=?")) {
            statement.setString(1, updatedOfferingId);
            statement.setString(2, originalOfferingId);
            statement.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName,
                new String[] { "TABLE" })) {
            return tables.next();
        }
    }

    /** 为新教学班的三个容量池建立当前占用人数，初始均为零。 */
    private static void initializeCapacityUsage(Connection connection, CourseOffering offering)
            throws SQLException {
        String sql = "INSERT INTO tblCourseOfferingCapacityUsage("
                + "offering_id,capacity_bucket,used_count) VALUES(?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (CapacityBucket bucket : CapacityBucket.values()) {
                statement.setString(1, offering.getOfferingId());
                statement.setString(2, bucket.name());
                statement.setInt(3, 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** 按星期和节次恢复教学班的结构化上课时间。 */
    private static CourseSchedule readMeetingSchedule(Connection connection, String offeringId)
            throws SQLException {
        String sql = "SELECT day_of_week,start_period,end_period,start_week,end_week,location FROM tblCourseMeeting "
                + "WHERE offering_id=? ORDER BY day_of_week,start_period";
        List<CourseMeeting> meetings = new ArrayList<CourseMeeting>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, offeringId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    meetings.add(new CourseMeeting(DayOfWeek.of(results.getInt("day_of_week")),
                            results.getInt("start_period"), results.getInt("end_period"),
                            results.getInt("start_week"), results.getInt("end_week"),
                            results.getString("location")));
                }
            }
        }
        return meetings.isEmpty() ? CourseSchedule.empty() : new CourseSchedule(meetings);
    }

    /**
     * 同一教学班列表的结构化排课一次性读取，避免列表中每一行都单独执行 SQL。
     */
    private static Map<String, CourseSchedule> readMeetingSchedules(Connection connection,
            List<CourseOffering> offerings) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT offering_id,day_of_week,start_period,end_period,")
                .append("start_week,end_week,location FROM tblCourseMeeting WHERE offering_id IN (");
        for (int index = 0; index < offerings.size(); index++) {
            if (index > 0) sql.append(',');
            sql.append('?');
        }
        sql.append(") ORDER BY offering_id,day_of_week,start_period");
        Map<String, List<CourseMeeting>> meetingsByOffering =
                new LinkedHashMap<String, List<CourseMeeting>>();
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < offerings.size(); index++) {
                statement.setString(index + 1, offerings.get(index).getOfferingId());
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String offeringId = results.getString("offering_id");
                    List<CourseMeeting> meetings = meetingsByOffering.get(offeringId);
                    if (meetings == null) {
                        meetings = new ArrayList<CourseMeeting>();
                        meetingsByOffering.put(offeringId, meetings);
                    }
                    meetings.add(new CourseMeeting(DayOfWeek.of(results.getInt("day_of_week")),
                            results.getInt("start_period"), results.getInt("end_period"),
                            results.getInt("start_week"), results.getInt("end_week"),
                            results.getString("location")));
                }
            }
        }
        Map<String, CourseSchedule> schedules = new LinkedHashMap<String, CourseSchedule>();
        for (Map.Entry<String, List<CourseMeeting>> entry : meetingsByOffering.entrySet()) {
            schedules.put(entry.getKey(), new CourseSchedule(entry.getValue()));
        }
        return schedules;
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

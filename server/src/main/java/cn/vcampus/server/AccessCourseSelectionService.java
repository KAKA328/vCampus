package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.course.SelectionRound;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 使用 Access 数据库保存课程和选课记录的选课服务实现。
 *
 * <p>它与内存版服务实现相同的接口，服务器接入时可以直接替换。</p>
 */
public final class AccessCourseSelectionService implements CourseSelectionService {
    private final Path databasePath;

    public AccessCourseSelectionService(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<List<Course>> listCourses() {
        String sql = "SELECT course_id,course_name,credits,capacity FROM tblCourse ORDER BY course_id";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            return ServiceResult.ok(readCourses(results));
        } catch (SQLException | IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to list courses");
        }
    }

    @Override
    public synchronized ServiceResult<Void> select(String studentId, String courseId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedStudentId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "studentId and courseId must not be blank");
        }

        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                Course course = findCourse(connection, normalizedCourseId);
                if (course == null) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
                }
                if (selectionExists(connection, normalizedStudentId, normalizedCourseId)) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT, "course is already selected");
                }
                if (selectionCount(connection, normalizedCourseId) >= course.getCapacity()) {
                    connection.rollback();
                    return ServiceResult.failure(StatusCode.CONFLICT, "course is full");
                }

                insertSelection(connection, normalizedStudentId, normalizedCourseId);
                connection.commit();
                return ServiceResult.ok(null);
            } catch (SQLException | IllegalArgumentException failure) {
                rollbackQuietly(connection);
                return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to select course");
            }
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to connect to course database");
        }
    }

    @Override
    public synchronized ServiceResult<Void> drop(String studentId, String courseId) {
        String normalizedStudentId = normalize(studentId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedStudentId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "studentId and courseId must not be blank");
        }

        String sql = "DELETE FROM tblCourseSelection WHERE student_id=? AND course_id=?";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedStudentId);
            statement.setString(2, normalizedCourseId);
            if (statement.executeUpdate() == 0) {
                return ServiceResult.failure(StatusCode.NOT_FOUND, "course selection not found");
            }
            return ServiceResult.ok(null);
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to drop course");
        }
    }

    @Override
    public ServiceResult<List<Course>> selectedCourses(String studentId) {
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }

        String sql = "SELECT c.course_id,c.course_name,c.credits,c.capacity "
                + "FROM tblCourse AS c INNER JOIN tblCourseSelection AS s "
                + "ON c.course_id=s.course_id WHERE s.student_id=? "
                + "ORDER BY s.selected_at,c.course_id";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedStudentId);
            try (ResultSet results = statement.executeQuery()) {
                return ServiceResult.ok(readCourses(results));
            }
        } catch (SQLException | IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read selected courses");
        }
    }

    @Override
    public ServiceResult<List<SelectionRound>> listRounds(String term) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course protocol v2 is not available in Access mode yet");
    }

    @Override
    public ServiceResult<List<CourseOffering>> listOfferings(String roundId, String courseId) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course protocol v2 is not available in Access mode yet");
    }

    @Override
    public ServiceResult<CourseSelectionRecord> selectOffering(String studentId, String roundId, String offeringId) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course protocol v2 is not available in Access mode yet");
    }

    @Override
    public ServiceResult<Void> dropRecord(String studentId, String recordId) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course protocol v2 is not available in Access mode yet");
    }

    @Override
    public ServiceResult<List<CourseSelectionRecord>> selectedRecords(String studentId, String term) {
        return ServiceResult.failure(StatusCode.SERVER_ERROR,
                "course protocol v2 is not available in Access mode yet");
    }

    private static List<Course> readCourses(ResultSet results) throws SQLException {
        List<Course> courses = new ArrayList<Course>();
        while (results.next()) {
            courses.add(new Course(results.getString("course_id"), results.getString("course_name"),
                    results.getInt("credits"), results.getInt("capacity")));
        }
        return courses;
    }

    private static Course findCourse(Connection connection, String courseId) throws SQLException {
        String sql = "SELECT course_id,course_name,credits,capacity FROM tblCourse WHERE course_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                return new Course(results.getString("course_id"), results.getString("course_name"),
                        results.getInt("credits"), results.getInt("capacity"));
            }
        }
    }

    private static boolean selectionExists(Connection connection, String studentId, String courseId)
            throws SQLException {
        String sql = "SELECT selection_id FROM tblCourseSelection WHERE student_id=? AND course_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setString(2, courseId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private static int selectionCount(Connection connection, String courseId) throws SQLException {
        String sql = "SELECT COUNT(*) AS selection_count FROM tblCourseSelection WHERE course_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, courseId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getInt("selection_count") : 0;
            }
        }
    }

    private static void insertSelection(Connection connection, String studentId, String courseId)
            throws SQLException {
        String sql = "INSERT INTO tblCourseSelection(selection_id,student_id,course_id,selected_at) "
                + "VALUES(?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studentId);
            statement.setString(3, courseId);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        loadDriver();
        // 每次操作结束后释放 Access 文件，避免 Windows 持续锁住 .accdb 文件。
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void loadDriver() throws SQLException {
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException failure) {
            throw new SQLException("UCanAccess driver is unavailable", failure);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 事务回滚失败时由外层返回服务器错误，不再覆盖原始错误。
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

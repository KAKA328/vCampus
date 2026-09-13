package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.CourseStatus;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 使用 Access 保存课程目录的服务实现。 */
public final class AccessCourseCatalogService implements CourseCatalogService {
    private final Path databasePath;

    public AccessCourseCatalogService(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public synchronized ServiceResult<Course> create(Course course) {
        if (course == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "course must not be null");
        }
        if (course.getStatus() != CourseStatus.ACTIVE) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "new course must be created as ACTIVE");
        }
        ServiceResult<Course> existing = findById(course.getCourseId());
        if (existing.getStatus() == StatusCode.OK) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course already exists");
        }
        if (existing.getStatus() != StatusCode.NOT_FOUND) {
            return ServiceResult.failure(existing.getStatus(), existing.getMessage());
        }

        String sql = "INSERT INTO tblCourse(course_id,course_name,credits,status) VALUES(?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            writeCourse(statement, course);
            statement.executeUpdate();
            return ServiceResult.ok(course);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<Course> findById(String courseId) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "courseId must not be blank");
        }
        String sql = "SELECT course_id,course_name,credits,status "
                + "FROM tblCourse WHERE course_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedCourseId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? ServiceResult.ok(readCourse(results))
                        : ServiceResult.<Course>failure(StatusCode.NOT_FOUND, "course not found");
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public ServiceResult<Course> findActiveById(String courseId) {
        ServiceResult<Course> result = findById(courseId);
        if (result.getStatus() != StatusCode.OK) {
            return result;
        }
        if (result.getData().getStatus() != CourseStatus.ACTIVE) {
            return ServiceResult.failure(StatusCode.CONFLICT, "course is disabled");
        }
        return result;
    }

    @Override
    public ServiceResult<List<Course>> listAll() {
        return list("SELECT course_id,course_name,credits,status FROM tblCourse "
                + "ORDER BY course_id");
    }

    @Override
    public ServiceResult<List<Course>> listActive() {
        String sql = "SELECT course_id,course_name,credits,status FROM tblCourse "
                + "WHERE status=? ORDER BY course_id";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, CourseStatus.ACTIVE.name());
            return readCourses(statement);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public synchronized ServiceResult<Course> updateDetails(String courseId, String name,
            int credits) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "courseId must not be blank");
        }
        ServiceResult<Course> existing = findById(normalizedCourseId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        try {
            return updateDetails(normalizedCourseId,
                    existing.getData().withDetails(name, credits));
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
    }

    @Override
    public synchronized ServiceResult<Course> updateDetails(String originalCourseId, Course course) {
        String normalizedOriginalId = normalize(originalCourseId);
        if (normalizedOriginalId == null || course == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "originalCourseId and course must not be null");
        }
        ServiceResult<Course> existing = findById(normalizedOriginalId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        if (!normalizedOriginalId.equals(course.getCourseId())) {
            ServiceResult<Course> duplicate = findById(course.getCourseId());
            if (duplicate.getStatus() == StatusCode.OK) {
                return ServiceResult.failure(StatusCode.CONFLICT, "course already exists");
            }
            if (duplicate.getStatus() != StatusCode.NOT_FOUND) {
                return ServiceResult.failure(duplicate.getStatus(), duplicate.getMessage());
            }
        }

        String sql = "UPDATE tblCourse SET course_id=?,course_name=?,credits=?,status=? WHERE course_id=?";
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, course.getCourseId());
                statement.setString(2, course.getName());
                statement.setInt(3, course.getCredits());
                statement.setString(4, course.getStatus().name());
                statement.setString(5, normalizedOriginalId);
                if (statement.executeUpdate() != 1) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
                }
                updateCourseReference(connection, "tblCourseOffering", normalizedOriginalId,
                        course.getCourseId());
                updateCourseReference(connection, "tblTrainingPlanCourse", normalizedOriginalId,
                        course.getCourseId());
                updateCourseReference(connection, "tblCourseResult", normalizedOriginalId,
                        course.getCourseId());
                connection.commit();
                return ServiceResult.ok(course);
            } catch (SQLException failure) {
                rollback(connection);
                return databaseFailure(failure);
            }
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    @Override
    public synchronized ServiceResult<Course> changeStatus(String courseId, CourseStatus status) {
        String normalizedCourseId = normalize(courseId);
        if (normalizedCourseId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "courseId and status must not be null");
        }
        ServiceResult<Course> existing = findById(normalizedCourseId);
        if (existing.getStatus() != StatusCode.OK) {
            return existing;
        }
        Course changed = existing.getData().withStatus(status);
        String sql = "UPDATE tblCourse SET status=? WHERE course_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changed.getStatus().name());
            statement.setString(2, changed.getCourseId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<Course>failure(StatusCode.NOT_FOUND, "course not found");
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private ServiceResult<List<Course>> list(String sql) {
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            return readCourses(statement);
        } catch (SQLException failure) {
            return databaseFailure(failure);
        }
    }

    private static ServiceResult<List<Course>> readCourses(PreparedStatement statement)
            throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<Course> courses = new ArrayList<Course>();
            while (results.next()) {
                courses.add(readCourse(results));
            }
            return ServiceResult.ok(Collections.unmodifiableList(courses));
        }
    }

    private static void writeCourse(PreparedStatement statement, Course course) throws SQLException {
        statement.setString(1, course.getCourseId());
        statement.setString(2, course.getName());
        statement.setInt(3, course.getCredits());
        statement.setString(4, course.getStatus().name());
    }

    private static Course readCourse(ResultSet results) throws SQLException {
        String courseId = results.getString("course_id");
        String name = results.getString("course_name");
        int credits = results.getInt("credits");
        Course course = new Course(courseId, name, credits);
        return course.withStatus(CourseStatus.valueOf(results.getString("status")));
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    /** 更新引用课程编号的表；精简测试数据库可能未创建所有关联表。 */
    private static void updateCourseReference(Connection connection, String tableName,
            String originalCourseId, String updatedCourseId) throws SQLException {
        if (!tableExists(connection, tableName)) return;
        String sql = "UPDATE " + tableName + " SET course_id=? WHERE course_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, updatedCourseId);
            statement.setString(2, originalCourseId);
            statement.executeUpdate();
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName,
                new String[] { "TABLE" })) {
            return tables.next();
        }
    }

    private static void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // 原始数据库异常会作为本次操作失败的原因返回。
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
                "course catalog database operation failed: " + failure.getMessage());
    }
}

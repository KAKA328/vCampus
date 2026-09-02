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

        String sql = "INSERT INTO tblCourse(course_id,course_name,credits,capacity,status) "
                + "VALUES(?,?,?,?,?)";
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
        String sql = "SELECT course_id,course_name,credits,capacity,status "
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
        return list("SELECT course_id,course_name,credits,capacity,status FROM tblCourse "
                + "ORDER BY course_id");
    }

    @Override
    public ServiceResult<List<Course>> listActive() {
        String sql = "SELECT course_id,course_name,credits,capacity,status FROM tblCourse "
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
        final Course changed;
        try {
            changed = existing.getData().withDetails(name, credits);
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }

        String sql = "UPDATE tblCourse SET course_name=?,credits=? WHERE course_id=?";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, changed.getName());
            statement.setInt(2, changed.getCredits());
            statement.setString(3, changed.getCourseId());
            return statement.executeUpdate() == 1 ? ServiceResult.ok(changed)
                    : ServiceResult.<Course>failure(StatusCode.NOT_FOUND, "course not found");
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
        // 课程容量已迁移到教学班；该列仅用于兼容早期 tblCourse 表结构。
        statement.setInt(4, course.getCapacity());
        statement.setString(5, course.getStatus().name());
    }

    private static Course readCourse(ResultSet results) throws SQLException {
        String courseId = results.getString("course_id");
        String name = results.getString("course_name");
        int credits = results.getInt("credits");
        int legacyCapacity = results.getInt("capacity");
        // 新课程目录会把兼容列写为 0，不能使用要求正容量的旧构造方法读取。
        Course course = legacyCapacity == 0 ? new Course(courseId, name, credits)
                : new Course(courseId, name, credits, legacyCapacity);
        return course.withStatus(CourseStatus.valueOf(results.getString("status")));
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
                "course catalog database operation failed: " + failure.getMessage());
    }
}

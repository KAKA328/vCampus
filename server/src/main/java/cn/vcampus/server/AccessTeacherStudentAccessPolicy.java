package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Access-backed teacher scope based on active teaching and course-selection records. */
final class AccessTeacherStudentAccessPolicy implements TeacherStudentAccessPolicy {
    private final Path databasePath;

    AccessTeacherStudentAccessPolicy(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<Boolean> canRead(String teacherUserId, String studentId) {
        String normalizedUserId = normalize(teacherUserId);
        String normalizedStudentId = normalize(studentId);
        if (normalizedUserId == null || normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "teacherUserId and studentId must not be blank");
        }
        String sql = "SELECT TOP 1 s.selection_id FROM (tblTeacher AS t "
                + "INNER JOIN tblCourseOffering AS o ON t.teacher_id=o.teacher_id) "
                + "INNER JOIN tblCourseSelection AS s ON o.offering_id=s.offering_id "
                + "WHERE t.user_id=? AND s.student_id=? AND s.status='ACTIVE'";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedUserId);
            statement.setString(2, normalizedStudentId);
            try (ResultSet results = statement.executeQuery()) {
                return ServiceResult.ok(results.next());
            }
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR,
                    "failed to verify teacher student scope");
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
}

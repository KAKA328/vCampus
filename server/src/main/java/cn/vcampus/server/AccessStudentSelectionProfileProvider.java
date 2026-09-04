package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.course.StudentSelectionProfileProvider;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** 从 Access 学籍与成绩表构造服务端选课资料，客户端不参与身份信息传递。 */
public final class AccessStudentSelectionProfileProvider
        implements StudentSelectionProfileProvider {
    private final Path databasePath;

    public AccessStudentSelectionProfileProvider(Path databasePath) {
        if (databasePath == null) {
            throw new IllegalArgumentException("databasePath must not be null");
        }
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public ServiceResult<StudentSelectionProfile> findByUserId(String userId) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        try (Connection connection = open()) {
            StudentRow student = findStudent(connection, normalizedUserId);
            if (student == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "student profile is not bound to this user");
            }
            String currentTerm = findCurrentTerm(connection);
            if (currentTerm == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "no current selection term is configured");
            }
            int recommendedTerm = recommendedTerm(currentTerm, student.enrollmentYear);
            Set<String> pendingRetakes = findPendingRetakeCourseIds(connection, student.studentId);
            return ServiceResult.ok(new StudentSelectionProfile(normalizedUserId, student.studentId,
                    student.majorName, student.enrollmentYear, student.status, currentTerm,
                    recommendedTerm, pendingRetakes));
        } catch (IllegalArgumentException invalidData) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR,
                    "student course selection profile data is invalid: " + invalidData.getMessage());
        } catch (SQLException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR,
                    "student course selection profile database operation failed: "
                            + failure.getMessage());
        }
    }

    private static StudentRow findStudent(Connection connection, String userId) throws SQLException {
        String sql = "SELECT student_id,major_name,enrollment_year,status FROM tblStudent WHERE user_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? new StudentRow(results.getString("student_id"),
                        results.getString("major_name"), results.getInt("enrollment_year"),
                        results.getString("status")) : null;
            }
        }
    }

    /** 优先使用当前开放轮次；关闭时回退到最近配置的轮次，以便学生仍可查看已选课程。 */
    private static String findCurrentTerm(Connection connection) throws SQLException {
        String sql = "SELECT term FROM tblSelectionRound WHERE status='OPEN' AND starts_at<=? "
                + "AND ends_at>=? ORDER BY starts_at";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, now);
            statement.setTimestamp(2, now);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return results.getString("term");
                }
            }
        }
        String fallbackSql = "SELECT TOP 1 term FROM tblSelectionRound ORDER BY starts_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(fallbackSql);
                ResultSet results = statement.executeQuery()) {
            return results.next() ? results.getString("term") : null;
        }
    }

    /** 已通过的课程不再待重修；同一课程的多次未通过记录只返回一次课程号。 */
    private static Set<String> findPendingRetakeCourseIds(Connection connection, String studentId)
            throws SQLException {
        String sql = "SELECT course_id,passed FROM tblCourseResult WHERE student_id=?";
        Set<String> failedCourses = new LinkedHashSet<String>();
        Set<String> passedCourses = new LinkedHashSet<String>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    String courseId = results.getString("course_id");
                    if (results.getBoolean("passed")) {
                        passedCourses.add(courseId);
                    } else {
                        failedCourses.add(courseId);
                    }
                }
            }
        }
        failedCourses.removeAll(passedCourses);
        return failedCourses;
    }

    /** 依据项目统一的“起始年-结束年-学期序号”格式计算学生推荐学期。 */
    private static int recommendedTerm(String currentTerm, int enrollmentYear) {
        String[] parts = currentTerm.split("-");
        if (parts.length != 3) {
            throw new IllegalArgumentException("term must use YYYY-YYYY-N format");
        }
        try {
            int startYear = Integer.parseInt(parts[0]);
            int semester = Integer.parseInt(parts[2]);
            int result = (startYear - enrollmentYear) * 2 + semester;
            if (semester < 1 || semester > 2 || result <= 0) {
                throw new IllegalArgumentException("term is outside student's study period");
            }
            return result;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("term must use YYYY-YYYY-N format");
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static final class StudentRow {
        private final String studentId;
        private final String majorName;
        private final int enrollmentYear;
        private final String status;

        private StudentRow(String studentId, String majorName, int enrollmentYear, String status) {
            this.studentId = studentId;
            this.majorName = majorName;
            this.enrollmentYear = enrollmentYear;
            this.status = status;
        }
    }
}

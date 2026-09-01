package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.student.CourseHistoryRecord;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用临时 Access 数据库验证课程历史和待重修查询。 */
class AccessAcademicReviewServiceTest {
    @TempDir
    Path temporaryDirectory;

    private AccessAcademicReviewService service;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("academic-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblCourse ("
                    + "course_id VARCHAR(32) NOT NULL, course_name VARCHAR(100) NOT NULL,"
                    + "credits INTEGER NOT NULL, capacity INTEGER NOT NULL, PRIMARY KEY (course_id))");
            statement.execute("CREATE TABLE tblCourseResult ("
                    + "result_id VARCHAR(36) NOT NULL, student_id VARCHAR(32) NOT NULL,"
                    + "course_id VARCHAR(32) NOT NULL, offering_id VARCHAR(36), semester VARCHAR(32) NOT NULL,"
                    + "attempt_no INTEGER NOT NULL, attempt_type VARCHAR(16) NOT NULL, score INTEGER,"
                    + "passed BIT NOT NULL, earned_credits INTEGER NOT NULL, recorded_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (result_id))");
            insertCourse(connection, "C001", "Java程序设计", 3);
            insertCourse(connection, "C002", "数据库原理", 3);
            insertResult(connection, "R001", "S001", "C001", "2025-2026-1", 1, "首修", 48, false, 0);
            insertResult(connection, "R002", "S001", "C001", "2025-2026-2", 2, "重修", 76, true, 3);
            insertResult(connection, "R003", "S001", "C002", "2025-2026-1", 1, "首修", 50, false, 0);
        }
        service = new AccessAcademicReviewService(database);
    }

    @Test
    void historyIncludesCourseNameFromCourseCatalog() {
        List<CourseHistoryRecord> history = service.historyFor("S001").getData();

        assertEquals(StatusCode.OK, service.historyFor("S001").getStatus());
        assertEquals(3, history.size());
        assertEquals("Java程序设计", history.get(0).getCourseName());
        assertEquals(2, history.get(2).getAttemptNo());
    }

    @Test
    void pendingRetakesExcludesCourseThatLaterPassed() {
        List<CourseHistoryRecord> pending = service.pendingRetakes("S001").getData();

        assertEquals(StatusCode.OK, service.pendingRetakes("S001").getStatus());
        assertEquals(1, pending.size());
        assertEquals("C002", pending.get(0).getCourseId());
        assertTrue(!pending.get(0).isPassed());
    }

    @Test
    void blankStudentIdReturnsBadRequest() {
        assertEquals(StatusCode.BAD_REQUEST, service.pendingRetakes(" ").getStatus());
    }

    private static void insertCourse(Connection connection, String id, String name, int credits)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourse(course_id,course_name,credits,capacity) VALUES(?,?,?,?)")) {
            statement.setString(1, id);
            statement.setString(2, name);
            statement.setInt(3, credits);
            statement.setInt(4, 40);
            statement.executeUpdate();
        }
    }

    private static void insertResult(Connection connection, String resultId, String studentId,
            String courseId, String semester, int attemptNo, String attemptType, int score,
            boolean passed, int earnedCredits) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourseResult(result_id,student_id,course_id,semester,attempt_no,"
                        + "attempt_type,score,passed,earned_credits,recorded_at) VALUES(?,?,?,?,?,?,?,?,?,NOW())")) {
            statement.setString(1, resultId);
            statement.setString(2, studentId);
            statement.setString(3, courseId);
            statement.setString(4, semester);
            statement.setInt(5, attemptNo);
            statement.setString(6, attemptType);
            statement.setInt(7, score);
            statement.setBoolean(8, passed);
            statement.setInt(9, earnedCredits);
            statement.executeUpdate();
        }
    }
}

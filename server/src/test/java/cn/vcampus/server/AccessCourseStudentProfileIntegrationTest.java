package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Access 学籍数据可以直接驱动选课 V2 的 Token 身份映射。 */
class AccessCourseStudentProfileIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void accessStudentHistoryProducesRetakeRoundForTokenUser() throws Exception {
        Path database = temporaryDirectory.resolve("course-profile.accdb");
        createDatabase(database);

        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials credentials = new UserCredentials(
                "login_001", "Demo123", "张三", Role.STUDENT.name());
        users.register(credentials);
        Session session = users.login(credentials).getData();

        StudentManagementService students = new DefaultStudentManagementService(
                new AccessStudentRepository(database));
        AcademicReviewService reviews = new AccessAcademicReviewService(database);
        StudentSelectionProfileAdapter profiles = new StudentSelectionProfileAdapter(
                students, reviews, CourseSelectionDemoFactory.DEMO_TERM);
        CourseMessageHandler handler = new CourseMessageHandler(
                CourseSelectionDemoFactory.createService(), profiles, users);

        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> rounds = (List<?>) response.getPayload();
        assertTrue(rounds.stream().map(SelectionRound.class::cast)
                .anyMatch(round -> round.getType() == SelectionRoundType.RETAKE));
    }

    private static void createDatabase(Path database) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblStudent ("
                    + "student_id VARCHAR(32) NOT NULL, user_id VARCHAR(32),"
                    + "student_name VARCHAR(64) NOT NULL, gender VARCHAR(8),"
                    + "department_name VARCHAR(64), major_name VARCHAR(64),"
                    + "class_id VARCHAR(32), enrollment_year INTEGER, status VARCHAR(16) NOT NULL,"
                    + "phone VARCHAR(32), email VARCHAR(100), PRIMARY KEY (student_id))");
            statement.execute("CREATE TABLE tblCourse ("
                    + "course_id VARCHAR(32) NOT NULL, course_name VARCHAR(100) NOT NULL,"
                    + "credits INTEGER NOT NULL, capacity INTEGER NOT NULL, PRIMARY KEY (course_id))");
            statement.execute("CREATE TABLE tblCourseResult ("
                    + "result_id VARCHAR(36) NOT NULL, student_id VARCHAR(32) NOT NULL,"
                    + "course_id VARCHAR(32) NOT NULL, offering_id VARCHAR(36), semester VARCHAR(32) NOT NULL,"
                    + "attempt_no INTEGER NOT NULL, attempt_type VARCHAR(16) NOT NULL, score INTEGER,"
                    + "passed BIT NOT NULL, earned_credits INTEGER NOT NULL, recorded_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (result_id))");
            insertStudent(connection);
            insertCourse(connection);
            insertFailedResult(connection);
        }
    }

    private static void insertStudent(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,"
                        + "major_name,class_id,enrollment_year,status,phone,email) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, "STU-001");
            statement.setString(2, "login_001");
            statement.setString(3, "张三");
            statement.setString(4, "男");
            statement.setString(5, "计算机学院");
            statement.setString(6, "软件工程");
            statement.setString(7, "SE2023-01");
            statement.setInt(8, 2023);
            statement.setString(9, "在读");
            statement.setString(10, "");
            statement.setString(11, "");
            statement.executeUpdate();
        }
    }

    private static void insertCourse(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourse(course_id,course_name,credits,capacity) VALUES(?,?,?,?)")) {
            statement.setString(1, "DB101");
            statement.setString(2, "数据库原理");
            statement.setInt(3, 3);
            statement.setInt(4, 40);
            statement.executeUpdate();
        }
    }

    private static void insertFailedResult(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblCourseResult(result_id,student_id,course_id,semester,attempt_no,"
                        + "attempt_type,score,passed,earned_credits,recorded_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,NOW())")) {
            statement.setString(1, "RESULT-001");
            statement.setString(2, "STU-001");
            statement.setString(3, "DB101");
            statement.setString(4, "2025-2026-1");
            statement.setInt(5, 1);
            statement.setString(6, "首修");
            statement.setInt(7, 52);
            statement.setBoolean(8, false);
            statement.setInt(9, 0);
            statement.executeUpdate();
        }
    }
}

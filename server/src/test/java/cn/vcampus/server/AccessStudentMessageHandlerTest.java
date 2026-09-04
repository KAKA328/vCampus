package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentUpdateCommand;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证真实 Access Repository 与 Token/权限 Handler 的组合行为。 */
class AccessStudentMessageHandlerTest {
    @TempDir
    Path temporaryDirectory;

    private StudentMessageHandler handler;
    private InMemoryUserManagementService users;
    private String studentToken;
    private String academicAdminToken;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("student-handler-test.accdb");
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
            insert(connection, "STU-001", "login_001", "张三");
            insert(connection, "STU-002", "login_002", "李四");
        }
        users = new InMemoryUserManagementService();
        UserCredentials student = new UserCredentials("login_001", "Demo123", "张三", Role.STUDENT.name());
        UserCredentials academicAdmin = new UserCredentials(
                "academic_admin", "Demo123", "教务管理员", Role.ACADEMIC_ADMIN.name());
        users.register(student);
        users.register(academicAdmin);
        studentToken = users.login(student).getData().getToken();
        academicAdminToken = users.login(academicAdmin).getData().getToken();
        StudentManagementService service = new DefaultStudentManagementService(
                new AccessStudentRepository(database));
        handler = new StudentMessageHandler(service, users);
    }

    @Test
    void studentTokenResolvesDifferentStudentIdAndCannotReadAnotherProfile() {
        Message own = request(StudentQueryCommand.self(studentToken));
        Message other = request(StudentQueryCommand.byId(studentToken, "STU-002"));

        assertEquals(StatusCode.OK, own.getStatusCode());
        assertEquals("STU-001", ((StudentRecord) own.getPayload()).getStudentId());
        assertEquals(StatusCode.FORBIDDEN, other.getStatusCode());
    }

    @Test
    void academicAdminCanQueryByClassAndMajorAndUpdateStatus() {
        assertEquals(StatusCode.OK, request(
                StudentQueryCommand.byClass(academicAdminToken, "SE2023-01")).getStatusCode());
        assertEquals(StatusCode.OK, request(
                StudentQueryCommand.byMajor(academicAdminToken, "软件工程")).getStatusCode());

        StudentRecord changed = new StudentRecord("STU-001", "login_001", "张三", "男",
                "计算机学院", "软件工程", "SE2023-01", 2023, "休学", "", "");
        Message update = Message.request("update", MessageType.STUDENT_UPDATE,
                new StudentUpdateCommand(academicAdminToken, changed));
        assertEquals(StatusCode.OK, handler.handle(update).getStatusCode());
        assertEquals("休学", ((StudentRecord) request(
                StudentQueryCommand.byId(academicAdminToken, "STU-001"))
                .getPayload()).getStatus());
    }

    private Message request(StudentQueryCommand command) {
        return requestPayload(command);
    }

    private Message requestPayload(Object payload) {
        return handler.handle(Message.request("query", MessageType.STUDENT_QUERY, payload));
    }

    private static void insert(Connection connection, String studentId, String userId, String name)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblStudent(student_id,user_id,student_name,gender,department_name,"
                        + "major_name,class_id,enrollment_year,status,phone,email) VALUES(?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, studentId);
            statement.setString(2, userId);
            statement.setString(3, name);
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
}

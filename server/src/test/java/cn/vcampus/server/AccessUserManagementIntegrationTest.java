package cn.vcampus.server;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.AuditEvent;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.user.UserRoleChangeCommand;
import cn.vcampus.user.UserStatusCommand;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用全新 Access 数据库验证用户管理的真实读写和跨服务实例持久化。 */
class AccessUserManagementIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    private Path database;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("user-management.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        executeScript(readScript("database/schema.sql"));
        executeScript(readScript("database/seed.sql"));
        insertUnboundProfile("S-ACCESS-001", "Access Student");
        insertUnboundProfile("S-ACCESS-002", "Another Student");
        insertUnboundTeacher("T-ACCESS-001", "Access Teacher");
    }

    @Test
    void accountWithExistingProfilePersistsAndCanLoginAfterServiceReopen() {
        UserManagementService first = accessService();
        ServiceResult<Void> created = first.register(new UserCredentials(
                "access_student", "Access123", "Access Student", Role.STUDENT.name(), "S-ACCESS-001"));

        assertEquals(StatusCode.OK, created.getStatus());
        assertNotNull(first.login(new UserCredentials(
                "access_student", "Access123", "ignored", Role.STUDENT.name())).getData());

        UserManagementService reopened = accessService();
        ServiceResult<Session> login = reopened.login(new UserCredentials(
                "access_student", "Access123", "ignored", Role.STUDENT.name()));
        assertEquals(StatusCode.OK, login.getStatus());
        assertEquals("S-ACCESS-001",
                new AccessProfileBindingRepository(database).findProfileId(Role.STUDENT, "access_student"));
    }

    @Test
    void studentAccountWithoutExistingProfileIsRejectedAndNotStored() throws Exception {
        UserManagementService service = accessService();

        ServiceResult<Void> result = service.register(new UserCredentials(
                "access_missing_profile", "Access123", "Missing Profile", Role.STUDENT.name(),
                "S-NOT-EXIST"));

        assertEquals(StatusCode.BAD_REQUEST, result.getStatus());
        assertEquals(0, countWhere("tblUser", "user_id", "access_missing_profile"));
    }

    @Test
    void boundStudentAndTeacherAccountsCannotChangeToAnotherRole() throws Exception {
        UserManagementService service = accessService();
        assertEquals(StatusCode.OK, service.register(new UserCredentials(
                "access_bound_student", "Access123", "Bound Student", Role.STUDENT.name(), "S-ACCESS-002"))
                .getStatus());
        assertEquals(StatusCode.OK, service.register(new UserCredentials(
                "access_bound_teacher", "Access123", "Bound Teacher", Role.TEACHER.name(), "T-ACCESS-001"))
                .getStatus());
        Session admin = service.login(new UserCredentials(
                "demo_admin", "Demo123", "ignored", Role.ADMIN.name())).getData();

        assertEquals(StatusCode.FORBIDDEN, service.changeUserRole(new UserRoleChangeCommand(
                admin.getToken(), "access_bound_student", Role.TEACHER.name())).getStatus());
        assertEquals(StatusCode.FORBIDDEN, service.changeUserRole(new UserRoleChangeCommand(
                admin.getToken(), "access_bound_teacher", Role.ADMIN.name())).getStatus());
        assertEquals(Role.STUDENT, accountRole("access_bound_student"));
        assertEquals(Role.TEACHER, accountRole("access_bound_teacher"));
    }

    @Test
    void disablingAccessAccountBlocksLoginAndPersists() throws Exception {
        UserManagementService service = accessService();
        assertEquals(StatusCode.OK, service.register(new UserCredentials(
                "access_disable", "Access123", "Disable Me", Role.ADMIN.name())).getStatus());
        Session admin = service.login(new UserCredentials(
                "demo_admin", "Demo123", "ignored", Role.ADMIN.name())).getData();

        assertEquals(StatusCode.OK, service.setAccountActive(
                new UserStatusCommand(admin.getToken(), "access_disable", false)).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, accessService().login(new UserCredentials(
                "access_disable", "Access123", "ignored", Role.ADMIN.name())).getStatus());
        assertFalse(accountActive("access_disable"));
    }

    @Test
    void loginAndLogoutAreRecordedInAccessAuditLog() throws Exception {
        UserManagementService service = accessService();
        ServiceResult<Session> login = service.login(new UserCredentials(
                "demo_admin", "Demo123", "ignored", Role.ADMIN.name()));
        assertEquals(StatusCode.OK, login.getStatus());
        assertEquals(StatusCode.OK, service.logout(login.getData().getToken()).getStatus());

        List<AuditEvent> events = new AccessAuditLogRepository(database).findAll();
        assertTrue(events.stream().anyMatch(event -> "LOGIN".equals(event.getAction())
                && "demo_admin".equals(event.getActorUserId())
                && "demo_admin".equals(event.getTargetId())));
        assertTrue(events.stream().anyMatch(event -> "LOGOUT".equals(event.getAction())
                && "demo_admin".equals(event.getActorUserId())
                && "demo_admin".equals(event.getTargetId())));
    }

    private UserManagementService accessService() {
        return UserServiceFactory.create(new String[] {"--db", database.toString()});
    }

    private void insertUnboundProfile(String studentId, String name) throws SQLException {
        try (Connection connection = open();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO tblStudent(student_id,user_id,student_name,status) VALUES(?,?,?,?)")) {
            statement.setString(1, studentId);
            statement.setNull(2, java.sql.Types.VARCHAR);
            statement.setString(3, name);
            statement.setString(4, "在读");
            statement.executeUpdate();
        }
    }

    private void insertUnboundTeacher(String teacherId, String name) throws SQLException {
        try (Connection connection = open();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO tblTeacher(teacher_id,user_id,teacher_name) VALUES(?,?,?)")) {
            statement.setString(1, teacherId);
            statement.setNull(2, java.sql.Types.VARCHAR);
            statement.setString(3, name);
            statement.executeUpdate();
        }
    }

    private Role accountRole(String userId) throws SQLException {
        try (Connection connection = open();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT role_code FROM tblUser WHERE user_id=?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return Role.valueOf(result.getString(1));
            }
        }
    }

    private boolean accountActive(String userId) throws SQLException {
        try (Connection connection = open();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT active FROM tblUser WHERE user_id=?")) {
            statement.setString(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getBoolean(1);
            }
        }
    }

    private int countWhere(String table, String column, String value) throws SQLException {
        try (Connection connection = open();
             java.sql.PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private String readScript(String file) throws IOException {
        return new String(Files.readAllBytes(repositoryRoot().resolve(file)), StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        Path current = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("database").resolve("schema.sql"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }

    private void executeScript(String script) throws SQLException, IOException {
        StringBuilder withoutComments = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(script));
        String line;
        while ((line = reader.readLine()) != null) {
            int commentStart = line.indexOf("--");
            if (commentStart >= 0) {
                line = line.substring(0, commentStart);
            }
            withoutComments.append(line).append('\n');
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
             Statement statement = connection.createStatement()) {
            for (String sql : withoutComments.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    try {
                        statement.execute(sql.trim());
                    } catch (SQLException failure) {
                        throw new SQLException("failed SQL: " + sql.trim(), failure);
                    }
                }
            }
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
    }
}

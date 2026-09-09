package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.server.AccessAuditLogRepository;
import cn.vcampus.server.AccessPasswordResetApplicationRepository;
import cn.vcampus.server.AccessProfileBindingRepository;
import cn.vcampus.server.AccessStudentRepository;
import cn.vcampus.server.AccessTeacherRepository;
import cn.vcampus.server.AccessUserRepository;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StudentTestDataImportIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test void suppliedSqlAndCsvCreateLoginReadyProfiles() throws Exception {
        Path root = repositoryRoot();
        Path database = temporaryDirectory.resolve("student-import.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        execute(database, root.resolve("database/schema.sql"), true);
        execute(database, root.resolve("database/seed.sql"), false);
        execute(database, root.resolve("database/student-test-data.sql"), false);

        List<UserImportRow> rows = new UserImportFileReader().read(
                root.resolve("test-data/学籍账号批量导入示例.csv"));
        assertEquals(3, rows.size());
        DefaultUserManagementService users = new DefaultUserManagementService(
                new AccessUserRepository(database), new SessionManager(),
                new AccessAuditLogRepository(database),
                new AccessPasswordResetApplicationRepository(database),
                new AccessProfileBindingRepository(database));
        Session admin = users.login(new UserCredentials(
                "demo_admin", "Demo123", "ignored", Role.ADMIN.name())).getData();
        assertNotNull(admin);

        ServiceResult<UserImportResult> imported = users.importUsers(admin.getToken(), rows);
        assertEquals(StatusCode.OK, imported.getStatus());
        assertEquals(3, imported.getData().getSuccessCount());
        assertEquals(0, imported.getData().getFailureCount());

        assertStudent(users, database, "student_enrolled_01", "test_student_enrolled");
        assertStudent(users, database, "student_leave_01", "test_student_leave");
        assertTeacher(users, database, "teacher_active_01", "test_teacher_active");

        ServiceResult<UserImportResult> repeated = users.importUsers(admin.getToken(), rows);
        assertEquals(StatusCode.OK, repeated.getStatus());
        assertEquals(0, repeated.getData().getSuccessCount());
        assertEquals(3, repeated.getData().getFailureCount());
    }

    private static void assertStudent(DefaultUserManagementService users, Path database,
            String userId, String expectedStudentId) {
        assertEquals(StatusCode.OK, users.login(new UserCredentials(
                userId, "Test123", "ignored", Role.STUDENT.name())).getStatus());
        ServiceResult<StudentRecord> profile = new DefaultStudentManagementService(
                new AccessStudentRepository(database)).findMyStudentProfile(userId);
        assertEquals(StatusCode.OK, profile.getStatus());
        assertEquals(expectedStudentId, profile.getData().getStudentId());
    }

    private static void assertTeacher(DefaultUserManagementService users, Path database,
            String userId, String expectedTeacherId) {
        assertEquals(StatusCode.OK, users.login(new UserCredentials(
                userId, "Test123", "ignored", Role.TEACHER.name())).getStatus());
        TeacherProfile profile = new AccessTeacherRepository(database).findByUserId(userId);
        assertNotNull(profile);
        assertEquals(expectedTeacherId, profile.getTeacherId());
    }

    private static void execute(Path database, Path script, boolean create) throws Exception {
        String url = "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true"
                + (create ? ";newDatabaseVersion=V2010" : "");
        String sql = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
        StringBuilder withoutComments = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(sql))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf("--");
                withoutComments.append(comment < 0 ? line : line.substring(0, comment)).append('\n');
            }
        }
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            for (String command : withoutComments.toString().split(";")) {
                if (!command.trim().isEmpty()) statement.execute(command.trim());
            }
        }
    }

    private static Path repositoryRoot() {
        Path current = java.nio.file.Paths.get("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("database/schema.sql"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}

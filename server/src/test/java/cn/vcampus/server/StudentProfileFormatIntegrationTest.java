package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.nio.file.Path;
import java.time.Year;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StudentProfileFormatIntegrationTest {
    @TempDir Path directory;
    @Test void memoryRejectsInvalidWritesForBothRoles() throws Exception { verify(false); }
    @Test void accessRejectsInvalidWritesForBothRoles() throws Exception { verify(true); }

    private void verify(boolean access) throws Exception {
        StudentRepository repository;
        if (access) {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
            Path database = directory.resolve("format.accdb");
            AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
            repository = new AccessStudentRepository(database);
        } else repository = new InMemoryStudentRepository();
        StudentRecord original = row("学生", "男", 2026, "在读", "13800000000", "old@example.com");
        repository.save(original);
        DefaultStudentManagementService service = new DefaultStudentManagementService(repository);
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        StudentMessageHandler handler = new StudentMessageHandler(service, users);
        String student = login(users, "student", Role.STUDENT);
        String admin = login(users, "academic", Role.ACADEMIC_ADMIN);
        for (String token : new String[] {student, admin}) {
            for (String phone : new String[] {"123", "1380000000a", "138000000000", "１２３４５６７８９０１"}) {
                Message result = update(handler, token, StudentProfileSnapshot.withContacts(original, phone, original.getEmail()));
                assertEquals(StatusCode.BAD_REQUEST, result.getStatusCode());
                assertTrue(String.valueOf(result.getPayload()).contains("11位"));
                assertTrue(StudentProfileSnapshot.matches(original, repository.findById("S001")));
            }
            assertEquals(StatusCode.BAD_REQUEST, update(handler, token,
                    StudentProfileSnapshot.withContacts(original, original.getPhone(), "not-email")).getStatusCode());
        }
        for (StudentRecord invalid : new StudentRecord[] {
                row("学生", "男", 2026, "任意状态", original.getPhone(), original.getEmail()),
                row("", "男", 2026, "在读", original.getPhone(), original.getEmail()),
                row("学生", "任意性别", 2026, "在读", original.getPhone(), original.getEmail()),
                row("学生", "男", Year.now().getValue() + 2, "在读", original.getPhone(), original.getEmail()),
                row(String.join("", java.util.Collections.nCopies(65, "字")), "男", 2026, "在读", original.getPhone(), original.getEmail())}) {
            assertEquals(StatusCode.BAD_REQUEST, update(handler, admin, invalid).getStatusCode());
            assertTrue(StudentProfileSnapshot.matches(original, repository.findById("S001")));
        }
        assertEquals(StatusCode.FORBIDDEN, update(handler, admin,
                row("学生", "男", 2026, "毕业", original.getPhone(), original.getEmail())).getStatusCode());
        assertEquals(StatusCode.OK, update(handler, admin,
                row("学生", "女", 2026, "休学", "13900000000", "new@example.com")).getStatusCode());
        StudentRecord paused = repository.findById("S001");
        assertEquals("休学", paused.getStatus());
        assertEquals(StatusCode.OK, update(handler, student,
                StudentProfileSnapshot.withContacts(paused, "13700000000", null)).getStatusCode());
        assertEquals("13700000000", repository.findById("S001").getPhone());
        assertEquals("休学", repository.findById("S001").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.updateContacts("student", repository.findById("S001"), "abc", null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.saveIfUnchanged(
                row("学生", "男", 2026, "INVALID", null, null), repository.findById("S001")).getStatus());
    }
    private static Message update(StudentMessageHandler handler, String token, StudentRecord row) {
        return handler.handle(Message.request("edit", MessageType.STUDENT_UPDATE, new StudentUpdateCommand(token, row)));
    }
    private static String login(InMemoryUserManagementService service, String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", "测试", role.name());
        service.register(credentials); return service.login(credentials).getData().getToken();
    }
    private static StudentRecord row(String name, String gender, int year, String state, String phone, String email) {
        return new StudentRecord("S001", "student", name, gender, "院系", "专业", "班级", year, state, phone, email);
    }
}

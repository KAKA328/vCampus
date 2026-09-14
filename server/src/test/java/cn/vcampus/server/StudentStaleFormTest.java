package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StudentStaleFormTest {
    @TempDir Path directory;

    @Test void memoryRejectsStaleForms() throws Exception { verify(false); }
    @Test void accessRejectsStaleForms() throws Exception { verify(true); }

    private void verify(boolean access) throws Exception {
        StudentRepository repository;
        if (access) {
            Path database = directory.resolve("stale.accdb");
            AccessDatabaseSchemaTest.executeScript(database, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
            repository = new AccessStudentRepository(database);
        } else repository = new InMemoryStudentRepository();
        StudentRecord original = row("Original", "13800000000");
        repository.save(original);
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        String admin = login(users, "admin_a", Role.ADMIN);
        String otherAdmin = login(users, "admin_b", Role.ACADEMIC_ADMIN);
        String student = login(users, "student_a", Role.STUDENT);
        String otherStudent = login(users, "student_b", Role.STUDENT);
        String teacher = login(users, "teacher_a", Role.TEACHER);
        StudentMessageHandler handler = new StudentMessageHandler(new DefaultStudentManagementService(repository), users);
        StudentRecord changed = row("ChangedByA", "13800000000");
        assertEquals(StatusCode.OK, save(handler, admin, changed, original).getStatusCode());
        Message stale = save(handler, otherAdmin, row("Original", "13800000001"), original);
        assertEquals(StatusCode.CONFLICT, stale.getStatusCode());
        assertTrue(stale.getPayload().toString().contains("重新加载"));
        assertTrue(StudentProfileSnapshot.matches(changed, repository.findById("S001")));
        assertEquals(StatusCode.CONFLICT, save(handler, student, row("Original", "13800000002"), original).getStatusCode());

        StudentRecord refreshed = repository.findById("S001");
        StudentRecord phoneChanged = StudentProfileSnapshot.withContacts(refreshed, "13800000003", null);
        assertEquals(StatusCode.OK, save(handler, student, phoneChanged, refreshed).getStatusCode());
        assertEquals(StatusCode.CONFLICT, save(handler, student,
                StudentProfileSnapshot.withContacts(refreshed, "13800000004", null), refreshed).getStatusCode());
        assertTrue(StudentProfileSnapshot.matches(phoneChanged, repository.findById("S001")));
        assertEquals(StatusCode.FORBIDDEN, save(handler, otherStudent, phoneChanged, phoneChanged).getStatusCode());
        assertEquals(StatusCode.FORBIDDEN, save(handler, teacher, phoneChanged, phoneChanged).getStatusCode());
        assertEquals(StatusCode.FORBIDDEN, save(handler, student, row("Forged", "13800000003"), phoneChanged).getStatusCode());
        assertEquals(StatusCode.UNAUTHORIZED, save(handler, "invalid", phoneChanged, phoneChanged).getStatusCode());
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("malformed",
                MessageType.STUDENT_UPDATE_V2, new StudentUpdateCommand(admin, phoneChanged))).getStatusCode());

        for (String token : new String[] {admin, student}) {
            Message legacy = handler.handle(Message.request("legacy", MessageType.STUDENT_UPDATE,
                    new StudentUpdateCommand(token, phoneChanged)));
            assertEquals(StatusCode.CONFLICT, legacy.getStatusCode());
            assertTrue(legacy.getPayload().toString().contains("升级"));
        }
        assertTrue(StudentProfileSnapshot.matches(phoneChanged, repository.findById("S001")));
        StudentRecord graduated = new StudentRecord("S001", "student_a", "ChangedByA", "男",
                "Engineering", "Software", "SE2024-01", 2024, "毕业", "13800000003", null);
        assertEquals(StatusCode.FORBIDDEN, save(handler, admin, graduated, phoneChanged).getStatusCode());
        repository.save(graduated);
        assertEquals(StatusCode.CONFLICT, save(handler, otherAdmin, phoneChanged, phoneChanged).getStatusCode());
        assertEquals("毕业", repository.findById("S001").getStatus());
    }

    private static Message save(StudentMessageHandler handler, String token, StudentRecord record, StudentRecord expected) {
        return handler.handle(Message.request("save", MessageType.STUDENT_UPDATE_V2,
                new StudentUpdateV2Command(token, record, expected)));
    }
    private static String login(InMemoryUserManagementService users, String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", "Review", role.name());
        assertEquals(StatusCode.OK, users.register(credentials).getStatus());
        return users.login(credentials).getData().getToken();
    }
    private static StudentRecord row(String name, String phone) {
        return new StudentRecord("S001", "student_a", name, "男", "Engineering", "Software",
                "SE2024-01", 2024, "在读", phone, null);
    }
}

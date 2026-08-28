package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserRepositoryBackedServiceTest {
    @Test
    void registeredAccountCanBeUsedByAnotherServiceInstance() {
        UserRepository users = new InMemoryUserRepository();
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        UserManagementService first = new DefaultUserManagementService(users, new SessionManager(), auditLog);
        UserManagementService second = new DefaultUserManagementService(users, new SessionManager(), auditLog);
        UserCredentials credentials = new UserCredentials("repo001", "repo001", "Repo User", Role.STUDENT.name());

        assertEquals(StatusCode.OK, first.register(credentials).getStatus());

        assertEquals(StatusCode.OK, second.login(credentials).getStatus());
    }

    @Test
    void adminCanUnregisterAnotherUserAndAuditIsRecorded() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        UserCredentials student = new UserCredentials("stu001", "stu001", "Student", Role.STUDENT.name());
        service.register(student);
        Session adminSession = sessions.create(new User("admin001", "Admin", Role.ADMIN));

        assertEquals(StatusCode.OK, service.unregister(student.getUserId(), adminSession.getToken()).getStatus());

        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());
        assertEquals(1, auditLog.findAll().size());
        assertEquals("UNREGISTER_USER", auditLog.findAll().get(0).getAction());
        assertEquals("admin001", auditLog.findAll().get(0).getActorUserId());
        assertEquals(student.getUserId(), auditLog.findAll().get(0).getTargetId());
    }

    @Test
    void nonAdminStillCannotUnregisterAnotherUser() {
        UserManagementService service = new InMemoryUserManagementService();
        UserCredentials first = new UserCredentials("stu002", "stu002", "Student A", Role.STUDENT.name());
        UserCredentials second = new UserCredentials("stu003", "stu003", "Student B", Role.STUDENT.name());
        service.register(first);
        service.register(second);
        Session firstSession = service.login(first).getData();

        assertEquals(StatusCode.UNAUTHORIZED, service.unregister(second.getUserId(), firstSession.getToken()).getStatus());
    }

    @Test
    void adminCanImportUsersAndImporterMetadataIsRecorded() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session adminSession = sessions.create(new User("admin002", "Admin", Role.ADMIN));

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("imp_stu001", "Demo123", "Imported Student", Role.STUDENT.name()),
                new UserImportRow("imp_tch001", "Demo123", "Imported Teacher", Role.TEACHER.name())));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(2, result.getData().getSuccessCount());
        assertEquals(0, result.getData().getFailureCount());
        assertFalse(result.getData().getImportBatchId().trim().isEmpty());

        UserAccount imported = users.findById("imp_stu001");
        assertNotNull(imported);
        assertEquals("admin002", imported.getCreatedBy());
        assertEquals(result.getData().getImportBatchId(), imported.getImportBatchId());
        assertNotNull(imported.getCreatedAt());
        assertEquals(StatusCode.OK, service.login(
                new UserCredentials("imp_stu001", "Demo123", "Imported Student", Role.STUDENT.name())).getStatus());

        assertEquals(2, auditLog.findAll().size());
        assertEquals("IMPORT_USER", auditLog.findAll().get(0).getAction());
        assertEquals("admin002", auditLog.findAll().get(0).getActorUserId());
        assertEquals("imp_stu001", auditLog.findAll().get(0).getTargetId());
    }

    @Test
    void importKeepsValidRowsWhenAnotherRowFails() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session adminSession = sessions.create(new User("admin003", "Admin", Role.ADMIN));
        assertEquals(StatusCode.OK, service.register(
                new UserCredentials("dup001", "Demo123", "Duplicate", Role.STUDENT.name())).getStatus());

        ServiceResult<UserImportResult> result = service.importUsers(adminSession.getToken(), Arrays.asList(
                new UserImportRow("dup001", "Demo123", "Duplicate Again", Role.STUDENT.name()),
                new UserImportRow("imp_ok001", "Demo123", "Imported OK", Role.STUDENT.name())));

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(2, result.getData().getTotalCount());
        assertEquals(1, result.getData().getSuccessCount());
        assertEquals(1, result.getData().getFailureCount());
        assertEquals(1, result.getData().getFailures().size());
        assertEquals(1, result.getData().getFailures().get(0).getRowNumber());
        assertEquals("dup001", result.getData().getFailures().get(0).getUserId());
        assertNotNull(users.findById("imp_ok001"));
        assertEquals(1, auditLog.findAll().size());
        assertEquals("imp_ok001", auditLog.findAll().get(0).getTargetId());
    }

    @Test
    void nonAdminCannotImportUsers() {
        InMemoryUserRepository users = new InMemoryUserRepository();
        InMemoryAuditLogRepository auditLog = new InMemoryAuditLogRepository();
        SessionManager sessions = new SessionManager();
        UserManagementService service = new DefaultUserManagementService(users, sessions, auditLog);
        Session studentSession = sessions.create(new User("stu004", "Student", Role.STUDENT));

        ServiceResult<UserImportResult> result = service.importUsers(studentSession.getToken(), Arrays.asList(
                new UserImportRow("blocked001", "Demo123", "Blocked", Role.STUDENT.name())));

        assertEquals(StatusCode.FORBIDDEN, result.getStatus());
        assertEquals(null, users.findById("blocked001"));
        assertEquals(0, auditLog.findAll().size());
    }

    @Test
    void emptyImportReturnsBadRequest() {
        UserManagementService service = new InMemoryUserManagementService();

        ServiceResult<UserImportResult> result = service.importUsers("token", Arrays.<UserImportRow>asList());

        assertEquals(StatusCode.BAD_REQUEST, result.getStatus());
    }
}

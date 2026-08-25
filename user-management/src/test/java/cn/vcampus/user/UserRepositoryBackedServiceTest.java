package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        UserManagementService service = new DefaultUserManagementService(users, new SessionManager(), auditLog);
        UserCredentials admin = new UserCredentials("admin001", "admin1", "Admin", Role.ADMIN.name());
        UserCredentials student = new UserCredentials("stu001", "stu001", "Student", Role.STUDENT.name());
        service.register(admin);
        service.register(student);
        Session adminSession = service.login(admin).getData();

        assertEquals(StatusCode.OK, service.unregister(student.getUserId(), adminSession.getToken()).getStatus());

        assertEquals(StatusCode.UNAUTHORIZED, service.login(student).getStatus());
        assertEquals(1, auditLog.findAll().size());
        assertEquals("UNREGISTER_USER", auditLog.findAll().get(0).getAction());
        assertEquals(admin.getUserId(), auditLog.findAll().get(0).getActorUserId());
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
}

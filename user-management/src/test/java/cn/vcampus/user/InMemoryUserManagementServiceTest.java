package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryUserManagementServiceTest {
    private final InMemoryUserManagementService service = new InMemoryUserManagementService();

    @Test
    void loginReturnsSessionAndLogoutInvalidatesToken() {
        UserCredentials credentials = new UserCredentials("u001", "p00100", "Student", Role.STUDENT.name());
        assertEquals(StatusCode.OK, service.register(credentials).getStatus());

        Session session = service.login(credentials).getData();
        assertNotNull(session);
        assertEquals(StatusCode.OK, service.logout(session.getToken()).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, service.logout(session.getToken()).getStatus());
    }

    @Test
    void duplicateRegistrationIsRejected() {
        UserCredentials credentials = new UserCredentials("u002", "p00200", "Student", Role.STUDENT.name());
        service.register(credentials);
        assertEquals(StatusCode.CONFLICT, service.register(credentials).getStatus());
    }

    @Test
    void unknownUserAndWrongPasswordUseSameLoginFailure() {
        UserCredentials credentials = new UserCredentials("u003", "p00300", "Student", Role.STUDENT.name());
        assertEquals(StatusCode.OK, service.register(credentials).getStatus());

        ServiceResult<Session> unknownUser = service.login(
                new UserCredentials("missing", "p00300", "Missing", Role.STUDENT.name()));
        ServiceResult<Session> wrongPassword = service.login(
                new UserCredentials("u003", "bad003", "Student", Role.STUDENT.name()));

        assertEquals(StatusCode.UNAUTHORIZED, unknownUser.getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, wrongPassword.getStatus());
        assertEquals(unknownUser.getMessage(), wrongPassword.getMessage());
    }

    @Test
    void cannotUseOneUsersTokenToUnregisterAnotherUser() {
        UserCredentials first = new UserCredentials("u004a", "p00400", "First", Role.STUDENT.name());
        UserCredentials second = new UserCredentials("u004b", "p00401", "Second", Role.STUDENT.name());
        service.register(first);
        service.register(second);

        Session firstSession = service.login(first).getData();
        assertEquals(StatusCode.UNAUTHORIZED, service.unregister(second.getUserId(), firstSession.getToken()).getStatus());
        assertEquals(StatusCode.OK, service.login(second).getStatus());
    }

    @Test
    void unregisterRemovesAccountAndInvalidatesAllSessionsForUser() {
        UserCredentials credentials = new UserCredentials("u005", "p00500", "Student", Role.STUDENT.name());
        service.register(credentials);
        Session firstSession = service.login(credentials).getData();
        Session secondSession = service.login(credentials).getData();

        assertEquals(StatusCode.OK, service.unregister(credentials.getUserId(), firstSession.getToken()).getStatus());

        assertEquals(StatusCode.UNAUTHORIZED, service.login(credentials).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED,
                service.authorize(secondSession.getToken(), Permission.USER_SELF_READ.getCode()).getStatus());
    }

    @Test
    void invalidRoleCodeRegistrationIsRejected() {
        UserCredentials credentials = new UserCredentials("u006", "p00600", "Invalid", "NOT_A_ROLE");
        assertEquals(StatusCode.BAD_REQUEST, service.register(credentials).getStatus());
    }

    @Test
    void rolePermissionPolicyAllowsAndRejectsExpectedPermissions() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertAllAllowed(policy, Role.ADMIN, Permission.values());
        assertAllAllowed(policy, Role.STUDENT,
                Permission.USER_SELF_READ,
                Permission.COURSE_READ,
                Permission.COURSE_SELECT,
                Permission.LIBRARY_READ,
                Permission.LIBRARY_BORROW,
                Permission.STORE_READ,
                Permission.STORE_PURCHASE);
        assertAllRejected(policy, Role.STUDENT,
                Permission.USER_MANAGE,
                Permission.STUDENT_WRITE,
                Permission.LIBRARY_MANAGE,
                Permission.STORE_MANAGE);

        assertAllAllowed(policy, Role.TEACHER,
                Permission.USER_SELF_READ,
                Permission.STUDENT_READ,
                Permission.COURSE_READ);
        assertAllRejected(policy, Role.TEACHER,
                Permission.USER_MANAGE,
                Permission.STORE_MANAGE);

        assertAllAllowed(policy, Role.LIBRARIAN,
                Permission.LIBRARY_READ,
                Permission.LIBRARY_MANAGE);
        assertAllRejected(policy, Role.LIBRARIAN,
                Permission.USER_MANAGE,
                Permission.STORE_MANAGE);

        assertAllAllowed(policy, Role.STORE_MANAGER,
                Permission.STORE_READ,
                Permission.STORE_MANAGE);
        assertAllRejected(policy, Role.STORE_MANAGER,
                Permission.USER_MANAGE,
                Permission.LIBRARY_MANAGE);
    }

    @Test
    void authorizeUsesRolePermissionPolicyAndDistinguishesInvalidPermissionCodes() {
        UserCredentials student = new UserCredentials("u007", "p00700", "Student", Role.STUDENT.name());
        service.register(student);
        Session session = service.login(student).getData();

        assertEquals(StatusCode.OK, service.authorize(session.getToken(), Permission.COURSE_SELECT.getCode()).getStatus());
        assertEquals(StatusCode.FORBIDDEN, service.authorize(session.getToken(), Permission.USER_MANAGE.getCode()).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.authorize(session.getToken(), "unknown:permission").getStatus());
    }

    private static void assertAllAllowed(RolePermissionPolicy policy, Role role, Permission... permissions) {
        for (Permission permission : permissions) {
            assertTrue(policy.isAllowed(role, permission), role + " should allow " + permission);
        }
    }

    private static void assertAllRejected(RolePermissionPolicy policy, Role role, Permission... permissions) {
        for (Permission permission : permissions) {
            assertFalse(policy.isAllowed(role, permission), role + " should reject " + permission);
        }
    }
}

package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
    void secondLoginForSameUserInvalidatesFirstSession() {
        UserCredentials credentials = new UserCredentials("u001b", "p00100", "Student", Role.STUDENT.name());
        assertEquals(StatusCode.OK, service.register(credentials).getStatus());

        ServiceResult<Session> first = service.login(credentials);
        ServiceResult<Session> second = service.login(credentials);

        assertEquals(StatusCode.OK, first.getStatus());
        assertEquals(StatusCode.OK, second.getStatus());
        assertEquals(StatusCode.UNAUTHORIZED,
                service.currentSession(first.getData().getToken()).getStatus());
        assertEquals(StatusCode.OK,
                service.currentSession(second.getData().getToken()).getStatus());
    }

    @Test
    void userCanLoginAgainAfterLogout() {
        UserCredentials credentials = new UserCredentials("u001c", "p00100", "Student", Role.STUDENT.name());
        assertEquals(StatusCode.OK, service.register(credentials).getStatus());
        Session session = service.login(credentials).getData();

        assertEquals(StatusCode.OK, service.logout(session.getToken()).getStatus());

        assertEquals(StatusCode.OK, service.login(credentials).getStatus());
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
    void unregisterRemovesAccountAndInvalidatesActiveSessionForUser() {
        UserCredentials credentials = new UserCredentials("u005", "p00500", "Student", Role.STUDENT.name());
        service.register(credentials);
        Session session = service.login(credentials).getData();

        assertEquals(StatusCode.OK, service.unregister(credentials.getUserId(), session.getToken()).getStatus());

        assertEquals(StatusCode.UNAUTHORIZED, service.login(credentials).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED,
                service.authorize(session.getToken(), Permission.USER_SELF_READ.getCode()).getStatus());
    }

    @Test
    void invalidRoleCodeRegistrationIsRejected() {
        UserCredentials credentials = new UserCredentials("u006", "p00600", "Invalid", "NOT_A_ROLE");
        assertEquals(StatusCode.BAD_REQUEST, service.register(credentials).getStatus());
    }

    @Test
    void registrationAcceptsAllDefinedRoles() {
        for (Role role : Role.values()) {
            UserCredentials credentials = new UserCredentials(
                    "role_" + role.name().toLowerCase(), "role001", role.name(), role.name());
            assertEquals(StatusCode.OK, service.register(credentials).getStatus(), role.name());
            assertNotNull(service.login(credentials).getData(), role.name());
        }
    }

    @Test
    void currentSessionReturnsAuthenticatedUserAndRejectsInvalidToken() {
        UserCredentials credentials = new UserCredentials("u008", "p00800", "Student", Role.STUDENT.name());
        service.register(credentials);
        Session session = service.login(credentials).getData();

        ServiceResult<Session> current = service.currentSession(session.getToken());

        assertEquals(StatusCode.OK, current.getStatus());
        assertEquals("u008", current.getData().getUser().getUserId());
        assertEquals(StatusCode.UNAUTHORIZED, service.currentSession("missing-token").getStatus());
    }

    @Test
    void studentPermissionSetMatchesApprovedMatrix() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertExactPermissions(policy, Role.STUDENT,
                "USER_SELF_READ", "STUDENT_READ", "COURSE_READ", "COURSE_SELECT",
                "LIBRARY_READ", "LIBRARY_BORROW", "STORE_READ", "STORE_PURCHASE");
    }

    @Test
    void teacherPermissionSetMatchesApprovedMatrix() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertExactPermissions(policy, Role.TEACHER,
                "USER_SELF_READ", "STUDENT_READ", "COURSE_READ", "GRADE_WRITE",
                "LIBRARY_READ", "LIBRARY_BORROW", "STORE_READ", "STORE_PURCHASE");
    }

    @Test
    void academicAdminPermissionSetMatchesApprovedMatrix() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertExactPermissions(policy, Role.valueOf("ACADEMIC_ADMIN"),
                "STUDENT_READ", "STUDENT_WRITE", "COURSE_READ", "COURSE_MANAGE",
                "ACADEMIC_REVIEW");
    }

    @Test
    void librarianPermissionSetMatchesApprovedMatrix() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertExactPermissions(policy, Role.LIBRARIAN,
                "LIBRARY_READ", "LIBRARY_BORROW", "LIBRARY_MANAGE");
    }

    @Test
    void storeManagerPermissionSetMatchesApprovedMatrix() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertExactPermissions(policy, Role.STORE_MANAGER,
                "STORE_READ", "STORE_PURCHASE", "STORE_MANAGE");
    }

    @Test
    void systemAdminRetainsEveryPermission() {
        RolePermissionPolicy policy = new RolePermissionPolicy();

        assertAllAllowed(policy, Role.ADMIN, Permission.values());
    }

    @Test
    void publicPermissionCodesIncludeAcademicCourseAndGradeOperations() {
        assertNotNull(Permission.fromCode("COURSE_MANAGE"));
        assertNotNull(Permission.fromCode("GRADE_WRITE"));
        assertNotNull(Permission.fromCode("ACADEMIC_REVIEW"));
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

    private static void assertExactPermissions(RolePermissionPolicy policy, Role role, String... permissionNames) {
        Set<String> expected = new HashSet<String>(Arrays.asList(permissionNames));
        for (Permission permission : Permission.values()) {
            assertEquals(expected.contains(permission.name()), policy.isAllowed(role, permission),
                    role + " permission mismatch for " + permission);
        }
    }
}

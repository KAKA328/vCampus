package cn.vcampus.user;

import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        assertEquals(StatusCode.UNAUTHORIZED, service.authorize(secondSession.getToken(), "user:read").getStatus());
    }

    @Test
    void invalidRoleCodeRegistrationIsRejected() {
        UserCredentials credentials = new UserCredentials("u006", "p00600", "Invalid", "NOT_A_ROLE");
        assertEquals(StatusCode.BAD_REQUEST, service.register(credentials).getStatus());
    }
}

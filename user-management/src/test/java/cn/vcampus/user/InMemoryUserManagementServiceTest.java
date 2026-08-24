package cn.vcampus.user;

import cn.vcampus.common.Role;
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
}

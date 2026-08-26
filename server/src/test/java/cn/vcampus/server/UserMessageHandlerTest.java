package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.user.AuthorizationRequest;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserRepository;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMessageHandlerTest {
    private final UserMessageHandler handler = new UserMessageHandler(new InMemoryUserManagementService());

    @Test
    void registerAndLoginMapServiceResultsToMessages() {
        UserCredentials credentials = new UserCredentials("srv001", "srv001", "Server User", Role.STUDENT.name());

        Message registerResponse = handler.handle(Message.request("r1", MessageType.REGISTER, credentials));
        Message loginResponse = handler.handle(Message.request("r2", MessageType.LOGIN, credentials));

        assertEquals(StatusCode.OK, registerResponse.getStatusCode());
        assertEquals(StatusCode.OK, loginResponse.getStatusCode());
        assertTrue(loginResponse.getPayload() instanceof Session);
    }

    @Test
    void invalidLoginPayloadReturnsBadRequest() {
        Message response = handler.handle(Message.request("r3", MessageType.LOGIN, "not credentials"));

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void authorizeDelegatesToUserService() {
        UserCredentials credentials = new UserCredentials("srv002", "srv002", "Server User", Role.STUDENT.name());
        handler.handle(Message.request("r4", MessageType.REGISTER, credentials));
        Message loginResponse = handler.handle(Message.request("r5", MessageType.LOGIN, credentials));
        Session session = (Session) loginResponse.getPayload();

        Message response = handler.handle(Message.request("r6", MessageType.AUTHORIZE,
                new AuthorizationRequest(session.getToken(), Permission.COURSE_SELECT.getCode())));

        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void forgedCoursePermissionRequestFromStoreManagerIsForbidden() {
        SessionManager sessions = new SessionManager();
        UserManagementService users = new DefaultUserManagementService(
                new InMemoryUserRepository(), sessions, new InMemoryAuditLogRepository());
        UserMessageHandler restrictedHandler = new UserMessageHandler(users);
        Session session = sessions.create(new User("store01", "Store Manager", Role.STORE_MANAGER));

        assertForbidden(restrictedHandler, session, Permission.COURSE_READ);
        assertForbidden(restrictedHandler, session, Permission.COURSE_SELECT);
        assertForbidden(restrictedHandler, session, Permission.COURSE_MANAGE);
    }

    @Test
    void unsupportedMessageTypeReturnsNotFound() {
        Message response = handler.handle(Message.request("r7", MessageType.COURSE_QUERY, null));

        assertEquals(StatusCode.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void nullRequestReturnsBadRequest() {
        Message response = handler.handle(null);

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }

    private void assertForbidden(UserMessageHandler target, Session session, Permission permission) {
        Message response = target.handle(Message.request("forged-" + permission, MessageType.AUTHORIZE,
                new AuthorizationRequest(session.getToken(), permission.getCode())));
        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode(), permission.name());
    }
}

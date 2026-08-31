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
import cn.vcampus.user.PasswordChangeCommand;
import cn.vcampus.user.PasswordResetRequestCommand;
import cn.vcampus.user.PasswordResetReviewCommand;
import cn.vcampus.user.PasswordResetReviewResult;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserImportCommand;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import cn.vcampus.user.UserRegistrationCommand;
import cn.vcampus.user.UserRoleChangeCommand;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserMessageHandlerTest {
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final UserMessageHandler handler = new UserMessageHandler(users);

    @Test
    void adminCanRegisterAndNewAccountCanLoginThroughMessages() {
        Session adminSession = loginAsAdmin();
        UserCredentials credentials = new UserCredentials("srv001", "srv001", "Server User", Role.STUDENT.name());

        Message registerResponse = handler.handle(Message.request("r1", MessageType.REGISTER,
                new UserRegistrationCommand(adminSession.getToken(), credentials)));
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
        users.register(credentials);
        Message loginResponse = handler.handle(Message.request("r5", MessageType.LOGIN, credentials));
        Session session = (Session) loginResponse.getPayload();

        Message response = handler.handle(Message.request("r6", MessageType.AUTHORIZE,
                new AuthorizationRequest(session.getToken(), Permission.COURSE_SELECT.getCode())));

        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void registerRequiresAdminSession() {
        UserCredentials student = new UserCredentials("srv003", "srv003", "Student User", Role.STUDENT.name());
        users.register(student);
        Message loginResponse = handler.handle(Message.request("r8", MessageType.LOGIN, student));
        Session session = (Session) loginResponse.getPayload();

        UserCredentials target = new UserCredentials("srv004", "srv004", "Target User", Role.TEACHER.name());
        Message response = handler.handle(Message.request("r9", MessageType.REGISTER,
                new UserRegistrationCommand(session.getToken(), target)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void adminCanImportUsersThroughMessages() {
        Session adminSession = loginAsAdmin();

        Message response = handler.handle(Message.request("r11", MessageType.USER_IMPORT,
                new UserImportCommand(adminSession.getToken(), Arrays.asList(
                        new UserImportRow("srv_imp001", "Demo123", "Imported User", Role.STUDENT.name())))));
        Message loginResponse = handler.handle(Message.request("r12", MessageType.LOGIN,
                new UserCredentials("srv_imp001", "Demo123", "Imported User", Role.STUDENT.name())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof UserImportResult);
        assertEquals(1, ((UserImportResult) response.getPayload()).getSuccessCount());
        assertEquals(StatusCode.OK, loginResponse.getStatusCode());
    }

    @Test
    void adminCanApprovePasswordResetThroughMessages() {
        Session adminSession = loginAsAdmin();
        UserCredentials oldCredentials = new UserCredentials("srv_reset001", "Old123", "Reset User", Role.STUDENT.name());
        handler.handle(Message.request("reset-register", MessageType.REGISTER,
                new UserRegistrationCommand(adminSession.getToken(), oldCredentials)));

        Message requestResponse = handler.handle(Message.request("reset-request", MessageType.PASSWORD_RESET_REQUEST,
                new PasswordResetRequestCommand("srv_reset001", "忘记密码", "13800000000")));
        Message reviewResponse = handler.handle(Message.request("reset-review", MessageType.PASSWORD_RESET_REVIEW,
                new PasswordResetReviewCommand(adminSession.getToken(), "srv_reset001", true)));
        Message oldLogin = handler.handle(Message.request("reset-old-login", MessageType.LOGIN, oldCredentials));
        PasswordResetReviewResult review = (PasswordResetReviewResult) reviewResponse.getPayload();
        Message temporaryLogin = handler.handle(Message.request("reset-temp-login", MessageType.LOGIN,
                new UserCredentials("srv_reset001", review.getTemporaryPassword(), "Reset User", Role.STUDENT.name())));
        Session temporarySession = (Session) temporaryLogin.getPayload();
        Message blockedAuthorize = handler.handle(Message.request("reset-temp-auth", MessageType.AUTHORIZE,
                new AuthorizationRequest(temporarySession.getToken(), Permission.COURSE_SELECT.getCode())));
        Message forcedChange = handler.handle(Message.request("reset-change", MessageType.PASSWORD_CHANGE,
                new PasswordChangeCommand(temporarySession.getToken(), "OwnNew123")));
        Message finalLogin = handler.handle(Message.request("reset-final-login", MessageType.LOGIN,
                new UserCredentials("srv_reset001", "OwnNew123", "Reset User", Role.STUDENT.name())));

        assertEquals(StatusCode.OK, requestResponse.getStatusCode());
        assertEquals(StatusCode.OK, reviewResponse.getStatusCode());
        assertEquals(StatusCode.UNAUTHORIZED, oldLogin.getStatusCode());
        assertEquals(StatusCode.OK, temporaryLogin.getStatusCode());
        assertTrue(temporarySession.isForcePasswordChange());
        assertEquals(StatusCode.FORBIDDEN, blockedAuthorize.getStatusCode());
        assertEquals(StatusCode.OK, forcedChange.getStatusCode());
        assertEquals(StatusCode.OK, finalLogin.getStatusCode());
    }

    @Test
    void adminCanChangeAnotherUsersRoleThroughMessages() {
        Session adminSession = loginAsAdmin();
        UserCredentials target = new UserCredentials("srv_role001", "Demo123", "Role User", Role.STUDENT.name());
        handler.handle(Message.request("role-register", MessageType.REGISTER,
                new UserRegistrationCommand(adminSession.getToken(), target)));
        Session targetSession = (Session) handler.handle(Message.request("role-login", MessageType.LOGIN, target))
                .getPayload();

        Message response = handler.handle(Message.request("role-change", MessageType.USER_ROLE_CHANGE,
                new UserRoleChangeCommand(adminSession.getToken(), "srv_role001", Role.STORE_MANAGER.name())));
        Message oldSession = handler.handle(Message.request("role-old-token", MessageType.AUTHORIZE,
                new AuthorizationRequest(targetSession.getToken(), Permission.COURSE_SELECT.getCode())));
        Message targetLogin = handler.handle(Message.request("role-new-login", MessageType.LOGIN, target));
        Session newSession = (Session) targetLogin.getPayload();
        Message storePermission = handler.handle(Message.request("role-store-auth", MessageType.AUTHORIZE,
                new AuthorizationRequest(newSession.getToken(), Permission.STORE_MANAGE.getCode())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(StatusCode.UNAUTHORIZED, oldSession.getStatusCode());
        assertEquals(StatusCode.OK, targetLogin.getStatusCode());
        assertEquals(StatusCode.OK, storePermission.getStatusCode());
    }

    @Test
    void rawRegisterCredentialsAreRejected() {
        Message response = handler.handle(Message.request("r10", MessageType.REGISTER,
                new UserCredentials("raw001", "raw001", "Raw User", Role.STUDENT.name())));

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
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

    private Session loginAsAdmin() {
        UserCredentials admin = new UserCredentials("admin01", "admin01", "Admin", Role.ADMIN.name());
        users.register(admin);
        Message loginResponse = handler.handle(Message.request("admin-login", MessageType.LOGIN, admin));
        return (Session) loginResponse.getPayload();
    }
}

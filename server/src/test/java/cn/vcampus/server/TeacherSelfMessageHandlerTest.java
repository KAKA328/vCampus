package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeacherSelfMessageHandlerTest {
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final InMemoryTeacherRepository repository = new InMemoryTeacherRepository();
    private final TeacherSelfMessageHandler handler = new TeacherSelfMessageHandler(
            new DefaultTeacherProfileService(repository), users);

    @Test void readsOnlyBoundProfileIncludingInactiveEmployment() {
        String token = login("teacher_a", Role.TEACHER);
        repository.save(new TeacherProfile("T001", "teacher_a", "本人", "院系", "讲师", false));
        repository.save(new TeacherProfile("T002", "teacher_b", "其他教师", "院系", "教授", true));
        Message result = handler.handle(request(token).withSender("teacher_b"));
        assertEquals(StatusCode.OK, result.getStatusCode());
        TeacherProfile profile = (TeacherProfile) result.getPayload();
        assertEquals("T001", profile.getTeacherId());
        assertFalse(profile.isActive());
    }

    @Test void rejectsStudentAndUnboundTeacherAndExpiredSession() {
        assertEquals(StatusCode.FORBIDDEN, handler.handle(request(login("student_a", Role.STUDENT))).getStatusCode());
        String token = login("T001", Role.TEACHER);
        repository.save(new TeacherProfile("T001", null, "未绑定", "", "", true));
        assertEquals(StatusCode.NOT_FOUND, handler.handle(request(token)).getStatusCode());
        users.logout(token);
        assertEquals(StatusCode.UNAUTHORIZED, handler.handle(request(token)).getStatusCode());
    }

    @Test void rejectsWrongPayloadAndDeserializedNullToken() throws Exception {
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(null).getStatusCode());
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.TEACHER_SELF_QUERY_V1, "T001")).getStatusCode());
        TeacherSelfQueryV1Command command = new TeacherSelfQueryV1Command("token");
        java.lang.reflect.Field field = command.getClass().getDeclaredField("token");
        field.setAccessible(true);
        field.set(command, null);
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.TEACHER_SELF_QUERY_V1, command)).getStatusCode());
    }

    @Test void serverDispatchUsesTeacherSelfService() throws Exception {
        String token = login("demo_teacher", Role.TEACHER);
        try (ServerApplication server = new ServerApplication(0, users)) {
            Message result = server.dispatch(request(token));
            assertEquals(StatusCode.OK, result.getStatusCode());
            assertEquals("demo_teacher", ((TeacherProfile) result.getPayload()).getUserId());
            assertEquals(StatusCode.FORBIDDEN, server.dispatch(Message.request("students",
                    MessageType.STUDENT_QUERY, StudentQueryCommand.self(token))).getStatusCode());
        }
    }

    @Test void forcedPasswordChangeBlocksProfileUntilPasswordIsChangedAndUserLogsInAgain() {
        SessionManager sessions = new SessionManager();
        DefaultUserManagementService accounts = new DefaultUserManagementService(
                new InMemoryUserRepository(), sessions, new InMemoryAuditLogRepository());
        UserCredentials credentials = new UserCredentials("teacher_reset", "Demo123", "教师", "TEACHER");
        assertEquals(StatusCode.OK, accounts.register(credentials).getStatus());
        Session forced = sessions.create(new User("teacher_reset", "教师", Role.TEACHER), true);
        repository.save(new TeacherProfile("T_RESET", "teacher_reset", "教师", "院系", "讲师", false));
        TeacherSelfMessageHandler checked = new TeacherSelfMessageHandler(new DefaultTeacherProfileService(repository), accounts);
        assertEquals(StatusCode.FORBIDDEN, checked.handle(request(forced.getToken())).getStatusCode());
        assertEquals(StatusCode.OK, accounts.changeForcedPassword(
                new PasswordChangeCommand(forced.getToken(), "Changed456")).getStatus());
        assertEquals(StatusCode.UNAUTHORIZED, checked.handle(request(forced.getToken())).getStatusCode());
        ServiceResult<Session> fresh = accounts.login(
                new UserCredentials("teacher_reset", "Changed456", "教师", "TEACHER"));
        assertEquals(StatusCode.OK, fresh.getStatus());
        Message profile = checked.handle(request(fresh.getData().getToken()));
        assertEquals(StatusCode.OK, profile.getStatusCode());
        assertFalse(((TeacherProfile) profile.getPayload()).isActive());
    }

    private String login(String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", id, role.name());
        users.register(credentials);
        return users.login(credentials).getData().getToken();
    }
    private static Message request(String token) {
        return Message.request("self", MessageType.TEACHER_SELF_QUERY_V1, new TeacherSelfQueryV1Command(token));
    }
}

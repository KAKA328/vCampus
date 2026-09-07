package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.InMemoryStudentRepository;
import cn.vcampus.student.StudentAcademicQueryV1Command;
import cn.vcampus.student.StudentAcademicQueryV1Command.QueryType;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.UserCredentials;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudentAcademicMessageHandlerTest {
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final InMemoryStudentRepository students = new InMemoryStudentRepository();
    private final InMemoryAcademicReviewService academics = new InMemoryAcademicReviewService();
    private StudentAcademicMessageHandler handler;
    private String token;

    @BeforeEach void setUp() {
        token = login("login_a", Role.STUDENT);
        students.save(student("S001", "login_a"));
        students.save(student("S002", "login_b"));
        academics.addHistory(attempt("S001", "JAVA", 1, false));
        academics.addHistory(attempt("S001", "JAVA", 2, true));
        academics.addHistory(attempt("S001", "DB", 1, false));
        academics.addHistory(attempt("S002", "SECRET", 1, true));
        handler = new StudentAcademicMessageHandler(new DefaultStudentManagementService(students),
                academics, users);
    }

    @Test void derivesBoundStudentIdAndIgnoresForgedSender() {
        Message response = handler.handle(request(token, QueryType.HISTORY).withSender("login_b"));
        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> rows = (List<?>) response.getPayload();
        assertEquals(3, rows.size());
        for (Object row : rows) assertEquals("S001", ((CourseHistoryRecord) row).getStudentId());
    }

    @Test void currentCreditsBelongOnlyToSessionStudentAndDoNotCreateReview() {
        Message result = handler.handle(request(token, QueryType.CREDITS).withSender("login_b"));
        assertEquals(StatusCode.OK, result.getStatusCode());
        cn.vcampus.student.CreditSummary credits = (cn.vcampus.student.CreditSummary) result.getPayload();
        assertEquals("S001", credits.getStudentId());
        assertEquals(3, credits.getEarnedCredits());
        assertEquals(1, credits.getPendingRetakes());
        assertEquals(StatusCode.NOT_FOUND, academics.latestReview("S001").getStatus());
    }

    @Test void retakeQueryOmitsCoursePassedOnLaterAttempt() {
        Message response = handler.handle(request(token, QueryType.PENDING_RETAKES));
        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> rows = (List<?>) response.getPayload();
        assertEquals(1, rows.size());
        assertEquals("DB", ((CourseHistoryRecord) rows.get(0)).getCourseId());
    }

    @Test void rejectsInvalidAndLoggedOutSessions() {
        assertEquals(StatusCode.UNAUTHORIZED, handler.handle(request("invalid", QueryType.HISTORY)).getStatusCode());
        users.logout(token);
        assertEquals(StatusCode.UNAUTHORIZED, handler.handle(request(token, QueryType.HISTORY)).getStatusCode());
    }

    @Test void unboundAccountCannotUseMatchingStudentId() {
        String unbound = login("S002", Role.STUDENT);
        assertEquals(StatusCode.NOT_FOUND, handler.handle(request(unbound, QueryType.HISTORY)).getStatusCode());
    }

    @Test void onlyStudentRoleCanUseSelfQueryEvenWithReadPermission() {
        for (Role role : Role.values()) {
            if (role == Role.STUDENT) continue;
            String other = login("role_" + role, role);
            assertEquals(StatusCode.FORBIDDEN, handler.handle(request(other, QueryType.HISTORY)).getStatusCode());
        }
    }

    @Test void emptyHistoryIsSuccessfulForBoundAccount() {
        students.save(student("S003", "empty"));
        Message result = handler.handle(request(login("empty", Role.STUDENT), QueryType.HISTORY));
        assertEquals(StatusCode.OK, result.getStatusCode());
        assertTrue(((List<?>) result.getPayload()).isEmpty());
    }

    @Test void rejectsWrongPayloadAndMissingDeserializedQueryType() throws Exception {
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.STUDENT_ACADEMIC_QUERY_V1, "S002")).getStatusCode());
        StudentAcademicQueryV1Command command = new StudentAcademicQueryV1Command(token, QueryType.HISTORY);
        java.lang.reflect.Field field = command.getClass().getDeclaredField("queryType");
        field.setAccessible(true);
        field.set(command, null);
        assertEquals(StatusCode.BAD_REQUEST, handler.handle(Message.request("bad",
                MessageType.STUDENT_ACADEMIC_QUERY_V1, command)).getStatusCode());
    }

    private String login(String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", id, role.name());
        assertEquals(StatusCode.OK, users.register(credentials).getStatus());
        return users.login(credentials).getData().getToken();
    }

    private static StudentRecord student(String id, String user) {
        return new StudentRecord(id, user, "测试学生", "未知", "计算机学院", "软件工程",
                "SE2026-01", 2026, "在读", "", "");
    }

    private static CourseHistoryRecord attempt(String student, String course, int number, boolean passed) {
        return new CourseHistoryRecord(student, course, course, "2026-2027-1", number,
                number > 1 ? "重修" : "首修", passed ? 80 : 50, passed, passed ? 3 : 0);
    }

    private static Message request(String token, QueryType type) {
        return Message.request("academic", MessageType.STUDENT_ACADEMIC_QUERY_V1,
                new StudentAcademicQueryV1Command(token, type));
    }
}

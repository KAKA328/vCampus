package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserRepository;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseMessageHandlerTest {
    private CourseMessageHandler handler;
    private Session session;

    @BeforeEach
    void setUp() {
        DefaultUserManagementService users = new DefaultUserManagementService(new InMemoryUserRepository(),
                new SessionManager(), new InMemoryAuditLogRepository());
        UserCredentials student = new UserCredentials("20260001", "password", "测试学生", Role.STUDENT.name());
        users.register(student); session = users.login(student).getData();
        InMemoryStudentSelectionProfileProvider profiles = new InMemoryStudentSelectionProfileProvider(
                Collections.singletonList(new StudentSelectionProfile("20260001", "STU-001", "计算机科学与技术", 2026, "在读", CourseSelectionDemoFactory.DEMO_TERM, 1, Collections.<String>emptySet())));
        handler = new CourseMessageHandler(CourseSelectionDemoFactory.createService(), profiles, users);
    }

    @Test
    void queryUsesTokenToReturnStudentRounds() {
        Message response = handler.handle(Message.request("rounds", MessageType.COURSE_QUERY,
                CourseQueryCommand.availableRounds(session.getToken())));
        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof List<?>);
    }

    @Test
    void selectionRequestDoesNotContainStudentId() {
        Message response = handler.handle(Message.request("select", MessageType.COURSE_SELECT,
                new CourseSelectionCommand(session.getToken(), "ROUND-INITIAL", "OFFER-JAVA-01")));
        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void invalidPayloadReturnsBadRequest() {
        Message response = handler.handle(Message.request("invalid", MessageType.COURSE_SELECT, "bad"));
        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }
}

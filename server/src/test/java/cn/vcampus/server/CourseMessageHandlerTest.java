package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectOfferingV2Command;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserRepository;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import java.util.Collections;
import java.time.LocalDateTime;
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
        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));
        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof List<?>);
    }

    @Test
    void selectionRequestDoesNotContainStudentId() {
        Message response = handler.handle(Message.request("select",
                MessageType.COURSE_SELECT_OFFERING_V2,
                new CourseSelectOfferingV2Command(session.getToken(), "ROUND-INITIAL",
                        "OFFER-JAVA-01")));
        assertEquals(StatusCode.OK, response.getStatusCode());
    }

    @Test
    void invalidPayloadReturnsBadRequest() {
        Message response = handler.handle(Message.request("invalid",
                MessageType.COURSE_SELECT_OFFERING_V2, "bad"));
        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void academicAdminCanManageCatalogAndOfferings() {
        DefaultUserManagementService users = new DefaultUserManagementService(new InMemoryUserRepository(),
                new SessionManager(), new InMemoryAuditLogRepository());
        UserCredentials academicAdmin = new UserCredentials("academic_001", "password", "教务老师",
                Role.ACADEMIC_ADMIN.name());
        users.register(academicAdmin);
        Session academicSession = users.login(academicAdmin).getData();
        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        CourseMessageHandler managementHandler = new CourseMessageHandler(
                module.getSelectionService(), module.getCatalogService(), module.getOfferingService(),
                module.getSelectionRoundService(),
                new InMemoryStudentSelectionProfileProvider(Collections.<StudentSelectionProfile>emptyList()),
                users);

        Message createCourse = managementHandler.handle(Message.request("create-course",
                MessageType.COURSE_MANAGE, CourseManagementCommand.createCourse(
                        academicSession.getToken(), new Course("CS201", "算法设计", 3))));
        assertEquals(StatusCode.OK, createCourse.getStatusCode());

        Message createOffering = managementHandler.handle(Message.request("create-offering",
                MessageType.COURSE_MANAGE, CourseManagementCommand.createOffering(
                        academicSession.getToken(), new CourseOffering("OFFER-CS201-01", "CS201",
                                CourseSelectionDemoFactory.DEMO_TERM, "教师004", "周四 1-2 节", "A204",
                                30, 10, 5, CourseOfferingStatus.DRAFT))));
        assertEquals(StatusCode.OK, createOffering.getStatusCode());

        Message updateTeachingInfo = managementHandler.handle(Message.request("update-teaching-info",
                MessageType.COURSE_MANAGE, CourseManagementCommand.updateOfferingTeachingInfo(
                        academicSession.getToken(), "OFFER-CS201-01", "教师005", "B301")));
        assertEquals(StatusCode.OK, updateTeachingInfo.getStatusCode());
        CourseOffering updatedOffering = (CourseOffering) updateTeachingInfo.getPayload();
        assertEquals("教师005", updatedOffering.getTeacherId());
        assertEquals("B301", updatedOffering.getLocation());
        assertEquals("周四 1-2 节", updatedOffering.getSchedule());

        LocalDateTime startsAt = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime endsAt = LocalDateTime.of(2026, 10, 7, 18, 0);
        Message createRound = managementHandler.handle(Message.request("create-round",
                MessageType.COURSE_MANAGE, CourseManagementCommand.createSelectionRound(
                        academicSession.getToken(), new SelectionRound("ROUND-EXTRA",
                                "2026-2027-2", SelectionRoundType.INITIAL, startsAt, endsAt,
                                SelectionRoundStatus.DRAFT))));
        assertEquals(StatusCode.OK, createRound.getStatusCode());

        Message openRound = managementHandler.handle(Message.request("open-round",
                MessageType.COURSE_MANAGE, CourseManagementCommand.changeSelectionRoundStatus(
                        academicSession.getToken(), "ROUND-EXTRA", SelectionRoundStatus.OPEN)));
        assertEquals(StatusCode.OK, openRound.getStatusCode());

        Message updateRoundTime = managementHandler.handle(Message.request("update-round-time",
                MessageType.COURSE_MANAGE, CourseManagementCommand.updateSelectionRoundTimeWindow(
                        academicSession.getToken(), "ROUND-EXTRA", startsAt.plusDays(1),
                        endsAt.plusDays(1))));
        assertEquals(StatusCode.OK, updateRoundTime.getStatusCode());
    }

    @Test
    void studentCannotCallCourseManagementMessage() {
        Message response = handler.handle(Message.request("manage", MessageType.COURSE_MANAGE,
                CourseManagementCommand.listCourses(session.getToken())));
        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }
}

package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionQueryV2Command;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.InMemoryStudentRepository;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseStudentProfileIntegrationTest {
    @Test
    void tokenResolvesDifferentStudentIdAndExposesRetakeRound() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session session = login(users, "login_001");
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        repository.save(student("STU-001", "login_001", "在读"));
        InMemoryAcademicReviewService academicReviews = new InMemoryAcademicReviewService();
        academicReviews.addHistory(new CourseHistoryRecord("STU-001", "DB101", "数据库原理",
                "2025-2026-1", 1, "首修", 52, false, 0));
        StudentSelectionProfileAdapter profiles = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(repository), academicReviews,
                CourseSelectionDemoFactory.DEMO_TERM);
        CourseMessageHandler handler = new CourseMessageHandler(
                CourseSelectionDemoFactory.createService(), profiles, users);

        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> rounds = (List<?>) response.getPayload();
        assertTrue(rounds.stream().map(SelectionRound.class::cast)
                .anyMatch(round -> round.getType() == SelectionRoundType.RETAKE));
    }

    @Test
    void unboundAccountIsRejectedBeforeCourseSelection() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session session = login(users, "unbound_001");
        StudentSelectionProfileAdapter profiles = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(new InMemoryStudentRepository()),
                new InMemoryAcademicReviewService(), CourseSelectionDemoFactory.DEMO_TERM);
        CourseMessageHandler handler = new CourseMessageHandler(
                CourseSelectionDemoFactory.createService(), profiles, users);

        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));

        assertEquals(StatusCode.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void suspendedStudentCannotEnterSelectionRounds() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session session = login(users, "suspended_001");
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        repository.save(student("STU-002", "suspended_001", "休学"));
        StudentSelectionProfileAdapter profiles = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(repository),
                new InMemoryAcademicReviewService(), CourseSelectionDemoFactory.DEMO_TERM);
        CourseMessageHandler handler = new CourseMessageHandler(
                CourseSelectionDemoFactory.createService(), profiles, users);

        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void accessSeedStyleStudentMatchesSoftwareEngineeringPlan() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session session = login(users, "demo_student");
        InMemoryStudentRepository repository = new InMemoryStudentRepository();
        repository.save(new StudentRecord("demo_student", "demo_student", "演示学生", "未知",
                "计算机科学与工程学院", "软件工程", "SE2023-01", 2023,
                "在读", "", ""));
        StudentSelectionProfileAdapter profiles = new StudentSelectionProfileAdapter(
                new DefaultStudentManagementService(repository),
                new InMemoryAcademicReviewService(), CourseSelectionDemoFactory.DEMO_TERM);
        CourseMessageHandler handler = new CourseMessageHandler(
                CourseSelectionDemoFactory.createService(), profiles, users);

        Message response = handler.handle(Message.request("rounds",
                MessageType.COURSE_SELECTION_QUERY_V2,
                CourseSelectionQueryV2Command.availableRounds(session.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> rounds = (List<?>) response.getPayload();
        assertTrue(rounds.stream().map(SelectionRound.class::cast)
                .anyMatch(round -> round.getType() == SelectionRoundType.INITIAL));
    }

    private static Session login(InMemoryUserManagementService users, String userId) {
        UserCredentials credentials = new UserCredentials(
                userId, "password", "测试学生", Role.STUDENT.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }

    private static StudentRecord student(String studentId, String userId, String status) {
        return new StudentRecord(studentId, userId, "测试学生", "男", "计算机学院",
                "计算机科学与技术", "CS2026-01", 2026, status, "", "");
    }
}

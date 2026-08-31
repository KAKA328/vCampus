package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.InMemoryCourseSelectionService;
import cn.vcampus.user.DefaultUserManagementService;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserRepository;
import cn.vcampus.user.Session;
import cn.vcampus.user.SessionManager;
import cn.vcampus.user.UserCredentials;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourseMessageHandlerTest {
    private InMemoryCourseSelectionService courses;
    private DefaultUserManagementService users;
    private CourseMessageHandler handler;
    private Session studentSession;

    @BeforeEach
    void setUp() {
        courses = new InMemoryCourseSelectionService();
        users = new DefaultUserManagementService(
                new InMemoryUserRepository(), new SessionManager(), new InMemoryAuditLogRepository());
        handler = new CourseMessageHandler(courses, users);

        UserCredentials student = new UserCredentials(
                "20230001", "password", "测试学生", Role.STUDENT.name());
        users.register(student);
        studentSession = users.login(student).getData();
    }

    @Test
    void courseQueryReturnsCourseList() {
        Message response = handler.handle(Message.request(
                "course-query", MessageType.COURSE_QUERY, CourseQueryCommand.allCourses()));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof List<?>);
        List<?> courses = (List<?>) response.getPayload();
        assertEquals(3, courses.size());
        assertTrue(courses.get(0) instanceof Course);
    }

    @Test
    void selectedCoursesQueryAuthorizesStudentAndReturnsOnlySelectedCourses() {
        courses.select("20230001", "JAVA101");
        CourseQueryCommand command = CourseQueryCommand.selectedCourses(
                studentSession.getToken(), "20230001");

        Message response = handler.handle(Message.request(
                "selected-courses", MessageType.COURSE_QUERY, command));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> selected = (List<?>) response.getPayload();
        assertEquals(1, selected.size());
        assertEquals("JAVA101", ((Course) selected.get(0)).getCourseId());
    }

    @Test
    void courseSelectAuthorizesStudentAndCallsService() {
        CourseSelectionCommand command = new CourseSelectionCommand(
                studentSession.getToken(), "20230001", "JAVA101");

        Message response = handler.handle(Message.request(
                "course-select", MessageType.COURSE_SELECT, command));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(1, courses.selectedCourses("20230001").getData().size());
    }

    @Test
    void studentCannotSelectCourseForAnotherStudent() {
        registerStudent("20230002", "password", "测试学生二");
        CourseSelectionCommand command = new CourseSelectionCommand(
                studentSession.getToken(), "20230002", "JAVA101");

        Message response = handler.handle(Message.request(
                "forged-course-select", MessageType.COURSE_SELECT, command));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertEquals(0, courses.selectedCourses("20230002").getData().size());
    }

    @Test
    void courseDropAuthorizesStudentAndCallsService() {
        courses.select("20230001", "JAVA101");
        CourseSelectionCommand command = new CourseSelectionCommand(
                studentSession.getToken(), "20230001", "JAVA101");

        Message response = handler.handle(Message.request(
                "course-drop", MessageType.COURSE_DROP, command));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(0, courses.selectedCourses("20230001").getData().size());
    }

    @Test
    void studentCannotDropCourseForAnotherStudent() {
        registerStudent("20230002", "password", "测试学生二");
        courses.select("20230002", "JAVA101");
        CourseSelectionCommand command = new CourseSelectionCommand(
                studentSession.getToken(), "20230002", "JAVA101");

        Message response = handler.handle(Message.request(
                "forged-course-drop", MessageType.COURSE_DROP, command));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertEquals(1, courses.selectedCourses("20230002").getData().size());
    }

    @Test
    void studentCannotQuerySelectedCoursesForAnotherStudent() {
        registerStudent("20230002", "password", "测试学生二");
        courses.select("20230002", "JAVA101");
        CourseQueryCommand command = CourseQueryCommand.selectedCourses(
                studentSession.getToken(), "20230002");

        Message response = handler.handle(Message.request(
                "forged-selected-courses", MessageType.COURSE_QUERY, command));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void invalidTokenRejectsCourseSelection() {
        CourseSelectionCommand command = new CourseSelectionCommand(
                "invalid-token", "20230001", "JAVA101");

        Message response = handler.handle(Message.request(
                "invalid-token", MessageType.COURSE_SELECT, command));

        assertEquals(StatusCode.UNAUTHORIZED, response.getStatusCode());
        assertEquals(0, courses.selectedCourses("20230001").getData().size());
    }

    @Test
    void userWithoutCourseSelectionPermissionIsRejected() {
        UserCredentials teacher = new UserCredentials(
                "teacher001", "password", "测试教师", Role.TEACHER.name());
        assertEquals(StatusCode.OK, users.provisionAccount(teacher).getStatus());
        ServiceResult<Session> login = users.login(teacher);
        assertEquals(StatusCode.OK, login.getStatus());
        Session teacherSession = login.getData();
        CourseSelectionCommand command = new CourseSelectionCommand(
                teacherSession.getToken(), "20230001", "JAVA101");

        Message response = handler.handle(Message.request(
                "permission-denied", MessageType.COURSE_SELECT, command));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertEquals(0, courses.selectedCourses("20230001").getData().size());
    }

    @Test
    void invalidPayloadReturnsBadRequest() {
        Message response = handler.handle(Message.request(
                "invalid-payload", MessageType.COURSE_SELECT, "not a course command"));

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void unsupportedMessageTypeReturnsNotFound() {
        Message response = handler.handle(Message.request(
                "unsupported", MessageType.LIBRARY_QUERY, null));

        assertEquals(StatusCode.NOT_FOUND, response.getStatusCode());
    }

    private void registerStudent(String userId, String password, String displayName) {
        users.register(new UserCredentials(userId, password, displayName, Role.STUDENT.name()));
    }
}

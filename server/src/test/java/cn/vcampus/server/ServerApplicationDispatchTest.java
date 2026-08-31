package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseRoundQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.InMemoryCourseSelectionService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerApplicationDispatchTest {
    @Test
    void dispatchRoutesCourseMessagesToCourseHandler() {
        ServerApplication server = new ServerApplication(
                0, new InMemoryUserManagementService(), new InMemoryCourseSelectionService());

        Message response = server.dispatch(Message.request(
                "course-query", MessageType.COURSE_QUERY, CourseQueryCommand.allCourses()));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof List<?>);
    }

    @Test
    void dispatchRoutesCourseProtocolV2MessagesToCourseHandler() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        InMemoryCourseSelectionService courses = new InMemoryCourseSelectionService();
        ServerApplication server = new ServerApplication(0, users, courses);
        UserCredentials student = new UserCredentials("20230001", "password", "测试学生", Role.STUDENT.name());
        users.register(student);
        Session session = users.login(student).getData();

        Message response = server.dispatch(Message.request(
                "course-v2-rounds", MessageType.COURSE_ROUND_QUERY,
                new CourseRoundQueryCommand(session.getToken(), "2026-2027-1")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(response.getPayload() instanceof List<?>);
    }

    @Test
    void dispatchRejectsForgedStudentIdInCourseSelection() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        InMemoryCourseSelectionService courses = new InMemoryCourseSelectionService();
        ServerApplication server = new ServerApplication(0, users, courses);
        UserCredentials student = new UserCredentials("20230001", "password", "测试学生", Role.STUDENT.name());
        users.register(student);
        Session session = users.login(student).getData();

        Message response = server.dispatch(Message.request(
                "forged-select", MessageType.COURSE_SELECT,
                new CourseSelectionCommand(session.getToken(), "20230002", "JAVA101")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertEquals(0, courses.selectedCourses("20230002").getData().size());
    }
}

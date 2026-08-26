package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.InMemoryCourseSelectionService;
import cn.vcampus.library.LibraryQueryCommand;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.student.StudentReviewCommand;
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

    @Test
    void dispatchRoutesStudentLibraryAndStoreMessages() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        ServerApplication server = new ServerApplication(0, users);
        Session student = login(users, "20230001", Role.STUDENT);

        Message studentReview = server.dispatch(Message.request(
                "student-review", MessageType.STUDENT_REVIEW,
                new StudentReviewCommand(student.getToken(), "20230001", 120)));
        Message libraryQuery = server.dispatch(Message.request(
                "library-query", MessageType.LIBRARY_QUERY,
                new LibraryQueryCommand(student.getToken(), "")));
        Message storeOrders = server.dispatch(Message.request(
                "store-orders", MessageType.STORE_ORDER_QUERY,
                StoreOrderQueryCommand.ownOrders(student.getToken(), "20230001")));

        assertEquals(StatusCode.OK, studentReview.getStatusCode());
        assertEquals(StatusCode.OK, libraryQuery.getStatusCode());
        assertEquals(StatusCode.OK, storeOrders.getStatusCode());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}

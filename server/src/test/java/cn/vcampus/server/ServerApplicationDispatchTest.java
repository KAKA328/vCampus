package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.InMemoryCourseSelectionService;
import cn.vcampus.user.InMemoryUserManagementService;
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
}

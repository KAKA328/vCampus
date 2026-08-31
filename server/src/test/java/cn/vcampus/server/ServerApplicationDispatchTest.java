package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.StudentSelectionProfile;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ServerApplicationDispatchTest {
    @Test
    void dispatchRoutesCurrentCourseQueryWithoutClientStudentId() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        UserCredentials account = new UserCredentials("20260001", "password", "测试学生", Role.STUDENT.name());
        users.register(account); Session session = users.login(account).getData();
        InMemoryStudentSelectionProfileProvider profiles = new InMemoryStudentSelectionProfileProvider(
                Collections.singletonList(new StudentSelectionProfile("20260001", "STU-001", "计算机科学与技术", 2026, "在读", CourseSelectionDemoFactory.DEMO_TERM, 1, Collections.<String>emptySet())));
        ServerApplication server = new ServerApplication(0, users, CourseSelectionDemoFactory.createService(), profiles);

        Message response = server.dispatch(Message.request("rounds", MessageType.COURSE_QUERY,
                CourseQueryCommand.availableRounds(session.getToken())));
        assertEquals(StatusCode.OK, response.getStatusCode());
    }
}

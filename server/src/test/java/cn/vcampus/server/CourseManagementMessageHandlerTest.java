package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseGradeCommand;
import cn.vcampus.course.InMemoryCourseSelectionService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

class CourseManagementMessageHandlerTest {
    @Test
    void academicAdminCanCreateAndDeactivateCourse() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session academic = login(users, "academic001", Role.ACADEMIC_ADMIN);
        InMemoryCourseSelectionService courses = new InMemoryCourseSelectionService();
        CourseMessageHandler handler = new CourseMessageHandler(courses, users);

        Message created = handler.handle(Message.request("create", MessageType.COURSE_CREATE,
                CourseManagementCommand.forCourse(academic.getToken(), new Course("AI101", "人工智能导论", 2, 30))));
        Message deactivated = handler.handle(Message.request("deactivate", MessageType.COURSE_DEACTIVATE,
                CourseManagementCommand.forCourseId(academic.getToken(), "AI101")));

        assertEquals(StatusCode.OK, created.getStatusCode());
        assertEquals(StatusCode.OK, deactivated.getStatusCode());
        assertEquals(false, courses.findCourse("AI101").getData().isActive());
    }

    @Test
    void storeManagerCannotCreateCourse() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session store = login(users, "store001", Role.STORE_MANAGER);
        CourseMessageHandler handler = new CourseMessageHandler(new InMemoryCourseSelectionService(), users);

        Message response = handler.handle(Message.request("create", MessageType.COURSE_CREATE,
                CourseManagementCommand.forCourse(store.getToken(), new Course("AI101", "人工智能导论", 2, 30))));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void teacherCanRecordGradeForSelectedStudent() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session teacher = login(users, "teacher001", Role.TEACHER);
        InMemoryCourseSelectionService courses = new InMemoryCourseSelectionService();
        courses.select("20230001", "JAVA101");
        CourseMessageHandler handler = new CourseMessageHandler(courses, users);

        Message response = handler.handle(Message.request("grade", MessageType.COURSE_GRADE_WRITE,
                new CourseGradeCommand(teacher.getToken(), "teacher001", "20230001", "JAVA101", 91)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(Integer.valueOf(91), courses.gradeOf("20230001", "JAVA101").getData());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}

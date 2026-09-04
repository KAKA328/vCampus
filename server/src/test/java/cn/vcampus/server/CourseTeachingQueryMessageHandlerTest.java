package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseSelectionDemoFactory;
import cn.vcampus.course.CourseSelectionModule;
import cn.vcampus.course.CourseSelectionRecord;
import cn.vcampus.course.CourseTeachingQueryV2Command;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.DefaultTeacherProfileService;
import cn.vcampus.student.InMemoryStudentRepository;
import cn.vcampus.student.InMemoryTeacherRepository;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.student.TeacherProfileService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 覆盖教师只能查询本人教学班，以及名单只包含有效选课记录的服务器边界。 */
class CourseTeachingQueryMessageHandlerTest {
    private CourseMessageHandler handler;
    private Session teacherOne;
    private Session teacherTwo;

    @BeforeEach
    void setUp() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        teacherOne = login(users, "teacher_001");
        teacherTwo = login(users, "teacher_002");

        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        module.getSelectionRecordService().create(new CourseSelectionRecord("REC-ACTIVE", "STU-001",
                "OFFER-JAVA-01", "ROUND-INITIAL", SelectionType.RETAKE, LocalDateTime.now()));
        module.getSelectionRecordService().create(new CourseSelectionRecord("REC-DROPPED", "STU-002",
                "OFFER-JAVA-01", "ROUND-INITIAL", SelectionType.REQUIRED, LocalDateTime.now()));
        module.getSelectionRecordService().markDropped("REC-DROPPED", LocalDateTime.now().plusMinutes(1));

        InMemoryStudentRepository studentRepository = new InMemoryStudentRepository();
        studentRepository.save(new StudentRecord("STU-001", "student_001", "张三", "男", "计算机学院",
                "软件工程", "SE2023-01", 2023, "在读", "", ""));
        studentRepository.save(new StudentRecord("STU-002", "student_002", "李四", "女", "计算机学院",
                "软件工程", "SE2023-01", 2023, "在读", "", ""));

        TeacherProfileService teachers = new DefaultTeacherProfileService(
                new InMemoryTeacherRepository());
        teachers.save(new TeacherProfile("教师001", "teacher_001", "王老师", "计算机学院", "讲师", true));
        teachers.save(new TeacherProfile("教师002", "teacher_002", "赵老师", "计算机学院", "讲师", true));
        handler = new CourseMessageHandler(module.getSelectionService(), module.getCatalogService(),
                module.getOfferingService(), module.getSelectionRoundService(),
                module.getSelectionRecordService(),
                new InMemoryStudentSelectionProfileProvider(Collections.emptyList()), users, teachers,
                new DefaultStudentManagementService(studentRepository));
    }

    @Test
    void teacherCanListOnlyOwnOfferingsAndSeeCourseInformation() {
        Message response = handler.handle(Message.request("my-offerings",
                MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.myOfferings(teacherOne.getToken(),
                        CourseSelectionDemoFactory.DEMO_TERM)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> offerings = (List<?>) response.getPayload();
        assertEquals(1, offerings.size());
        TeachingOffering teachingOffering = (TeachingOffering) offerings.get(0);
        assertEquals("OFFER-JAVA-01", teachingOffering.getOffering().getOfferingId());
        assertEquals("Java 程序设计", teachingOffering.getCourse().getName());
    }

    @Test
    void rosterContainsOnlyActiveStudentsAndKeepsSelectionType() {
        Message response = handler.handle(Message.request("roster",
                MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.offeringRoster(teacherOne.getToken(),
                        "OFFER-JAVA-01")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        TeachingRoster roster = (TeachingRoster) response.getPayload();
        assertEquals("Java 程序设计", roster.getTeachingOffering().getCourse().getName());
        assertEquals(1, roster.getStudents().size());
        assertEquals("STU-001", roster.getStudents().get(0).getStudentId());
        assertEquals(SelectionType.RETAKE, roster.getStudents().get(0).getSelectionType());
    }

    @Test
    void teacherCannotQueryAnotherTeachersRoster() {
        Message response = handler.handle(Message.request("other-roster",
                MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.offeringRoster(teacherTwo.getToken(),
                        "OFFER-JAVA-01")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void ownOfferingWithNoActiveSelectionsReturnsEmptyRoster() {
        Message response = handler.handle(Message.request("empty-roster",
                MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.offeringRoster(teacherTwo.getToken(), "OFFER-DB-01")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        TeachingRoster roster = (TeachingRoster) response.getPayload();
        assertTrue(roster.getStudents().isEmpty());
    }

    @Test
    void unboundTeacherAccountIsRejected() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session unboundTeacher = login(users, "teacher_unbound");
        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        CourseMessageHandler unboundHandler = new CourseMessageHandler(module.getSelectionService(),
                module.getCatalogService(), module.getOfferingService(), module.getSelectionRoundService(),
                module.getSelectionRecordService(),
                new InMemoryStudentSelectionProfileProvider(Collections.emptyList()), users,
                new DefaultTeacherProfileService(new InMemoryTeacherRepository()),
                new DefaultStudentManagementService(new InMemoryStudentRepository()));

        Message response = unboundHandler.handle(Message.request("unbound",
                MessageType.COURSE_TEACHING_QUERY_V2,
                CourseTeachingQueryV2Command.myOfferings(unboundTeacher.getToken(),
                        CourseSelectionDemoFactory.DEMO_TERM)));

        assertEquals(StatusCode.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getPayload() instanceof List<?>);
    }

    private static Session login(InMemoryUserManagementService users, String userId) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, Role.TEACHER.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}

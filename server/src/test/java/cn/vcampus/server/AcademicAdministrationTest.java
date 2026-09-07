package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import cn.vcampus.user.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcademicAdministrationTest {
    private final InMemoryStudentRepository students = new InMemoryStudentRepository();
    private final InMemoryTeacherRepository teachers = new InMemoryTeacherRepository();
    private final InMemoryAcademicReviewService history = new InMemoryAcademicReviewService();
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private final DefaultStudentManagementService studentService = new DefaultStudentManagementService(students);
    private AcademicAdminMessageHandler handler;
    private String admin;
    @BeforeEach void setup() {
        students.save(student("S001", "student_a", "在读"));
        students.save(student("S002", "student_b", "休学"));
        teachers.save(new TeacherProfile("T001", "teacher_a", "教师", "院系", "教授", false));
        history.addHistory(attempt("C1", 1, false, 0));
        history.addHistory(attempt("C1", 2, true, 3));
        history.addHistory(attempt("C1", 3, true, 3));
        handler = new AcademicAdminMessageHandler(new AcademicAdminService(new InMemoryAcademicAdminStore(
                studentService, new DefaultTeacherProfileService(teachers), history)), users);
        admin = login("academic", Role.ACADEMIC_ADMIN);
    }
    @Test void administratorSeesEveryStudentAndInactiveTeacher() {
        assertEquals(2, ((List<?>) send(command(admin, Action.STUDENTS, null, 0, null)).getPayload()).size());
        assertEquals(1, ((List<?>) send(command(admin, Action.TEACHERS, null, 0, null)).getPayload()).size());
        String system = login("admin", Role.ADMIN);
        assertEquals(StatusCode.OK, send(command(system, Action.STUDENTS, null, 0, null)).getStatusCode());
    }
    @Test void everyNonAdministratorIsRejectedBeforeListingOrReviewing() {
        for (Role role : Role.values()) {
            if (role == Role.ADMIN || role == Role.ACADEMIC_ADMIN) continue;
            String token = login("role_" + role, role);
            for (Action action : Action.values()) {
                assertEquals(StatusCode.FORBIDDEN, send(command(token, action, "S001", 3, "fake")).getStatusCode());
            }
        }
        assertEquals(StatusCode.UNAUTHORIZED, send(command("invalid", Action.STUDENTS, null, 0, null)).getStatusCode());
    }
    @Test void creditSummaryDoesNotDoubleCountPassedAttemptsOrUseFailedCredits() {
        history.addHistory(attempt("C1", 4, false, 99));
        CreditSummary summary = (CreditSummary) send(command(admin, Action.CREDITS, "S001", 0, null)).getPayload();
        assertEquals(3, summary.getEarnedCredits());
        assertEquals(1, summary.getPassedCourses());
        assertEquals(0, summary.getPendingRetakes());
        assertEquals(3, history.review("S001", 3).getData().getTotalEarnedCredits());
        assertEquals(StatusCode.NOT_FOUND, send(command(admin, Action.CREDITS, "missing", 0, null)).getStatusCode());
    }
    @Test void reviewIsPersistedAndGraduationRequiresSeparateConfirmation() {
        AcademicAssessment assessment = review(3);
        assertEquals("academic", assessment.getReviewedBy());
        assertTrue(assessment.isCreditRequirementMet());
        assertFalse(assessment.isGraduated());
        assertEquals("在读", students.findById("S001").getStatus());
        Message graduated = send(command(admin, Action.GRADUATE, "S001", 0, assessment.getId()));
        assertEquals(StatusCode.OK, graduated.getStatusCode());
        AcademicAssessment saved = (AcademicAssessment) graduated.getPayload();
        assertEquals("academic", saved.getGraduatedBy());
        assertNotNull(saved.getGraduatedAt());
        assertEquals("毕业", students.findById("S001").getStatus());
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, assessment.getId())).getStatusCode());
        assertTrue(((AcademicAssessment) ((List<?>) send(command(admin, Action.ASSESSMENTS, "S001", 0, null))
                .getPayload()).get(0)).isGraduated());
    }
    @Test void insufficientCreditsOrPendingRetakesCannotGraduate() {
        AcademicAssessment insufficient = review(6);
        assertEquals(3, insufficient.getShortfall());
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, insufficient.getId())).getStatusCode());
        history.addHistory(attempt("C2", 1, false, 0));
        AcademicAssessment failed = review(3);
        assertFalse(failed.isCreditRequirementMet());
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, failed.getId())).getStatusCode());
    }
    @Test void outdatedReviewOrChangedHistoryCannotBeUsed() {
        AcademicAssessment old = review(3);
        AcademicAssessment latest = review(3);
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, old.getId())).getStatusCode());
        history.addHistory(attempt("C1", 4, true, 3)); // total is unchanged, evidence differs
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, latest.getId())).getStatusCode());
    }
    @Test void changedProfileOrInactiveStatusBlocksConfirmation() {
        AcademicAssessment old = review(3);
        students.save(new StudentRecord("S001", "student_a", "新姓名", "未知", "院系", "专业", "班级", 2026, "在读", "", ""));
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.GRADUATE, "S001", 0, old.getId())).getStatusCode());
        assertEquals(StatusCode.CONFLICT, send(command(admin, Action.REVIEW, "S002", 3, null)).getStatusCode());
    }
    @Test void bypassThroughStudentUpdateIsRejected() {
        StudentMessageHandler legacy = new StudentMessageHandler(studentService, users);
        Message request = Message.request("bypass", MessageType.STUDENT_UPDATE,
                new StudentUpdateCommand(admin, student("S001", "student_a", "毕业")));
        assertEquals(StatusCode.FORBIDDEN, legacy.handle(request).getStatusCode());
        assertEquals("在读", students.findById("S001").getStatus());
    }
    @Test void requestRequiresExplicitOtherRequirementsAndPositiveCredits() {
        assertThrows(IllegalArgumentException.class, () -> command(admin, Action.REVIEW, "S001", 0, null));
        assertThrows(IllegalArgumentException.class, () -> new AcademicAdminCommandV1(admin, Action.GRADUATE,
                "S001", 0, "id", "依据", false));
    }
    @Test void deserializedUnconfirmedGraduationCannotBypassValidation() throws Exception {
        AcademicAdminCommandV1 command = command(admin, Action.GRADUATE, "S001", 0, review(3).getId());
        java.lang.reflect.Field field = command.getClass().getDeclaredField("otherRequirementsConfirmed");
        field.setAccessible(true); field.setBoolean(command, false);
        assertEquals(StatusCode.BAD_REQUEST, send(command).getStatusCode());
        assertEquals("在读", students.findById("S001").getStatus());
    }

    @Test void serverDispatchConnectsAdminDirectoryToTheSameStudentService() throws Exception {
        try (ServerApplication server = new ServerApplication(0, users)) {
            Message result = server.dispatch(Message.request("directory", MessageType.ACADEMIC_ADMIN_V1,
                    command(admin, Action.STUDENTS, null, 0, null)));
            assertEquals(StatusCode.OK, result.getStatusCode());
            assertEquals("20260001", ((StudentRecord) ((List<?>) result.getPayload()).get(0)).getStudentId());
        }
    }
    private AcademicAssessment review(int required) {
        Message result = send(command(admin, Action.REVIEW, "S001", required, null));
        assertEquals(StatusCode.OK, result.getStatusCode(), String.valueOf(result.getPayload()));
        return (AcademicAssessment) result.getPayload();
    }
    private Message send(AcademicAdminCommandV1 command) {
        return handler.handle(Message.request("admin", MessageType.ACADEMIC_ADMIN_V1, command).withSender("spoofed_actor"));
    }
    private static AcademicAdminCommandV1 command(String token, Action action, String student, int credits, String id) {
        return new AcademicAdminCommandV1(token, action, student, credits, id, "适用培养方案已核查", true);
    }
    private String login(String id, Role role) {
        UserCredentials credentials = new UserCredentials(id, "Demo123", id, role.name());
        users.register(credentials); return users.login(credentials).getData().getToken();
    }
    private static StudentRecord student(String id, String account, String state) {
        return new StudentRecord(id, account, "学生", "未知", "院系", "专业", "班级", 2026, state, "", "");
    }
    private static CourseHistoryRecord attempt(String course, int attempt, boolean passed, int credits) {
        return new CourseHistoryRecord("S001", course, course, "2026-2027-1", attempt, "首修", passed ? 80 : 50, passed, credits);
    }
}

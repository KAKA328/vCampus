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
import cn.vcampus.course.CourseGradeDraftV2Command;
import cn.vcampus.course.CourseGradeImportV2Command;
import cn.vcampus.course.CourseGradeReviewV2Command;
import cn.vcampus.course.CourseTeacherDirectoryV1Command;
import cn.vcampus.course.CourseTeachingQueryV2Command;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.GradeSubmissionAuditAction;
import cn.vcampus.course.GradeSubmissionAuditRecord;
import cn.vcampus.course.GradeSubmissionService;
import cn.vcampus.course.GradeImportResult;
import cn.vcampus.course.InMemoryStudentSelectionProfileProvider;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingGradeDraft;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.student.DefaultStudentManagementService;
import cn.vcampus.student.InMemoryAcademicReviewService;
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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 覆盖教师只能查询本人教学班，以及名单只包含有效选课记录的服务器边界。 */
class CourseTeachingQueryMessageHandlerTest {
    private CourseMessageHandler handler;
    private Session teacherOne;
    private Session teacherTwo;
    private Session student;
    private Session academicAdmin;
    private InMemoryAcademicReviewService formalResults;
    private GradeSubmissionService gradeSubmissions;
    private TeacherProfileService teachers;

    @BeforeEach
    void setUp() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        teacherOne = login(users, "teacher_001", Role.TEACHER);
        teacherTwo = login(users, "teacher_002", Role.TEACHER);
        student = login(users, "student_001", Role.STUDENT);
        academicAdmin = login(users, "academic_001", Role.ACADEMIC_ADMIN);
        formalResults = new InMemoryAcademicReviewService();

        CourseSelectionModule module = CourseSelectionDemoFactory.createModule();
        gradeSubmissions = module.getGradeSubmissionService();
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

        teachers = new DefaultTeacherProfileService(
                new InMemoryTeacherRepository());
        teachers.save(new TeacherProfile("教师001", "teacher_001", "王老师", "计算机学院", "讲师", true));
        teachers.save(new TeacherProfile("教师002", "teacher_002", "赵老师", "计算机学院", "讲师", true));
        handler = new CourseMessageHandler(module.getSelectionService(), module.getCatalogService(),
                module.getOfferingService(), module.getSelectionRoundService(),
                module.getSelectionRecordService(), module.getGradeSubmissionService(),
                formalResults,
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

    /** 教学班编辑依赖此目录；仅教务可查询，且停用教师不能出现在可选项中。 */
    @Test
    void academicAdminCanLoadOnlyActiveTeachersForOfferingEditor() {
        teachers.save(new TeacherProfile("教师002", "teacher_002", "赵老师", "计算机学院", "讲师", false));

        Message response = handler.handle(Message.request("active-teachers",
                MessageType.COURSE_TEACHER_DIRECTORY_V1,
                new CourseTeacherDirectoryV1Command(academicAdmin.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<?> profiles = (List<?>) response.getPayload();
        assertEquals(1, profiles.size());
        assertEquals("教师001", ((TeacherProfile) profiles.get(0)).getTeacherId());
    }

    @Test
    void studentCannotLoadTeacherDirectoryForOfferingEditor() {
        Message response = handler.handle(Message.request("active-teachers-forbidden",
                MessageType.COURSE_TEACHER_DIRECTORY_V1,
                new CourseTeacherDirectoryV1Command(student.getToken())));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
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
        Session unboundTeacher = login(users, "teacher_unbound", Role.TEACHER);
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

    @Test
    void teacherCanOpenDraftAndSaveOnlyActiveStudentsGrade() {
        Message opened = handler.handle(Message.request("open-grade-draft",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.openDraft(teacherOne.getToken(), "OFFER-JAVA-01")));

        assertEquals(StatusCode.OK, opened.getStatusCode());
        TeachingGradeDraft draft = (TeachingGradeDraft) opened.getPayload();
        assertEquals("教师001", draft.getSubmission().getTeacherId());
        assertTrue(draft.getEntries().isEmpty());
        assertEquals(1, draft.getRoster().getStudents().size());

        Message saved = handler.handle(Message.request("save-grade-entry",
                MessageType.COURSE_GRADE_DRAFT_V2, CourseGradeDraftV2Command.saveEntry(
                        teacherOne.getToken(), "OFFER-JAVA-01", "STU-001", 91)));

        assertEquals(StatusCode.OK, saved.getStatusCode());
        TeachingGradeDraft savedDraft = (TeachingGradeDraft) saved.getPayload();
        assertEquals(1, savedDraft.getEntries().size());
        assertEquals(91, savedDraft.getEntries().get(0).getScore());
        assertEquals(SelectionType.RETAKE, savedDraft.getEntries().get(0).getSelectionType());
    }

    @Test
    void rejectsAnotherTeachersOrDroppedStudentsGradeEntry() {
        Message otherTeacher = handler.handle(Message.request("other-teacher-save",
                MessageType.COURSE_GRADE_DRAFT_V2, CourseGradeDraftV2Command.saveEntry(
                        teacherTwo.getToken(), "OFFER-JAVA-01", "STU-001", 85)));
        Message droppedStudent = handler.handle(Message.request("dropped-student-save",
                MessageType.COURSE_GRADE_DRAFT_V2, CourseGradeDraftV2Command.saveEntry(
                        teacherOne.getToken(), "OFFER-JAVA-01", "STU-002", 85)));

        assertEquals(StatusCode.FORBIDDEN, otherTeacher.getStatusCode());
        assertEquals(StatusCode.NOT_FOUND, droppedStudent.getStatusCode());
    }

    @Test
    void studentCannotOpenTeachersGradeDraft() {
        Message response = handler.handle(Message.request("student-open-grade-draft",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.openDraft(student.getToken(), "OFFER-JAVA-01")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void teacherCanSubmitOnlyCompleteRosterAndLaterReplacePendingGrade() {
        Message incomplete = handler.handle(Message.request("submit-incomplete-grade-draft",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));
        assertEquals(StatusCode.CONFLICT, incomplete.getStatusCode());

        handler.handle(Message.request("save-complete-grade-draft", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.saveEntry(teacherOne.getToken(), "OFFER-JAVA-01",
                        "STU-001", 76)));
        Message submitted = handler.handle(Message.request("submit-complete-grade-draft",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));

        assertEquals(StatusCode.OK, submitted.getStatusCode());
        TeachingGradeDraft pending = (TeachingGradeDraft) submitted.getPayload();
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                pending.getSubmission().getStatus());

        Message corrected = handler.handle(Message.request("replace-pending-grade",
                MessageType.COURSE_GRADE_DRAFT_V2, CourseGradeDraftV2Command.saveEntry(
                        teacherOne.getToken(), "OFFER-JAVA-01", "STU-001", 89)));
        assertEquals(StatusCode.OK, corrected.getStatusCode());
        TeachingGradeDraft latest = (TeachingGradeDraft) corrected.getPayload();
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                latest.getSubmission().getStatus());
        assertEquals(89, latest.getEntries().get(0).getScore());

        String submissionId = latest.getSubmission().getSubmissionId();
        Message reviewBeforeResubmit = handler.handle(Message.request("review-snapshot-v1",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.viewDetail(
                        academicAdmin.getToken(), submissionId)));
        assertEquals(StatusCode.OK, reviewBeforeResubmit.getStatusCode());
        TeachingGradeDraft snapshotV1 = (TeachingGradeDraft) reviewBeforeResubmit.getPayload();
        assertEquals(Integer.valueOf(1), snapshotV1.getReviewVersionNo());
        assertEquals(76, snapshotV1.getEntries().get(0).getScore());

        Message resubmitted = handler.handle(Message.request("submit-revised-grade",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));
        assertEquals(StatusCode.OK, resubmitted.getStatusCode());
        Message reviewAfterResubmit = handler.handle(Message.request("review-snapshot-v2",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.viewDetail(
                        academicAdmin.getToken(), submissionId)));
        TeachingGradeDraft snapshotV2 = (TeachingGradeDraft) reviewAfterResubmit.getPayload();
        assertEquals(Integer.valueOf(2), snapshotV2.getReviewVersionNo());
        assertEquals(89, snapshotV2.getEntries().get(0).getScore());
    }

    @Test
    void teacherCanAtomicallyImportOwnActiveStudentsCsvGrades() {
        byte[] content = "学号,成绩\nSTU-001,93\n".getBytes(StandardCharsets.UTF_8);
        Message response = handler.handle(Message.request("import-grades",
                MessageType.COURSE_GRADE_IMPORT_V2, new CourseGradeImportV2Command(
                        teacherOne.getToken(), "OFFER-JAVA-01", "java-grades.csv", content)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        GradeImportResult result = (GradeImportResult) response.getPayload();
        assertEquals(1, result.getImportedCount());
        assertEquals(93, result.getDraft().getEntries().get(0).getScore());
        assertEquals(SelectionType.RETAKE, result.getDraft().getEntries().get(0).getSelectionType());
    }

    @Test
    void importRejectsDroppedStudentBeforeCreatingOrChangingGrades() {
        byte[] content = "学号,成绩\nSTU-002,70\n".getBytes(StandardCharsets.UTF_8);
        Message response = handler.handle(Message.request("import-dropped",
                MessageType.COURSE_GRADE_IMPORT_V2, new CourseGradeImportV2Command(
                        teacherOne.getToken(), "OFFER-JAVA-01", "java-grades.csv", content)));

        assertEquals(StatusCode.NOT_FOUND, response.getStatusCode());
        // 每个测试独立初始化；失败的导入不能因文件中无效学生而创建空草稿。
        assertEquals(StatusCode.NOT_FOUND, gradeSubmissions.findByOffering("OFFER-JAVA-01")
                .getStatus());
    }

    @Test
    void academicAdminCanApproveLatestPendingGradesAndPublishFormalResult() {
        handler.handle(Message.request("save-grade", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.saveEntry(teacherOne.getToken(), "OFFER-JAVA-01",
                        "STU-001", 88)));
        handler.handle(Message.request("submit-grade", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));

        Message pending = handler.handle(Message.request("pending-grades",
                MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.listPending(academicAdmin.getToken())));
        assertEquals(StatusCode.OK, pending.getStatusCode());
        assertEquals(1, ((List<?>) pending.getPayload()).size());
        String submissionId = ((cn.vcampus.course.GradeSubmission) ((List<?>) pending.getPayload())
                .get(0)).getSubmissionId();

        Message approved = handler.handle(Message.request("approve-grade",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.approve(
                        academicAdmin.getToken(), submissionId, "审核通过")));
        assertEquals(StatusCode.OK, approved.getStatusCode());
        assertEquals(GradeSubmissionStatus.APPROVED,
                ((cn.vcampus.course.GradeSubmission) approved.getPayload()).getStatus());
        assertEquals(1, formalResults.historyFor("STU-001").getData().size());
        assertEquals(88, formalResults.historyFor("STU-001").getData().get(0).getScore());
        assertEquals("重修", formalResults.historyFor("STU-001").getData().get(0).getAttemptType());

        Message audit = handler.handle(Message.request("grade-audit",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.viewAudit(
                        academicAdmin.getToken(), submissionId)));
        assertEquals(StatusCode.OK, audit.getStatusCode());
        List<?> events = (List<?>) audit.getPayload();
        assertEquals(2, events.size());
        assertEquals(GradeSubmissionAuditAction.SUBMITTED,
                ((GradeSubmissionAuditRecord) events.get(0)).getAction());
        assertEquals(GradeSubmissionAuditAction.APPROVED,
                ((GradeSubmissionAuditRecord) events.get(1)).getAction());

        Message repeated = handler.handle(Message.request("approve-grade-again",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.approve(
                        academicAdmin.getToken(), submissionId, "重复通过")));
        assertEquals(StatusCode.CONFLICT, repeated.getStatusCode());
        assertEquals(1, formalResults.historyFor("STU-001").getData().size());
    }

    @Test
    void academicAdminCanReturnPendingGradeWithRemarkButTeacherCannotReview() {
        handler.handle(Message.request("save-grade", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.saveEntry(teacherOne.getToken(), "OFFER-JAVA-01",
                        "STU-001", 58)));
        Message submitted = handler.handle(Message.request("submit-grade",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));
        String submissionId = ((TeachingGradeDraft) submitted.getPayload()).getSubmission()
                .getSubmissionId();

        Message forbidden = handler.handle(Message.request("teacher-review",
                MessageType.COURSE_GRADE_REVIEW_V2,
                CourseGradeReviewV2Command.viewDetail(teacherOne.getToken(), submissionId)));
        Message returned = handler.handle(Message.request("return-grade",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.returnForRevision(
                        academicAdmin.getToken(), submissionId, "请确认该生补考成绩")));

        assertEquals(StatusCode.FORBIDDEN, forbidden.getStatusCode());
        assertEquals(StatusCode.OK, returned.getStatusCode());
        assertEquals(GradeSubmissionStatus.RETURNED,
                ((cn.vcampus.course.GradeSubmission) returned.getPayload()).getStatus());
        assertEquals("请确认该生补考成绩",
                ((cn.vcampus.course.GradeSubmission) returned.getPayload()).getReviewRemark());
        assertTrue(formalResults.historyFor("STU-001").getData().isEmpty());
    }

    @Test
    void academicAdminCanReturnApprovedGradesForCorrectionAndTeacherCanResubmit() {
        Message saved = handler.handle(Message.request("save-grade", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.saveEntry(teacherOne.getToken(), "OFFER-JAVA-01",
                        "STU-001", 88)));
        assertEquals(StatusCode.OK, saved.getStatusCode());
        Message submitted = handler.handle(Message.request("submit-grade", MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));
        String submissionId = ((TeachingGradeDraft) submitted.getPayload()).getSubmission()
                .getSubmissionId();
        assertEquals(StatusCode.OK, handler.handle(Message.request("approve-grade",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.approve(
                        academicAdmin.getToken(), submissionId, "审核通过"))).getStatusCode());
        assertEquals(1, formalResults.historyFor("STU-001").getData().size());

        Message returned = handler.handle(Message.request("return-approved-grade",
                MessageType.COURSE_GRADE_REVIEW_V2, CourseGradeReviewV2Command.returnForRevision(
                        academicAdmin.getToken(), submissionId, "请修正分数后重新提交")));
        assertEquals(StatusCode.OK, returned.getStatusCode());
        assertEquals(GradeSubmissionStatus.RETURNED,
                ((cn.vcampus.course.GradeSubmission) returned.getPayload()).getStatus());
        assertTrue(formalResults.historyFor("STU-001").getData().isEmpty());

        assertEquals(StatusCode.OK, handler.handle(Message.request("correct-grade",
                MessageType.COURSE_GRADE_DRAFT_V2, CourseGradeDraftV2Command.saveEntry(
                        teacherOne.getToken(), "OFFER-JAVA-01", "STU-001", 92))).getStatusCode());
        Message resubmitted = handler.handle(Message.request("resubmit-grade",
                MessageType.COURSE_GRADE_DRAFT_V2,
                CourseGradeDraftV2Command.submitForReview(teacherOne.getToken(), "OFFER-JAVA-01")));
        assertEquals(StatusCode.OK, resubmitted.getStatusCode());
        assertEquals(GradeSubmissionStatus.PENDING_REVIEW,
                ((TeachingGradeDraft) resubmitted.getPayload()).getSubmission().getStatus());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}

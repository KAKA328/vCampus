package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/** 为本地 Socket 演示提供覆盖学生、教师和教务流程的选课测试数据。 */
public final class CourseSelectionDemoFactory {
    public static final String DEMO_TERM = "2026-2027-1";

    private CourseSelectionDemoFactory() {
    }

    public static CourseSelectionService createService() {
        return createModule().getSelectionService();
    }

    /** 创建供单元测试使用的最小内存服务，不预置选课记录或成绩提交单。 */
    public static CourseSelectionModule createModule() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService();
        catalog.create(new Course("JAVA101", "Java 程序设计", 3));
        catalog.create(new Course("DB101", "数据库原理", 3));
        catalog.create(new Course("GE101", "大学写作", 2));

        InMemoryTrainingPlanService plans = new InMemoryTrainingPlanService(catalog);
        plans.create(new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026, Arrays.asList(
                new TrainingPlanCourse("JAVA101", 1, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("DB101", 1, SelectionType.ELECTIVE, false))));
        plans.create(new TrainingPlan("PLAN-CN-2026", "汉语言文学", 2026, Arrays.asList(
                new TrainingPlanCourse("GE101", 1, SelectionType.ELECTIVE, true))));
        plans.create(new TrainingPlan("PLAN-SE-2023", "软件工程", 2023, Arrays.asList(
                new TrainingPlanCourse("JAVA101", 7, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("DB101", 7, SelectionType.ELECTIVE, false))));
        plans.changeStatus("PLAN-CS-2026", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("PLAN-CN-2026", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("PLAN-SE-2023", TrainingPlanStatus.PUBLISHED);

        LocalDateTime now = LocalDateTime.now();
        InMemorySelectionRoundService rounds = new InMemorySelectionRoundService(Arrays.asList(
                new SelectionRound("ROUND-INITIAL", DEMO_TERM, SelectionRoundType.INITIAL,
                        now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN),
                new SelectionRound("ROUND-RETAKE", DEMO_TERM, SelectionRoundType.RETAKE,
                        now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN)));
        InMemoryCourseSelectionRecordService records = new InMemoryCourseSelectionRecordService();
        InMemoryCourseOfferingService offerings = new InMemoryCourseOfferingService(catalog, records);
        offerings.create(offering("OFFER-JAVA-01", "JAVA101", "教师001", "周一 1-2 节", "A201",
                DayOfWeek.MONDAY, 1, 2, 40, 20, 10));
        offerings.create(offering("OFFER-DB-01", "DB101", "教师002", "周二 3-4 节", "A202",
                DayOfWeek.TUESDAY, 3, 4, 20, 30, 10));
        offerings.create(offering("OFFER-GE-01", "GE101", "教师003", "周三 5-6 节", "A203",
                DayOfWeek.WEDNESDAY, 5, 6, 0, 10, 20));

        CourseSelectionService selectionService = new DefaultCourseSelectionService(catalog, plans,
                rounds, offerings, records, new DefaultCourseOfferingCapacityService(offerings, records),
                new ScheduleConflictDetector());
        return new CourseSelectionModule(selectionService, catalog, offerings, rounds, records,
                new InMemoryGradeSubmissionService(), plans);
    }

    /** 创建覆盖学生、教师和教务完整流程的本地演示服务。 */
    public static CourseSelectionModule createRichDemoModule() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService();
        catalog.create(new Course("JAVA101", "Java 程序设计", 3));
        catalog.create(new Course("DB101", "数据库原理", 3));
        catalog.create(new Course("NET101", "计算机网络", 3));
        catalog.create(new Course("GE101", "大学写作", 2));
        catalog.create(new Course("OS101", "操作系统", 3));
        catalog.create(new Course("MATH101", "高等数学", 4));

        InMemoryTrainingPlanService plans = new InMemoryTrainingPlanService(catalog);
        plans.create(new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026, Arrays.asList(
                new TrainingPlanCourse("JAVA101", 1, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("DB101", 1, SelectionType.ELECTIVE, false),
                new TrainingPlanCourse("NET101", 1, SelectionType.REQUIRED, false))));
        plans.create(new TrainingPlan("PLAN-CN-2026", "汉语言文学", 2026, Arrays.asList(
                new TrainingPlanCourse("GE101", 1, SelectionType.ELECTIVE, true))));
        plans.create(new TrainingPlan("PLAN-SE-2023", "软件工程", 2023, Arrays.asList(
                new TrainingPlanCourse("JAVA101", 7, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("DB101", 7, SelectionType.ELECTIVE, false))));
        plans.changeStatus("PLAN-CS-2026", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("PLAN-CN-2026", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("PLAN-SE-2023", TrainingPlanStatus.PUBLISHED);

        LocalDateTime now = LocalDateTime.now();
        InMemorySelectionRoundService rounds = new InMemorySelectionRoundService(Arrays.asList(
                new SelectionRound("ROUND-INITIAL", DEMO_TERM, SelectionRoundType.INITIAL,
                        now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN),
                new SelectionRound("ROUND-RETAKE", DEMO_TERM, SelectionRoundType.RETAKE,
                        now.minusDays(1), now.plusDays(30), SelectionRoundStatus.OPEN)));
        InMemoryCourseSelectionRecordService records = new InMemoryCourseSelectionRecordService(
                Arrays.asList(
                        new CourseSelectionRecord("SEL-DEMO-JAVA-001", "20260001",
                                "OFFER-JAVA-01", "ROUND-INITIAL", SelectionType.REQUIRED,
                                now.minusDays(2)),
                        new CourseSelectionRecord("SEL-DEMO-DB-RETAKE", "20230003",
                                "OFFER-DB-01", "ROUND-RETAKE", SelectionType.RETAKE,
                                now.minusDays(2)),
                        new CourseSelectionRecord("SEL-DEMO-DB-ELECTIVE", "20260004",
                                "OFFER-DB-01", "ROUND-INITIAL", SelectionType.ELECTIVE,
                                now.minusDays(1)),
                        new CourseSelectionRecord("SEL-DEMO-GE-CROSS", "20260005",
                                "OFFER-GE-01", "ROUND-INITIAL", SelectionType.CROSS_MAJOR,
                                now.minusHours(12))));
        InMemoryCourseOfferingService offerings = new InMemoryCourseOfferingService(catalog, records);
        offerings.create(offering("OFFER-JAVA-01", "JAVA101", "教师001", "周一 1-2 节", "A201",
                DayOfWeek.MONDAY, 1, 2, 40, 20, 10));
        offerings.create(offering("OFFER-JAVA-02", "JAVA101", "教师001", "周三 3-4 节", "A202",
                DayOfWeek.WEDNESDAY, 3, 4, 40, 20, 10));
        offerings.create(offering("OFFER-DB-01", "DB101", "教师002", "周二 3-4 节", "A202",
                DayOfWeek.TUESDAY, 3, 4, 20, 30, 10));
        offerings.create(offering("OFFER-NET-01", "NET101", "教师001", "周四 5-6 节", "B301",
                DayOfWeek.THURSDAY, 5, 6, 30, 10, 5));
        offerings.create(offering("OFFER-NET-CLASH", "NET101", "教师001", "周一 1-2 节", "A205",
                DayOfWeek.MONDAY, 1, 2, 30, 10, 5));
        offerings.create(offering("OFFER-GE-01", "GE101", "教师003", "周三 5-6 节", "A203",
                DayOfWeek.WEDNESDAY, 5, 6, 0, 10, 20));
        offerings.create(closedOffering("OFFER-OS-DRAFT", "OS101", "教师002", "周五 1-2 节",
                "B302", DayOfWeek.FRIDAY, 1, 2));

        CourseSelectionService selectionService = new DefaultCourseSelectionService(catalog, plans,
                rounds, offerings, records, new DefaultCourseOfferingCapacityService(offerings, records),
                new ScheduleConflictDetector());
        InMemoryGradeSubmissionService gradeSubmissions = new InMemoryGradeSubmissionService();
        createGradeReviewDemo(gradeSubmissions, now);
        return new CourseSelectionModule(selectionService, catalog, offerings, rounds, records,
                gradeSubmissions, plans);
    }

    /** 创建供单元测试使用的最小学生资料。 */
    public static StudentSelectionProfileProvider createProfileProvider() {
        return new InMemoryStudentSelectionProfileProvider(Arrays.asList(
                new StudentSelectionProfile("demo_student", "20260001", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet())));
    }

    /** 创建与完整内存演示数据对应的学生资料。 */
    public static StudentSelectionProfileProvider createRichProfileProvider() {
        return new InMemoryStudentSelectionProfileProvider(Arrays.asList(
                new StudentSelectionProfile("demo_student", "20260001", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet()),
                new StudentSelectionProfile("demo_student_new", "20260002", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet()),
                new StudentSelectionProfile("demo_student_retake", "20230003", "软件工程", 2023,
                        "在读", DEMO_TERM, 7, Collections.singleton("DB101")),
                new StudentSelectionProfile("demo_student_elective", "20260004", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet()),
                new StudentSelectionProfile("demo_student_cross", "20260005", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet())));
    }

    private static CourseOffering offering(String offeringId, String courseId, String teacherId,
            String schedule, String location, DayOfWeek day, int startPeriod, int endPeriod,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        return new CourseOffering(offeringId, courseId, DEMO_TERM, teacherId, schedule, location,
                requiredCapacity, electiveCapacity, crossMajorCapacity, CourseOfferingStatus.OPEN)
                .withMeetingSchedule(new CourseSchedule(Arrays.asList(
                        new CourseMeeting(day, startPeriod, endPeriod, location))))
                .withTeacherName(demoTeacherName(teacherId));
    }

    private static CourseOffering closedOffering(String offeringId, String courseId, String teacherId,
            String schedule, String location, DayOfWeek day, int startPeriod, int endPeriod) {
        return new CourseOffering(offeringId, courseId, DEMO_TERM, teacherId, schedule, location,
                30, 10, 5, CourseOfferingStatus.DRAFT).withMeetingSchedule(new CourseSchedule(
                        Arrays.asList(new CourseMeeting(day, startPeriod, endPeriod, location))))
                .withTeacherName(demoTeacherName(teacherId));
    }

    private static String demoTeacherName(String teacherId) {
        if ("教师001".equals(teacherId)) return "李明远";
        if ("教师002".equals(teacherId)) return "周雨桐";
        if ("教师003".equals(teacherId)) return "陈思涵";
        return teacherId;
    }

    /** 预置一份待审核成绩和一份已退回草稿，覆盖教师与教务两端的完整联调。 */
    private static void createGradeReviewDemo(InMemoryGradeSubmissionService service,
            LocalDateTime now) {
        GradeSubmission javaSubmission = GradeSubmission.draft("GRADE-DEMO-JAVA-001",
                "OFFER-JAVA-01", "教师001", now.minusHours(5));
        requireDemoData(service.createDraft(javaSubmission));
        requireDemoData(service.saveDraftEntry(new GradeEntry(javaSubmission.getSubmissionId(),
                "20260001", SelectionType.REQUIRED, 92, now.minusHours(4))));
        requireDemoData(service.submitForReview(javaSubmission.getSubmissionId()));

        GradeSubmission databaseSubmission = GradeSubmission.draft("GRADE-DEMO-DB-001",
                "OFFER-DB-01", "教师002", now.minusHours(4));
        requireDemoData(service.createDraft(databaseSubmission));
        requireDemoData(service.saveDraftEntries(Arrays.asList(
                new GradeEntry(databaseSubmission.getSubmissionId(), "20230003",
                        SelectionType.RETAKE, 58, now.minusHours(3)),
                new GradeEntry(databaseSubmission.getSubmissionId(), "20260004",
                        SelectionType.ELECTIVE, 88, now.minusHours(3)))));
        requireDemoData(service.submitForReview(databaseSubmission.getSubmissionId()));
        requireDemoData(service.review(databaseSubmission.getSubmissionId(),
                GradeReviewDecision.RETURN, "demo_academic_admin", "请复核重修学生的成绩依据"));
    }

    private static <T> T requireDemoData(ServiceResult<T> result) {
        if (result.getStatus() != StatusCode.OK) {
            throw new IllegalStateException("cannot initialize course demo data: "
                    + result.getStatus() + " " + result.getMessage());
        }
        return result.getData();
    }
}

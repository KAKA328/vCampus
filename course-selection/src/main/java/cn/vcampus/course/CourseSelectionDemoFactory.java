package cn.vcampus.course;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

/** 为本地 Socket 演示提供一套符合当前业务规则的选课服务和学生资料。 */
public final class CourseSelectionDemoFactory {
    public static final String DEMO_TERM = "2026-2027-1";

    private CourseSelectionDemoFactory() {
    }

    public static CourseSelectionService createService() {
        return createModule().getSelectionService();
    }

    /** 创建一组共享课程目录、教学班和选课记录的本地演示服务。 */
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
                new InMemoryGradeSubmissionService());
    }

    public static StudentSelectionProfileProvider createProfileProvider() {
        return new InMemoryStudentSelectionProfileProvider(Arrays.asList(
                new StudentSelectionProfile("demo_student", "20260001", "计算机科学与技术", 2026,
                        "在读", DEMO_TERM, 1, Collections.<String>emptySet())));
    }

    private static CourseOffering offering(String offeringId, String courseId, String teacherId,
            String schedule, String location, DayOfWeek day, int startPeriod, int endPeriod,
            int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        return new CourseOffering(offeringId, courseId, DEMO_TERM, teacherId, schedule, location,
                requiredCapacity, electiveCapacity, crossMajorCapacity, CourseOfferingStatus.OPEN)
                .withMeetingSchedule(new CourseSchedule(Arrays.asList(
                        new CourseMeeting(day, startPeriod, endPeriod, location))));
    }
}

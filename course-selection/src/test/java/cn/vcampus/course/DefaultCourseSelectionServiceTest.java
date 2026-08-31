package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultCourseSelectionServiceTest {
    private static final String TERM = "2026-2027-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 10, 0);
    private DefaultCourseSelectionService service;
    private StudentSelectionProfile student;
    private StudentSelectionProfile retakeStudent;

    @BeforeEach
    void setUp() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService();
        catalog.create(new Course("CS101", "程序设计基础", 3));
        catalog.create(new Course("CS102", "数据结构", 3));
        catalog.create(new Course("GE101", "大学写作", 2));
        InMemoryTrainingPlanService plans = new InMemoryTrainingPlanService(catalog);
        plans.create(new TrainingPlan("PLAN-CS", "计算机科学与技术", 2026, Arrays.asList(
                new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("CS102", 1, SelectionType.ELECTIVE, false))));
        plans.create(new TrainingPlan("PLAN-CN", "汉语言文学", 2026, Arrays.asList(
                new TrainingPlanCourse("GE101", 1, SelectionType.ELECTIVE, true))));
        plans.changeStatus("PLAN-CS", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("PLAN-CN", TrainingPlanStatus.PUBLISHED);
        InMemorySelectionRoundService rounds = new InMemorySelectionRoundService(Arrays.asList(
                round("ROUND-INITIAL", SelectionRoundType.INITIAL),
                round("ROUND-RETAKE", SelectionRoundType.RETAKE)));
        InMemoryCourseOfferingService offerings = new InMemoryCourseOfferingService(catalog);
        offerings.create(offering("OFFER-CS101-A", "CS101", DayOfWeek.MONDAY, 1, 2, 1, 2, 1));
        offerings.create(offering("OFFER-CS101-B", "CS101", DayOfWeek.TUESDAY, 1, 2, 1, 2, 1));
        offerings.create(offering("OFFER-CS102", "CS102", DayOfWeek.MONDAY, 2, 3, 1, 2, 1));
        offerings.create(offering("OFFER-GE101", "GE101", DayOfWeek.WEDNESDAY, 1, 2, 0, 1, 2));
        InMemoryCourseSelectionRecordService records = new InMemoryCourseSelectionRecordService();
        service = new DefaultCourseSelectionService(catalog, plans, rounds, offerings, records,
                new DefaultCourseOfferingCapacityService(offerings, records),
                new ScheduleConflictDetector());
        student = profile("user-001", "STU-001", Collections.<String>emptySet());
        retakeStudent = profile("user-002", "STU-002", Collections.singleton("CS101"));
    }

    @Test
    void initialRoundShowsOwnPlanAndCrossMajorOfferings() {
        ServiceResult<List<SelectionRound>> rounds = service.listAvailableRounds(student, NOW);
        ServiceResult<List<SelectableCourseOffering>> offerings = service.listAvailableOfferings(student,
                "ROUND-INITIAL", NOW);

        assertEquals(1, rounds.getData().size());
        assertEquals(4, offerings.getData().size());
        assertEquals(SelectionType.REQUIRED, offerings.getData().get(0).getSelectionType());
        assertEquals(SelectionType.ELECTIVE, offerings.getData().get(2).getSelectionType());
        assertEquals("GE101", offerings.getData().get(3).getCourse().getCourseId());
        assertEquals(SelectionType.CROSS_MAJOR, offerings.getData().get(3).getSelectionType());
    }

    @Test
    void selectionChecksEligibilityCapacityDuplicateCourseAndSchedule() {
        ServiceResult<CourseSelectionRecord> selectResult = service.select(student, "ROUND-INITIAL",
                "OFFER-CS101-A", NOW);

        assertEquals(StatusCode.OK, selectResult.getStatus());
        assertEquals(SelectionType.REQUIRED, selectResult.getData().getSelectionType());
        assertEquals(StatusCode.CONFLICT, service.select(student, "ROUND-INITIAL",
                "OFFER-CS101-B", NOW).getStatus());
        assertEquals(StatusCode.CONFLICT, service.select(student, "ROUND-INITIAL",
                "OFFER-CS102", NOW).getStatus());
        assertEquals(1, service.listSelectedOfferings(student).getData().size());
    }

    @Test
    void retakeRoundUsesRequiredCapacityAndRequiresPendingRetakeCourse() {
        assertEquals(StatusCode.FORBIDDEN, service.listAvailableOfferings(student, "ROUND-RETAKE",
                NOW).getStatus());

        ServiceResult<CourseSelectionRecord> result = service.select(retakeStudent, "ROUND-RETAKE",
                "OFFER-CS101-A", NOW);

        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(SelectionType.RETAKE, result.getData().getSelectionType());
    }

    @Test
    void dropRequiresRecordOwnerAndOpenRound() {
        CourseSelectionRecord record = service.select(student, "ROUND-INITIAL", "OFFER-CS101-A", NOW)
                .getData();

        assertEquals(StatusCode.FORBIDDEN, service.drop(retakeStudent, record.getRecordId(), NOW).getStatus());
        assertEquals(StatusCode.OK, service.drop(student, record.getRecordId(), NOW).getStatus());
        assertEquals(0, service.listSelectedOfferings(student).getData().size());
    }

    private static SelectionRound round(String id, SelectionRoundType type) {
        return new SelectionRound(id, TERM, type, NOW.minusDays(1), NOW.plusDays(1),
                SelectionRoundStatus.OPEN);
    }

    private static CourseOffering offering(String id, String courseId, DayOfWeek day, int start,
            int end, int requiredCapacity, int electiveCapacity, int crossMajorCapacity) {
        return new CourseOffering(id, courseId, TERM, "TEACHER-001", day + " " + start + "-" + end,
                "A201", requiredCapacity, electiveCapacity, crossMajorCapacity,
                CourseOfferingStatus.OPEN).withMeetingSchedule(new CourseSchedule(Arrays.asList(
                        new CourseMeeting(day, start, end, "A201"))));
    }

    private static StudentSelectionProfile profile(String userId, String studentId,
            java.util.Set<String> retakes) {
        return new StudentSelectionProfile(userId, studentId, "计算机科学与技术", 2026, "在读",
                TERM, 1, retakes);
    }
}

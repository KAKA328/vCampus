package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TrainingPlanTest {

    @Test
    void storesImmutableCourseRequirementsForOneMajorAndEnrollmentYear() {
        TrainingPlanCourse required = new TrainingPlanCourse("CS101", 1,
                SelectionType.REQUIRED, false);
        TrainingPlanCourse crossMajor = new TrainingPlanCourse("GE201", 1,
                SelectionType.ELECTIVE, true);
        TrainingPlan plan = new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026,
                Arrays.asList(required, crossMajor));

        assertEquals("PLAN-CS-2026", plan.getPlanId());
        assertEquals("计算机科学与技术", plan.getMajorName());
        assertEquals(2026, plan.getEnrollmentYear());
        assertEquals(TrainingPlanStatus.DRAFT, plan.getStatus());
        assertEquals(2, plan.getCourses().size());
        assertEquals(true, plan.getCourses().get(1).isCrossMajorAllowed());
        assertThrows(UnsupportedOperationException.class, () -> plan.getCourses().clear());
    }

    @Test
    void rejectsRetakeAndDuplicateCoursesInTrainingPlan() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingPlanCourse("CS101", 1,
                SelectionType.RETAKE, false));

        TrainingPlanCourse course = new TrainingPlanCourse("CS101", 1,
                SelectionType.REQUIRED, false);
        assertThrows(IllegalArgumentException.class, () -> new TrainingPlan("PLAN-CS-2026",
                "计算机科学与技术", 2026, Arrays.asList(course, course)));
    }

    @Test
    void returnsNewPlanWhenCourseRequirementIsAddedUpdatedOrRemoved() {
        TrainingPlan initial = new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026,
                Arrays.asList(new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false),
                        new TrainingPlanCourse("CS102", 1, SelectionType.REQUIRED, false)));

        TrainingPlan updated = initial.withCourse(new TrainingPlanCourse("CS101", 2,
                SelectionType.ELECTIVE, true));
        TrainingPlan added = updated.withCourse(new TrainingPlanCourse("GE201", 2,
                SelectionType.ELECTIVE, true));
        TrainingPlan removed = added.withoutCourse("CS102");

        assertEquals(1, initial.getCourses().get(0).getRecommendedTerm());
        assertEquals(2, updated.getCourses().get(0).getRecommendedTerm());
        assertEquals(3, added.getCourses().size());
        assertEquals(2, removed.getCourses().size());
        assertThrows(IllegalArgumentException.class, () -> new TrainingPlan("ONLY-ONE", "软件工程",
                2026, Arrays.asList(new TrainingPlanCourse("SE101", 1,
                        SelectionType.REQUIRED, false))).withoutCourse("SE101"));
    }

    @Test
    void keepsCourseRequirementsWhenStatusChanges() {
        TrainingPlan initial = new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026,
                Arrays.asList(new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false)));

        TrainingPlan published = initial.withStatus(TrainingPlanStatus.PUBLISHED);

        assertEquals(TrainingPlanStatus.DRAFT, initial.getStatus());
        assertEquals(TrainingPlanStatus.PUBLISHED, published.getStatus());
        assertEquals("CS101", published.getCourses().get(0).getCourseId());
    }
}

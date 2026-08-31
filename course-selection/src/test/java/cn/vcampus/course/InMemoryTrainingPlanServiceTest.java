package cn.vcampus.course;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryTrainingPlanServiceTest {

    @Test
    void createsPlanAndListsOnlyCoursesForRequestedRecommendedTerm() {
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService();
        TrainingPlan plan = computerSciencePlan("PLAN-CS-2026");

        ServiceResult<TrainingPlan> createResult = service.create(plan);
        ServiceResult<TrainingPlan> publishResult = service.changeStatus("PLAN-CS-2026",
                TrainingPlanStatus.PUBLISHED);
        ServiceResult<List<TrainingPlanCourse>> listResult = service
                .listCoursesByRecommendedTerm(" 计算机科学与技术 ", 2026, 1);

        assertEquals(StatusCode.OK, createResult.getStatus());
        assertEquals(StatusCode.OK, publishResult.getStatus());
        assertEquals(StatusCode.OK, listResult.getStatus());
        assertEquals(2, listResult.getData().size());
        assertEquals("CS101", listResult.getData().get(0).getCourseId());
        assertEquals(SelectionType.ELECTIVE, listResult.getData().get(1).getSelectionType());
        assertEquals(true, listResult.getData().get(1).isCrossMajorAllowed());
    }

    @Test
    void treatsMajorAndEnrollmentYearAsUniquePlanScope() {
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService(Arrays.asList(
                computerSciencePlan("PLAN-CS-2026")));
        TrainingPlan sameScope = new TrainingPlan("PLAN-CS-2026-NEW", "计算机科学与技术", 2026,
                Arrays.asList(new TrainingPlanCourse("CS102", 1, SelectionType.REQUIRED, false)));
        TrainingPlan differentYear = new TrainingPlan("PLAN-CS-2027", "计算机科学与技术", 2027,
                Arrays.asList(new TrainingPlanCourse("CS102", 1, SelectionType.REQUIRED, false)));

        assertEquals(StatusCode.CONFLICT, service.create(sameScope).getStatus());
        assertEquals(StatusCode.OK, service.create(differentYear).getStatus());
    }

    @Test
    void reportsInvalidAndUnknownPlanRequests() {
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService();

        assertEquals(StatusCode.BAD_REQUEST, service.create(null).getStatus());
        assertEquals(StatusCode.BAD_REQUEST,
                service.findByMajorAndEnrollmentYear(" ", 2026).getStatus());
        assertEquals(StatusCode.BAD_REQUEST,
                service.listCoursesByRecommendedTerm("计算机科学与技术", 2026, 0).getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.findByMajorAndEnrollmentYear("计算机科学与技术", 2026).getStatus());
    }

    @Test
    void letsAcademicStaffListAndMaintainCourseRequirements() {
        TrainingPlan plan = computerSciencePlan("PLAN-CS-2026");
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService(Arrays.asList(plan));

        ServiceResult<List<TrainingPlan>> listResult = service.listAll();
        ServiceResult<TrainingPlan> updateResult = service.saveCourse("PLAN-CS-2026",
                new TrainingPlanCourse("CS101", 2, SelectionType.ELECTIVE, true));
        ServiceResult<TrainingPlan> addResult = service.saveCourse("PLAN-CS-2026",
                new TrainingPlanCourse("GE301", 3, SelectionType.ELECTIVE, true));
        ServiceResult<TrainingPlan> removeResult = service.removeCourse("PLAN-CS-2026", "CS201");

        assertEquals(1, listResult.getData().size());
        assertEquals(StatusCode.OK, updateResult.getStatus());
        assertEquals(2, updateResult.getData().getCourses().get(0).getRecommendedTerm());
        assertEquals(StatusCode.OK, addResult.getStatus());
        assertEquals(4, addResult.getData().getCourses().size());
        assertEquals(StatusCode.OK, removeResult.getStatus());
        assertEquals(3, removeResult.getData().getCourses().size());
    }

    @Test
    void rejectsInvalidTrainingPlanMaintenanceRequests() {
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService(Arrays.asList(
                computerSciencePlan("PLAN-CS-2026")));

        assertEquals(StatusCode.BAD_REQUEST, service.saveCourse("PLAN-CS-2026", null).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.saveCourse("UNKNOWN", new TrainingPlanCourse("CS301",
                3, SelectionType.REQUIRED, false)).getStatus());
        assertEquals(StatusCode.BAD_REQUEST,
                service.removeCourse("PLAN-CS-2026", "UNKNOWN").getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.removeCourse("UNKNOWN", "CS101").getStatus());
    }

    @Test
    void allowsOnlyDraftPlanToBeMaintainedAndPublishedPlanToBeQueriedByStudents() {
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService(Arrays.asList(
                computerSciencePlan("PLAN-CS-2026")));

        assertEquals(StatusCode.NOT_FOUND,
                service.listCoursesByRecommendedTerm("计算机科学与技术", 2026, 1).getStatus());
        assertEquals(StatusCode.OK, service.changeStatus("PLAN-CS-2026",
                TrainingPlanStatus.PUBLISHED).getStatus());
        assertEquals(StatusCode.CONFLICT, service.saveCourse("PLAN-CS-2026",
                new TrainingPlanCourse("CS301", 3, SelectionType.REQUIRED, false)).getStatus());
        assertEquals(StatusCode.OK,
                service.listCoursesByRecommendedTerm("计算机科学与技术", 2026, 1).getStatus());
        assertEquals(StatusCode.OK, service.changeStatus("PLAN-CS-2026",
                TrainingPlanStatus.ARCHIVED).getStatus());
        assertEquals(StatusCode.CONFLICT, service.changeStatus("PLAN-CS-2026",
                TrainingPlanStatus.PUBLISHED).getStatus());
        assertEquals(StatusCode.NOT_FOUND,
                service.listCoursesByRecommendedTerm("计算机科学与技术", 2026, 1).getStatus());
    }

    @Test
    void validatesPlanCoursesAgainstInjectedCourseCatalog() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService();
        catalog.create(new Course("CS101", "程序设计基础", 3));
        InMemoryTrainingPlanService service = new InMemoryTrainingPlanService(catalog);
        TrainingPlan validPlan = new TrainingPlan("PLAN-CS-2026", "计算机科学与技术", 2026,
                Arrays.asList(new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false)));
        TrainingPlan unknownCoursePlan = new TrainingPlan("PLAN-SE-2026", "软件工程", 2026,
                Arrays.asList(new TrainingPlanCourse("UNKNOWN", 1, SelectionType.REQUIRED, false)));

        assertEquals(StatusCode.OK, service.create(validPlan).getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.create(unknownCoursePlan).getStatus());
        catalog.changeStatus("CS101", CourseStatus.DISABLED);
        assertEquals(StatusCode.CONFLICT, service.saveCourse("PLAN-CS-2026",
                new TrainingPlanCourse("CS101", 2, SelectionType.REQUIRED, false)).getStatus());
    }

    private static TrainingPlan computerSciencePlan(String planId) {
        return new TrainingPlan(planId, "计算机科学与技术", 2026, Arrays.asList(
                new TrainingPlanCourse("CS101", 1, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("GE201", 1, SelectionType.ELECTIVE, true),
                new TrainingPlanCourse("CS201", 2, SelectionType.REQUIRED, false)));
    }
}

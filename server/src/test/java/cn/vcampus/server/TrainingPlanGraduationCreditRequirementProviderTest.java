package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseStatus;
import cn.vcampus.course.InMemoryCourseCatalogService;
import cn.vcampus.course.InMemoryTrainingPlanService;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanStatus;
import cn.vcampus.student.GraduationCreditRequirement;
import cn.vcampus.student.StudentRecord;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingPlanGraduationCreditRequirementProviderTest {
    @Test void sumsOnlyRequiredCoursesAndUsesCurrentCatalogCredits() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService(Arrays.asList(
                new Course("C1", "必修一", 3),
                new Course("C2", "必修二", 2),
                new Course("C3", "选修", 4)));
        InMemoryTrainingPlanService plans = new InMemoryTrainingPlanService(catalog);
        plans.create(new TrainingPlan("PLAN-2026", "计算机", 2026, Arrays.asList(
                new TrainingPlanCourse("C1", 1, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("C2", 2, SelectionType.REQUIRED, false),
                new TrainingPlanCourse("C3", 2, SelectionType.ELECTIVE, false))));
        plans.changeStatus("PLAN-2026", TrainingPlanStatus.PUBLISHED);
        TrainingPlanGraduationCreditRequirementProvider provider =
                new TrainingPlanGraduationCreditRequirementProvider(plans, catalog);

        GraduationCreditRequirement initial = provider.findFor(student("计算机", 2026)).getData();
        assertEquals(new java.math.BigDecimal("5"), initial.getRequiredCreditsDecimal());
        assertEquals(2, initial.getRequiredCourseCredits().size());

        catalog.changeStatus("C2", CourseStatus.DISABLED);
        catalog.updateDetails("C1", "必修一", 4);
        GraduationCreditRequirement changed = provider.findFor(student("计算机", 2026)).getData();
        assertEquals(new java.math.BigDecimal("6"), changed.getRequiredCreditsDecimal(),
                "已发布方案中的停用课程仍计入要求");
        assertNotEquals(initial.fingerprint(), changed.fingerprint());
    }

    @Test void rejectsMissingUnpublishedOrInvalidRequirements() {
        InMemoryCourseCatalogService catalog = new InMemoryCourseCatalogService(
                Collections.singletonList(new Course("C1", "课程", 3)));
        InMemoryTrainingPlanService plans = new InMemoryTrainingPlanService();
        plans.create(new TrainingPlan("DRAFT", "草稿专业", 2026,
                Collections.singletonList(new TrainingPlanCourse(
                        "C1", 1, SelectionType.REQUIRED, false))));
        plans.create(new TrainingPlan("EMPTY", "无必修专业", 2026,
                Collections.singletonList(new TrainingPlanCourse(
                        "C1", 1, SelectionType.ELECTIVE, false))));
        plans.create(new TrainingPlan("MISSING", "缺课专业", 2026,
                Collections.singletonList(new TrainingPlanCourse(
                        "C404", 1, SelectionType.REQUIRED, false))));
        plans.changeStatus("EMPTY", TrainingPlanStatus.PUBLISHED);
        plans.changeStatus("MISSING", TrainingPlanStatus.PUBLISHED);
        TrainingPlanGraduationCreditRequirementProvider provider =
                new TrainingPlanGraduationCreditRequirementProvider(plans, catalog);

        assertStatus(StatusCode.NOT_FOUND, provider.findFor(student("未配置专业", 2026)));
        assertStatus(StatusCode.NOT_FOUND, provider.findFor(student("草稿专业", 2026)));
        assertStatus(StatusCode.CONFLICT, provider.findFor(student("无必修专业", 2026)));
        assertStatus(StatusCode.CONFLICT, provider.findFor(student("缺课专业", 2026)));
        assertStatus(StatusCode.CONFLICT, provider.findFor(student("", 2026)));
    }

    private static StudentRecord student(String major, int year) {
        return new StudentRecord("S1", "u1", "学生", "未知", "院系", major,
                "班级", year, "在读", "", "");
    }

    private static void assertStatus(StatusCode expected, ServiceResult<?> result) {
        assertEquals(expected, result.getStatus(), result.getMessage());
    }
}

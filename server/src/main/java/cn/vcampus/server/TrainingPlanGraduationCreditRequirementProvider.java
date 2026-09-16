package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseCatalogService;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanService;
import cn.vcampus.course.TrainingPlanStatus;
import cn.vcampus.student.GraduationCreditRequirement;
import cn.vcampus.student.GraduationCreditRequirementProvider;
import cn.vcampus.student.StudentRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.math.BigDecimal;

/** Calculates required graduation credits from a student's published training plan. */
final class TrainingPlanGraduationCreditRequirementProvider
        implements GraduationCreditRequirementProvider {
    private final TrainingPlanService trainingPlans;
    private final CourseCatalogService courses;

    TrainingPlanGraduationCreditRequirementProvider(TrainingPlanService trainingPlans,
            CourseCatalogService courses) {
        if (trainingPlans == null || courses == null) {
            throw new IllegalArgumentException("training plan requirement dependencies are required");
        }
        this.trainingPlans = trainingPlans;
        this.courses = courses;
    }

    @Override
    public ServiceResult<GraduationCreditRequirement> findFor(StudentRecord student) {
        if (student == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "学生档案不能为空");
        }
        String majorName = normalize(student.getMajorName());
        if (majorName == null || student.getEnrollmentYear() < 1900) {
            return ServiceResult.failure(StatusCode.CONFLICT, "学生专业或入学年份未完整配置");
        }
        ServiceResult<TrainingPlan> planResult = trainingPlans.findByMajorAndEnrollmentYear(
                majorName, student.getEnrollmentYear());
        if (planResult.getStatus() != StatusCode.OK) {
            return planResult.getStatus() == StatusCode.NOT_FOUND
                    ? ServiceResult.<GraduationCreditRequirement>failure(StatusCode.NOT_FOUND,
                            "未找到该学生适用的已发布培养方案")
                    : ServiceResult.<GraduationCreditRequirement>failure(planResult.getStatus(),
                            planResult.getMessage());
        }
        TrainingPlan plan = planResult.getData();
        if (plan == null) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "培养方案服务返回了空数据");
        }
        if (plan.getStatus() != TrainingPlanStatus.PUBLISHED) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "未找到该学生适用的已发布培养方案");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<String> courseCredits = new ArrayList<String>();
        Set<String> counted = new LinkedHashSet<String>();
        for (TrainingPlanCourse requirement : plan.getCourses()) {
            if (requirement.getSelectionType() != SelectionType.REQUIRED
                    || !counted.add(requirement.getCourseId())) {
                continue;
            }
            ServiceResult<Course> courseResult = courses.findById(requirement.getCourseId());
            if (courseResult.getStatus() != StatusCode.OK) {
                return courseResult.getStatus() == StatusCode.NOT_FOUND
                        ? ServiceResult.<GraduationCreditRequirement>failure(StatusCode.CONFLICT,
                                "培养方案中的必修课程不存在：" + requirement.getCourseId())
                        : ServiceResult.<GraduationCreditRequirement>failure(courseResult.getStatus(),
                                courseResult.getMessage());
            }
            Course course = courseResult.getData();
            if (course == null) {
                return ServiceResult.failure(StatusCode.SERVER_ERROR, "课程目录服务返回了空数据");
            }
            total = total.add(course.getCreditsDecimal());
            courseCredits.add(course.getCourseId().length() + ":" + course.getCourseId()
                    + ":" + course.getCreditsDecimal().stripTrailingZeros().toPlainString());
        }
        if (courseCredits.isEmpty()) {
            return ServiceResult.failure(StatusCode.CONFLICT, "已发布培养方案未配置必修课程");
        }
        return ServiceResult.ok(new GraduationCreditRequirement(plan.getPlanId(), total, courseCredits));
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

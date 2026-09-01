package cn.vcampus.course;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于开发和测试的内存培养方案服务。
 *
 * <p>同一专业、同一入学年份只能有一份生效培养方案。程序关闭后数据会丢失；后续接入
 * Access 数据库时应保持 {@link TrainingPlanService} 接口不变。</p>
 */
public final class InMemoryTrainingPlanService implements TrainingPlanService {
    private final Map<String, TrainingPlan> plansById;
    private final Map<String, String> planIdByScope;
    private final CourseCatalogService courseCatalog;

    public InMemoryTrainingPlanService() {
        this(Collections.<TrainingPlan>emptyList(), null);
    }

    public InMemoryTrainingPlanService(List<TrainingPlan> plans) {
        this(plans, null);
    }

    /** 使用课程目录创建培养方案服务，新增课程要求时会校验课程可用性。 */
    public InMemoryTrainingPlanService(CourseCatalogService courseCatalog) {
        this(Collections.<TrainingPlan>emptyList(), courseCatalog);
    }

    public InMemoryTrainingPlanService(List<TrainingPlan> plans,
            CourseCatalogService courseCatalog) {
        if (plans == null) {
            throw new IllegalArgumentException("plans must not be null");
        }
        this.plansById = new LinkedHashMap<String, TrainingPlan>();
        this.planIdByScope = new LinkedHashMap<String, String>();
        this.courseCatalog = courseCatalog;
        for (TrainingPlan plan : plans) {
            if (plan == null) {
                throw new IllegalArgumentException("plans must not contain null");
            }
            if (plansById.put(plan.getPlanId(), plan) != null) {
                throw new IllegalArgumentException("duplicate planId: " + plan.getPlanId());
            }
            String scopeKey = scopeKey(plan.getMajorName(), plan.getEnrollmentYear());
            if (planIdByScope.put(scopeKey, plan.getPlanId()) != null) {
                throw new IllegalArgumentException("duplicate training plan scope: " + scopeKey);
            }
        }
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> create(TrainingPlan plan) {
        if (plan == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "plan must not be null");
        }
        if (plan.getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "new training plan must be created as DRAFT");
        }
        for (TrainingPlanCourse course : plan.getCourses()) {
            ServiceResult<Void> courseResult = requireActiveCourse(course.getCourseId());
            if (courseResult.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
            }
        }
        if (plansById.containsKey(plan.getPlanId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "training plan already exists");
        }
        String scopeKey = scopeKey(plan.getMajorName(), plan.getEnrollmentYear());
        if (planIdByScope.containsKey(scopeKey)) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "training plan already exists for major and enrollment year");
        }
        plansById.put(plan.getPlanId(), plan);
        planIdByScope.put(scopeKey, plan.getPlanId());
        return ServiceResult.ok(plan);
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> findById(String planId) {
        String normalizedPlanId = normalize(planId);
        if (normalizedPlanId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "planId must not be blank");
        }
        TrainingPlan plan = plansById.get(normalizedPlanId);
        if (plan == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
        }
        return ServiceResult.ok(plan);
    }

    @Override
    public synchronized ServiceResult<List<TrainingPlan>> listAll() {
        return ServiceResult.ok(Collections.unmodifiableList(
                new ArrayList<TrainingPlan>(plansById.values())));
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> findByMajorAndEnrollmentYear(String majorName,
            int enrollmentYear) {
        String scopeKey = validatedScopeKey(majorName, enrollmentYear);
        if (scopeKey == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "majorName and enrollmentYear are invalid");
        }
        String planId = planIdByScope.get(scopeKey);
        if (planId == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
        }
        return ServiceResult.ok(plansById.get(planId));
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> changeStatus(String planId,
            TrainingPlanStatus status) {
        String normalizedPlanId = normalize(planId);
        if (normalizedPlanId == null || status == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "planId and status must not be null");
        }
        TrainingPlan existing = plansById.get(normalizedPlanId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
        }
        if (!canChangeTo(existing.getStatus(), status)) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "training plan status transition is not allowed");
        }
        TrainingPlan changed = existing.withStatus(status);
        plansById.put(normalizedPlanId, changed);
        return ServiceResult.ok(changed);
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> saveCourse(String planId,
            TrainingPlanCourse course) {
        String normalizedPlanId = normalize(planId);
        if (normalizedPlanId == null || course == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "planId and course must not be null");
        }
        TrainingPlan existing = plansById.get(normalizedPlanId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
        }
        if (existing.getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only DRAFT training plan can be maintained");
        }
        ServiceResult<Void> courseResult = requireActiveCourse(course.getCourseId());
        if (courseResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(courseResult.getStatus(), courseResult.getMessage());
        }
        TrainingPlan changed = existing.withCourse(course);
        plansById.put(normalizedPlanId, changed);
        return ServiceResult.ok(changed);
    }

    @Override
    public synchronized ServiceResult<TrainingPlan> removeCourse(String planId, String courseId) {
        String normalizedPlanId = normalize(planId);
        String normalizedCourseId = normalize(courseId);
        if (normalizedPlanId == null || normalizedCourseId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "planId and courseId must not be blank");
        }
        TrainingPlan existing = plansById.get(normalizedPlanId);
        if (existing == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "training plan not found");
        }
        if (existing.getStatus() != TrainingPlanStatus.DRAFT) {
            return ServiceResult.failure(StatusCode.CONFLICT,
                    "only DRAFT training plan can be maintained");
        }
        try {
            TrainingPlan changed = existing.withoutCourse(normalizedCourseId);
            plansById.put(normalizedPlanId, changed);
            return ServiceResult.ok(changed);
        } catch (IllegalArgumentException invalidRequest) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidRequest.getMessage());
        }
    }

    @Override
    public synchronized ServiceResult<List<TrainingPlanCourse>> listCoursesByRecommendedTerm(
            String majorName, int enrollmentYear, int recommendedTerm) {
        if (recommendedTerm <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST,
                    "recommendedTerm must be positive");
        }
        ServiceResult<TrainingPlan> planResult = findByMajorAndEnrollmentYear(majorName,
                enrollmentYear);
        if (planResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(planResult.getStatus(), planResult.getMessage());
        }
        if (planResult.getData().getStatus() != TrainingPlanStatus.PUBLISHED) {
            return ServiceResult.failure(StatusCode.NOT_FOUND,
                    "published training plan not found");
        }
        List<TrainingPlanCourse> courses = new ArrayList<TrainingPlanCourse>();
        for (TrainingPlanCourse course : planResult.getData().getCourses()) {
            if (course.getRecommendedTerm() == recommendedTerm) {
                courses.add(course);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(courses));
    }

    private static String validatedScopeKey(String majorName, int enrollmentYear) {
        String normalizedMajorName = normalize(majorName);
        if (normalizedMajorName == null || enrollmentYear < 1900 || enrollmentYear > 9999) {
            return null;
        }
        return scopeKey(normalizedMajorName, enrollmentYear);
    }

    private static boolean canChangeTo(TrainingPlanStatus current, TrainingPlanStatus target) {
        return (current == TrainingPlanStatus.DRAFT && target == TrainingPlanStatus.PUBLISHED)
                || (current == TrainingPlanStatus.PUBLISHED
                        && target == TrainingPlanStatus.ARCHIVED);
    }

    private ServiceResult<Void> requireActiveCourse(String courseId) {
        if (courseCatalog == null) {
            return ServiceResult.ok(null);
        }
        ServiceResult<Course> courseResult = courseCatalog.findActiveById(courseId);
        return courseResult.getStatus() == StatusCode.OK
                ? ServiceResult.ok(null)
                : ServiceResult.<Void>failure(courseResult.getStatus(), courseResult.getMessage());
    }

    private static String scopeKey(String majorName, int enrollmentYear) {
        return majorName + "|" + enrollmentYear;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

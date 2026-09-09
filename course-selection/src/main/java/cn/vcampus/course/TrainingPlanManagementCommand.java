package cn.vcampus.course;

import java.io.Serializable;

/**
 * 教务端维护培养方案的 Socket 命令。
 *
 * <p>命令只携带 token 和培养方案业务数据。服务端必须以 token 校验
 * {@code COURSE_MANAGE} 权限，不能依赖客户端是否隐藏管理入口。</p>
 */
public final class TrainingPlanManagementCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Operation {
        LIST,
        CREATE,
        UPDATE_BASIC_INFO,
        SAVE_COURSE,
        REMOVE_COURSE,
        CHANGE_STATUS
    }

    private final String token;
    private final Operation operation;
    private final TrainingPlan plan;
    private final String planId;
    private final TrainingPlanCourse course;
    private final String courseId;
    private final TrainingPlanStatus status;

    private TrainingPlanManagementCommand(String token, Operation operation, TrainingPlan plan,
            String planId, TrainingPlanCourse course, String courseId, TrainingPlanStatus status) {
        this.token = requireText(token, "token");
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        this.operation = operation;
        this.plan = plan;
        this.planId = normalize(planId);
        this.course = course;
        this.courseId = normalize(courseId);
        this.status = status;
    }

    public static TrainingPlanManagementCommand list(String token) {
        return new TrainingPlanManagementCommand(token, Operation.LIST, null, null, null, null,
                null);
    }

    public static TrainingPlanManagementCommand create(String token, TrainingPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        return new TrainingPlanManagementCommand(token, Operation.CREATE, plan, null, null, null,
                null);
    }

    public static TrainingPlanManagementCommand updateBasicInfo(String token, TrainingPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        return new TrainingPlanManagementCommand(token, Operation.UPDATE_BASIC_INFO, plan, null,
                null, null, null);
    }

    public static TrainingPlanManagementCommand saveCourse(String token, String planId,
            TrainingPlanCourse course) {
        if (course == null) {
            throw new IllegalArgumentException("course must not be null");
        }
        return new TrainingPlanManagementCommand(token, Operation.SAVE_COURSE, null,
                requireText(planId, "planId"), course, null, null);
    }

    public static TrainingPlanManagementCommand removeCourse(String token, String planId,
            String courseId) {
        return new TrainingPlanManagementCommand(token, Operation.REMOVE_COURSE, null,
                requireText(planId, "planId"), null, requireText(courseId, "courseId"), null);
    }

    public static TrainingPlanManagementCommand changeStatus(String token, String planId,
            TrainingPlanStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return new TrainingPlanManagementCommand(token, Operation.CHANGE_STATUS, null,
                requireText(planId, "planId"), null, null, status);
    }

    public String getToken() { return token; }
    public Operation getOperation() { return operation; }
    public TrainingPlan getPlan() { return plan; }
    public String getPlanId() { return planId; }
    public TrainingPlanCourse getCourse() { return course; }
    public String getCourseId() { return courseId; }
    public TrainingPlanStatus getStatus() { return status; }

    private static String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

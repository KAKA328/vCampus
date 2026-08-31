package cn.vcampus.course;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 面向一个专业和入学年份的培养方案。
 *
 * <p>学籍模块当前提供专业名称和入学年份，因此本阶段使用这两个字段定位培养方案。
 * 后续若项目统一引入专业编号，可在数据库实现中改用专业编号，业务接口保持不变。</p>
 */
public final class TrainingPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String planId;
    private final String majorName;
    private final int enrollmentYear;
    private final List<TrainingPlanCourse> courses;
    private final TrainingPlanStatus status;

    public TrainingPlan(String planId, String majorName, int enrollmentYear,
            List<TrainingPlanCourse> courses) {
        this(planId, majorName, enrollmentYear, courses, TrainingPlanStatus.DRAFT);
    }

    public TrainingPlan(String planId, String majorName, int enrollmentYear,
            List<TrainingPlanCourse> courses, TrainingPlanStatus status) {
        this.planId = requireText(planId, "planId");
        this.majorName = requireText(majorName, "majorName");
        if (enrollmentYear < 1900 || enrollmentYear > 9999) {
            throw new IllegalArgumentException("enrollmentYear must be a four digit year");
        }
        if (courses == null || courses.isEmpty()) {
            throw new IllegalArgumentException("courses must not be empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }

        Set<String> courseIds = new LinkedHashSet<String>();
        List<TrainingPlanCourse> copiedCourses = new ArrayList<TrainingPlanCourse>();
        for (TrainingPlanCourse course : courses) {
            if (course == null) {
                throw new IllegalArgumentException("courses must not contain null");
            }
            if (!courseIds.add(course.getCourseId())) {
                throw new IllegalArgumentException(
                        "courses must not contain duplicate courseId: " + course.getCourseId());
            }
            copiedCourses.add(course);
        }
        this.enrollmentYear = enrollmentYear;
        this.courses = Collections.unmodifiableList(copiedCourses);
        this.status = status;
    }

    public String getPlanId() {
        return planId;
    }

    public String getMajorName() {
        return majorName;
    }

    public int getEnrollmentYear() {
        return enrollmentYear;
    }

    /** 返回不可修改的课程要求列表。 */
    public List<TrainingPlanCourse> getCourses() {
        return courses;
    }

    public TrainingPlanStatus getStatus() {
        return status;
    }

    /**
     * 返回加入或更新一门课程要求后的新培养方案。
     *
     * <p>课程编号相同时视为修改：建议修读学期、课程类别和跨专业开放状态会被新值替换。</p>
     */
    public TrainingPlan withCourse(TrainingPlanCourse course) {
        if (course == null) {
            throw new IllegalArgumentException("course must not be null");
        }
        List<TrainingPlanCourse> changedCourses = new ArrayList<TrainingPlanCourse>();
        boolean replaced = false;
        for (TrainingPlanCourse existingCourse : courses) {
            if (existingCourse.getCourseId().equals(course.getCourseId())) {
                changedCourses.add(course);
                replaced = true;
            } else {
                changedCourses.add(existingCourse);
            }
        }
        if (!replaced) {
            changedCourses.add(course);
        }
        return new TrainingPlan(planId, majorName, enrollmentYear, changedCourses, status);
    }

    /**
     * 返回删除指定课程要求后的新培养方案。
     *
     * @throws IllegalArgumentException 课程不存在，或删除后方案不再包含课程时抛出
     */
    public TrainingPlan withoutCourse(String courseId) {
        String normalizedCourseId = requireText(courseId, "courseId");
        List<TrainingPlanCourse> changedCourses = new ArrayList<TrainingPlanCourse>();
        boolean removed = false;
        for (TrainingPlanCourse existingCourse : courses) {
            if (existingCourse.getCourseId().equals(normalizedCourseId)) {
                removed = true;
            } else {
                changedCourses.add(existingCourse);
            }
        }
        if (!removed) {
            throw new IllegalArgumentException("courseId does not exist in training plan");
        }
        if (changedCourses.isEmpty()) {
            throw new IllegalArgumentException("training plan must contain at least one course");
        }
        return new TrainingPlan(planId, majorName, enrollmentYear, changedCourses, status);
    }

    /** 返回状态更新后的新培养方案，原对象保持不变。 */
    public TrainingPlan withStatus(TrainingPlanStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return new TrainingPlan(planId, majorName, enrollmentYear, courses, newStatus);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

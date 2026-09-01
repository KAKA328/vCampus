package cn.vcampus.course;

import java.io.Serializable;

/**
 * A course that students can select.
 *
 * <p>课程目录只描述稳定的课程信息。教学班容量属于具体教学班；{@code capacity} 字段仅为
 * 兼容第一阶段的简化选课演示服务而保留，新的教学班管理不会使用它。</p>
 */
public final class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final String name;
    private final int credits;
    private final int capacity;
    private final CourseStatus status;

    /**
     * 创建课程目录中的启用课程。
     *
     * <p>教学班容量应在 {@link CourseOffering} 中配置，而非在课程目录中配置。</p>
     */
    public Course(String courseId, String name, int credits) {
        this(courseId, name, credits, 0, CourseStatus.ACTIVE, false);
    }

    /** 第一阶段简化选课服务使用的兼容构造方法，新教学班不再使用其中的容量。 */
    public Course(String courseId, String name, int credits, int capacity) {
        this(courseId, name, credits, capacity, CourseStatus.ACTIVE, true);
    }

    private Course(String courseId, String name, int credits, int capacity, CourseStatus status,
            boolean requiresLegacyCapacity) {
        this.courseId = requireText(courseId, "courseId");
        this.name = requireText(name, "name");
        if (credits <= 0) {
            throw new IllegalArgumentException("credits must be positive");
        }
        if (capacity < 0 || (requiresLegacyCapacity && capacity == 0)) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.credits = credits;
        this.capacity = capacity;
        this.status = status;
    }

    public String getCourseId() {
        return courseId;
    }

    public String getName() {
        return name;
    }

    public int getCredits() {
        return credits;
    }

    public int getCapacity() {
        return capacity;
    }

    public CourseStatus getStatus() {
        return status;
    }

    /** 返回课程名称或学分更新后的新课程对象。 */
    public Course withDetails(String newName, int newCredits) {
        return new Course(courseId, newName, newCredits, capacity, status, false);
    }

    /** 返回状态更新后的新课程对象。 */
    public Course withStatus(CourseStatus newStatus) {
        return new Course(courseId, name, credits, capacity, newStatus, false);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

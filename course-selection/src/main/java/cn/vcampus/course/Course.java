package cn.vcampus.course;

import java.io.Serializable;

/** 课程目录中的稳定课程信息。教学班容量由具体教学班维护。 */
public final class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final String name;
    private final int credits;
    private final CourseStatus status;

    /**
     * 创建课程目录中的启用课程。
     *
     * <p>教学班容量应在 {@link CourseOffering} 中配置，而非在课程目录中配置。</p>
     */
    public Course(String courseId, String name, int credits) {
        this(courseId, name, credits, CourseStatus.ACTIVE);
    }

    private Course(String courseId, String name, int credits, CourseStatus status) {
        this.courseId = requireText(courseId, "courseId");
        this.name = requireText(name, "name");
        if (credits <= 0) {
            throw new IllegalArgumentException("credits must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.credits = credits;
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

    public CourseStatus getStatus() {
        return status;
    }

    /** 返回课程名称或学分更新后的新课程对象。 */
    public Course withDetails(String newName, int newCredits) {
        return new Course(courseId, newName, newCredits, status);
    }

    /** 返回状态更新后的新课程对象。 */
    public Course withStatus(CourseStatus newStatus) {
        return new Course(courseId, name, credits, newStatus);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

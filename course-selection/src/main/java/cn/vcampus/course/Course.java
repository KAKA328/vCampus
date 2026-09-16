package cn.vcampus.course;

import cn.vcampus.common.CreditFormat;
import java.io.Serializable;
import java.math.BigDecimal;

/** 课程目录中的稳定课程信息。教学班容量由具体教学班维护。 */
public final class Course implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String courseId;
    private final String name;
    /** Legacy serialized field. Keep name and type for Java wire compatibility. */
    private final int credits;
    private final BigDecimal creditsDecimal;
    private final CourseStatus status;

    /**
     * 创建课程目录中的启用课程。
     *
     * <p>教学班容量应在 {@link CourseOffering} 中配置，而非在课程目录中配置。</p>
     */
    public Course(String courseId, String name, int credits) {
        this(courseId, name, BigDecimal.valueOf(credits));
    }

    public Course(String courseId, String name, BigDecimal credits) {
        this(courseId, name, credits, CourseStatus.ACTIVE);
    }

    private Course(String courseId, String name, BigDecimal credits, CourseStatus status) {
        this.courseId = requireText(courseId, "courseId");
        this.name = requireText(name, "name");
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.creditsDecimal = CreditFormat.positive(credits, "credits");
        this.credits = CreditFormat.legacyInt(this.creditsDecimal, "credits");
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

    public BigDecimal getCreditsDecimal() {
        return CreditFormat.decimalOrLegacy(creditsDecimal, credits);
    }

    public boolean hasPreciseCredits() { return creditsDecimal != null; }

    public CourseStatus getStatus() {
        return status;
    }

    /** 返回课程名称或学分更新后的新课程对象。 */
    public Course withDetails(String newName, int newCredits) {
        return withDetails(newName, BigDecimal.valueOf(newCredits));
    }

    public Course withDetails(String newName, BigDecimal newCredits) {
        return new Course(courseId, newName, newCredits, status);
    }

    /** 返回状态更新后的新课程对象。 */
    public Course withStatus(CourseStatus newStatus) {
        return new Course(courseId, name, getCreditsDecimal(), newStatus);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

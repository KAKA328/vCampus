package cn.vcampus.course;

import java.io.Serializable;

/** 学生在某轮次中可选择的一条“课程 + 教学班”项目。 */
public final class SelectableCourseOffering implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Course course;
    private final CourseOffering offering;
    private final SelectionType selectionType;
    private final CapacityBucketUsage capacityUsage;

    public SelectableCourseOffering(Course course, CourseOffering offering,
            SelectionType selectionType, CapacityBucketUsage capacityUsage) {
        if (course == null || offering == null || selectionType == null || capacityUsage == null) {
            throw new IllegalArgumentException("selectable offering fields must not be null");
        }
        if (!course.getCourseId().equals(offering.getCourseId())
                || selectionType.getCapacityBucket() != capacityUsage.getCapacityBucket()) {
            throw new IllegalArgumentException("selectable offering fields do not match");
        }
        this.course = course;
        this.offering = offering;
        this.selectionType = selectionType;
        this.capacityUsage = capacityUsage;
    }

    public Course getCourse() { return course; }
    public CourseOffering getOffering() { return offering; }
    public SelectionType getSelectionType() { return selectionType; }
    public CapacityBucketUsage getCapacityUsage() { return capacityUsage; }
}

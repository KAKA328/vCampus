package cn.vcampus.course;

import java.io.Serializable;

/** 学生在某轮次中可选择的一条“课程 + 教学班”项目。 */
public final class SelectableCourseOffering implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Course course;
    private final CourseOffering offering;
    private final SelectionType selectionType;
    private final CourseOfferingCapacitySnapshot capacitySnapshot;
    private final CapacityBucketUsage capacityUsage;

    public SelectableCourseOffering(Course course, CourseOffering offering,
            SelectionType selectionType, CourseOfferingCapacitySnapshot capacitySnapshot) {
        if (course == null || offering == null || selectionType == null || capacitySnapshot == null) {
            throw new IllegalArgumentException("selectable offering fields must not be null");
        }
        if (!course.getCourseId().equals(offering.getCourseId())
                || !offering.getOfferingId().equals(capacitySnapshot.getOfferingId())) {
            throw new IllegalArgumentException("selectable offering fields do not match");
        }
        this.course = course;
        this.offering = offering;
        this.selectionType = selectionType;
        this.capacitySnapshot = capacitySnapshot;
        this.capacityUsage = capacitySnapshot.getUsage(selectionType.getCapacityBucket());
    }

    public Course getCourse() { return course; }
    public CourseOffering getOffering() { return offering; }
    public SelectionType getSelectionType() { return selectionType; }
    /** 用于学生端展示教学班总容量和总已选人数。 */
    public CourseOfferingCapacitySnapshot getCapacitySnapshot() { return capacitySnapshot; }
    public CapacityBucketUsage getCapacityUsage() { return capacityUsage; }
}

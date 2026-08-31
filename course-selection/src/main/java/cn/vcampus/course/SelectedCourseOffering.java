package cn.vcampus.course;

import java.io.Serializable;

/** 学生当前有效选课记录及其关联的课程、教学班信息。 */
public final class SelectedCourseOffering implements Serializable {
    private static final long serialVersionUID = 1L;
    private final CourseSelectionRecord record;
    private final Course course;
    private final CourseOffering offering;

    public SelectedCourseOffering(CourseSelectionRecord record, Course course,
            CourseOffering offering) {
        if (record == null || course == null || offering == null
                || !record.getOfferingId().equals(offering.getOfferingId())
                || !course.getCourseId().equals(offering.getCourseId())) {
            throw new IllegalArgumentException("selected offering fields do not match");
        }
        this.record = record;
        this.course = course;
        this.offering = offering;
    }

    public CourseSelectionRecord getRecord() { return record; }
    public Course getCourse() { return course; }
    public CourseOffering getOffering() { return offering; }
}

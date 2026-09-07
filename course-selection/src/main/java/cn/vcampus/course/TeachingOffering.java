package cn.vcampus.course;

import java.io.Serializable;

/** 教师端展示的教学班摘要，包含课程目录信息。 */
public final class TeachingOffering implements Serializable {
    private static final long serialVersionUID = 1L;
    private final CourseOffering offering;
    private final Course course;

    public TeachingOffering(CourseOffering offering, Course course) {
        if (offering == null || course == null) {
            throw new IllegalArgumentException("offering and course must not be null");
        }
        this.offering = offering;
        this.course = course;
    }

    public CourseOffering getOffering() { return offering; }
    public Course getCourse() { return course; }
}

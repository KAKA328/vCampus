package cn.vcampus.course;

import java.io.Serializable;

/** Course value object. */
public final class Course implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String courseId; private final String name; private final int credits;
    public Course(String courseId, String name, int credits) { this.courseId=courseId; this.name=name; this.credits=credits; }
    public String getCourseId() { return courseId; } public String getName() { return name; } public int getCredits() { return credits; }
}

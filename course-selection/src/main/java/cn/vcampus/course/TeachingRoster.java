package cn.vcampus.course;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 教师查看本人教学班时返回的教学班信息与有效选课名单。 */
public final class TeachingRoster implements Serializable {
    private static final long serialVersionUID = 1L;
    private final TeachingOffering teachingOffering;
    private final List<TeachingRosterEntry> students;

    public TeachingRoster(TeachingOffering teachingOffering, List<TeachingRosterEntry> students) {
        if (teachingOffering == null || students == null) {
            throw new IllegalArgumentException("teachingOffering and students must not be null");
        }
        this.teachingOffering = teachingOffering;
        this.students = Collections.unmodifiableList(new ArrayList<TeachingRosterEntry>(students));
    }

    public TeachingOffering getTeachingOffering() { return teachingOffering; }
    public List<TeachingRosterEntry> getStudents() { return students; }
}

package cn.vcampus.student;

import java.io.Serializable;

/** Shared student record placeholder; extend only through reviewed API changes. */
public final class StudentRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId; private final String name; private final String classId;
    public StudentRecord(String studentId, String name, String classId) { this.studentId=studentId; this.name=name; this.classId=classId; }
    public String getStudentId() { return studentId; } public String getName() { return name; } public String getClassId() { return classId; }
}

package cn.vcampus.student;

import java.io.Serializable;

/** Shared student profile used by student-management and academic review. */
public final class StudentRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String studentId;
    private final String name;
    private final String gender;
    private final String departmentName;
    private final String majorName;
    private final String classId;
    private final int enrollmentYear;
    private final String status;
    private final String phone;
    private final String email;

    public StudentRecord(String studentId, String name, String classId) {
        this(studentId, name, "", "", "", classId, 0, "", "", "");
    }

    public StudentRecord(
            String studentId,
            String name,
            String gender,
            String departmentName,
            String majorName,
            String classId,
            int enrollmentYear,
            String status,
            String phone,
            String email
    ) {
        this.studentId = studentId;
        this.name = name;
        this.gender = gender;
        this.departmentName = departmentName;
        this.majorName = majorName;
        this.classId = classId;
        this.enrollmentYear = enrollmentYear;
        this.status = status;
        this.phone = phone;
        this.email = email;
    }

    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public String getGender() { return gender; }
    public String getDepartmentName() { return departmentName; }
    public String getMajorName() { return majorName; }
    public String getClassId() { return classId; }
    public int getEnrollmentYear() { return enrollmentYear; }
    public String getStatus() { return status; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
}

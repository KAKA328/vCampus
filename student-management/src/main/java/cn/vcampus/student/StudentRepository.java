package cn.vcampus.student;

import java.util.List;

/** Persistence contract for student profiles owned by the student-management module. */
public interface StudentRepository {
    StudentRecord findById(String studentId);
    StudentRecord findByUserId(String userId);
    List<StudentRecord> findByClass(String classId);
    List<StudentRecord> findByMajor(String majorName);
    StudentRecord save(StudentRecord record);
}

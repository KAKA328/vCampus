package cn.vcampus.student;

import java.util.List;

/** Persistence contract for student profiles owned by the student-management module. */
public interface StudentRepository {
    default List<StudentRecord> findAll() { throw new UnsupportedOperationException("student directory unavailable"); }
    StudentRecord findById(String studentId);
    StudentRecord findByUserId(String userId);
    List<StudentRecord> findByClass(String classId);
    List<StudentRecord> findByMajor(String majorName);
    List<StudentRecord> findByIds(List<String> studentIds);
    StudentRecord save(StudentRecord record);
    /** Null result means a concurrent change; null expected means insert only, never upsert. */
    default StudentRecord saveIfUnchanged(StudentRecord record, StudentRecord expected) {
        throw new UnsupportedOperationException("conditional profile updates unavailable");
    }
    /** Atomically verifies the expected bound profile and updates phone/email only. */
    default StudentRecord updateContacts(StudentRecord expected, String phone, String email) {
        throw new UnsupportedOperationException("contact updates unavailable");
    }
}

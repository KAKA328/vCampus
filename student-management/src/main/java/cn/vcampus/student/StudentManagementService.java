package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Student records contract owned by the student-management team. */
public interface StudentManagementService {
    ServiceResult<StudentRecord> findById(String studentId);
    ServiceResult<StudentRecord> findByUserId(String userId);
    /** Alias used by server adapters after the token has been resolved to userId. */
    default ServiceResult<StudentRecord> findMyStudentProfile(String userId) {
        return findByUserId(userId);
    }
    ServiceResult<List<StudentRecord>> findByClass(String classId);
    ServiceResult<List<StudentRecord>> findByMajor(String majorName);
    /** Loads a complete teaching roster without repeated per-student service calls. */
    ServiceResult<List<StudentRecord>> findByIds(List<String> studentIds);
    ServiceResult<StudentRecord> save(StudentRecord record);
}

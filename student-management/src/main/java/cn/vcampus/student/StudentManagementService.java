package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Student records contract owned by the student-management team. */
public interface StudentManagementService {
    default cn.vcampus.common.ServiceResult<java.util.List<StudentRecord>> findAll() {
        return cn.vcampus.common.ServiceResult.failure(cn.vcampus.common.StatusCode.SERVER_ERROR, "student directory unavailable");
    }
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
    default ServiceResult<StudentRecord> saveIfUnchanged(StudentRecord record, StudentRecord expected) {
        return ServiceResult.failure(cn.vcampus.common.StatusCode.SERVER_ERROR, "conditional updates unavailable");
    }
    default ServiceResult<StudentRecord> updateContacts(String userId, StudentRecord expected,
            String phone, String email) {
        return ServiceResult.failure(cn.vcampus.common.StatusCode.SERVER_ERROR, "contact updates unavailable");
    }
}

package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Student records contract owned by the student-management team. */
public interface StudentManagementService {
    ServiceResult<StudentRecord> findById(String studentId);
    ServiceResult<List<StudentRecord>> findByClass(String classId);
    ServiceResult<StudentRecord> save(StudentRecord record);
}

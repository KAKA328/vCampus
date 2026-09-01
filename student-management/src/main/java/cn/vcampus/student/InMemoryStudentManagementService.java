package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory student records for local demos and handler tests. */
public final class InMemoryStudentManagementService implements StudentManagementService {
    private final Map<String, StudentRecord> records = new LinkedHashMap<String, StudentRecord>();
    private final Map<String, String> studentIdByUserId = new LinkedHashMap<String, String>();

    public InMemoryStudentManagementService() {
        save(new StudentRecord("demo_student", "演示学生", "未知", "计算机科学与工程学院",
                "软件工程", "SE2023-01", 2023, "在读", "", ""));
        bindUser("demo_student", "demo_student");
    }

    public synchronized void bindUser(String userId, String studentId) {
        if (userId != null && studentId != null) {
            studentIdByUserId.put(userId.trim(), studentId.trim());
        }
    }

    @Override
    public synchronized ServiceResult<StudentRecord> findByUserId(String userId) {
        String studentId = userId == null ? null : studentIdByUserId.get(userId.trim());
        if (studentId == null && userId != null) studentId = userId.trim();
        return findById(studentId);
    }

    @Override
    public synchronized ServiceResult<StudentRecord> findById(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        StudentRecord record = records.get(studentId.trim());
        return record == null ? ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student not found")
                : ServiceResult.ok(record);
    }

    @Override
    public synchronized ServiceResult<List<StudentRecord>> findByClass(String classId) {
        if (classId == null || classId.trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "classId must not be blank");
        }
        List<StudentRecord> result = new ArrayList<StudentRecord>();
        for (StudentRecord record : records.values()) {
            if (classId.trim().equals(record.getClassId())) result.add(record);
        }
        return ServiceResult.ok(result);
    }

    @Override
    public synchronized ServiceResult<StudentRecord> save(StudentRecord record) {
        if (record == null || record.getStudentId() == null || record.getStudentId().trim().isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "student record is invalid");
        }
        records.put(record.getStudentId(), record);
        return ServiceResult.ok(record);
    }
}

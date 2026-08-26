package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory student profile service for early integration and UI demos. */
public final class InMemoryStudentManagementService implements StudentManagementService {
    private final Map<String, StudentRecord> recordsById = new LinkedHashMap<String, StudentRecord>();

    public InMemoryStudentManagementService() {
        save(new StudentRecord("20230001", "演示学生", "女", "计算机学院",
                "软件工程", "09024429", 2025, "在读", "13800000000", "demo@student.local"));
        save(new StudentRecord("20230002", "测试学生二", "男", "计算机学院",
                "软件工程", "09024429", 2025, "在读", "13800000001", "demo2@student.local"));
    }

    @Override
    public synchronized ServiceResult<StudentRecord> findById(String studentId) {
        String normalizedStudentId = normalize(studentId);
        if (normalizedStudentId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentId must not be blank");
        }
        StudentRecord record = recordsById.get(normalizedStudentId);
        if (record == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "student profile not found");
        }
        return ServiceResult.ok(record);
    }

    @Override
    public synchronized ServiceResult<List<StudentRecord>> findByClass(String classId) {
        String normalizedClassId = normalize(classId);
        if (normalizedClassId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "classId must not be blank");
        }
        List<StudentRecord> matches = new ArrayList<StudentRecord>();
        for (StudentRecord record : recordsById.values()) {
            if (normalizedClassId.equals(record.getClassId())) {
                matches.add(record);
            }
        }
        return ServiceResult.ok(Collections.unmodifiableList(matches));
    }

    @Override
    public synchronized ServiceResult<StudentRecord> save(StudentRecord record) {
        if (record == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "student record must not be null");
        }
        recordsById.put(record.getStudentId(), record);
        return ServiceResult.ok(record);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

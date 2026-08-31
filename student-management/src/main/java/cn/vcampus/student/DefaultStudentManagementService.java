package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Collections;
import java.util.List;

/** Repository-backed student service shared by Access and in-memory deployments. */
public final class DefaultStudentManagementService implements StudentManagementService {
    private final StudentRepository students;

    public DefaultStudentManagementService(StudentRepository students) {
        if (students == null) {
            throw new IllegalArgumentException("students must not be null");
        }
        this.students = students;
    }

    @Override
    public ServiceResult<StudentRecord> findById(String studentId) {
        try {
            StudentRecord record = students.findById(studentId);
            return record == null
                    ? ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student not found")
                    : ServiceResult.ok(record);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find student");
        }
    }

    @Override
    public ServiceResult<List<StudentRecord>> findByClass(String classId) {
        try {
            List<StudentRecord> records = students.findByClass(classId);
            return ServiceResult.ok(records == null
                    ? Collections.<StudentRecord>emptyList() : records);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find students");
        }
    }

    @Override
    public ServiceResult<StudentRecord> save(StudentRecord record) {
        try {
            return ServiceResult.ok(students.save(record));
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            if ("userId is already bound to another student".equals(failure.getMessage())) {
                return ServiceResult.failure(StatusCode.CONFLICT, failure.getMessage());
            }
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to save student");
        }
    }
}

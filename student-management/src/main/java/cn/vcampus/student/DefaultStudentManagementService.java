package cn.vcampus.student;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

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
    public ServiceResult<List<StudentRecord>> findAll() {
        try { return ServiceResult.ok(students.findAll()); }
        catch (IllegalStateException | UnsupportedOperationException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to list students");
        }
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
    public ServiceResult<StudentRecord> findByUserId(String userId) {
        try {
            StudentRecord record = students.findByUserId(userId);
            return record == null
                    ? ServiceResult.<StudentRecord>failure(StatusCode.NOT_FOUND, "student profile not bound")
                    : ServiceResult.ok(record);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find student profile");
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
    public ServiceResult<List<StudentRecord>> findByMajor(String majorName) {
        try {
            List<StudentRecord> records = students.findByMajor(majorName);
            return ServiceResult.ok(records == null
                    ? Collections.<StudentRecord>emptyList() : records);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find students by major");
        }
    }

    @Override
    public ServiceResult<List<StudentRecord>> findByIds(List<String> studentIds) {
        try {
            if (studentIds == null) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "studentIds must not be null");
            }
            if (studentIds.isEmpty()) {
                return ServiceResult.ok(Collections.<StudentRecord>emptyList());
            }
            Set<String> expectedIds = new LinkedHashSet<String>();
            for (String studentId : studentIds) {
                if (studentId == null || studentId.trim().isEmpty()) {
                    return ServiceResult.failure(StatusCode.BAD_REQUEST,
                            "studentId must not be blank");
                }
                expectedIds.add(studentId.trim());
            }
            List<StudentRecord> records = students.findByIds(
                    new java.util.ArrayList<String>(expectedIds));
            if (records == null || records.size() != expectedIds.size()) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "one or more student profiles were not found");
            }
            Set<String> actualIds = new LinkedHashSet<String>();
            for (StudentRecord record : records) {
                if (record == null || !actualIds.add(record.getStudentId())) {
                    return ServiceResult.failure(StatusCode.NOT_FOUND,
                            "one or more student profiles were not found");
                }
            }
            if (!actualIds.equals(expectedIds)) {
                return ServiceResult.failure(StatusCode.NOT_FOUND,
                        "one or more student profiles were not found");
            }
            return ServiceResult.ok(records);
        } catch (IllegalArgumentException failure) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, failure.getMessage());
        } catch (IllegalStateException failure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to find students by ids");
        }
    }

    @Override
    public synchronized ServiceResult<StudentRecord> save(StudentRecord record) {
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

    @Override
    public synchronized ServiceResult<StudentRecord> saveIfUnchanged(StudentRecord record, StudentRecord expected) {
        try {
            return conditionalResult(students.saveIfUnchanged(record, expected));
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException failure) {
            return saveFailure(failure);
        } catch (UnsupportedOperationException unsupported) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "conditional updates unavailable");
        }
    }

    @Override
    public synchronized ServiceResult<StudentRecord> updateContacts(String userId, StudentRecord expected,
            String phone, String email) {
        if (userId == null || expected == null || !userId.equals(expected.getUserId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "contact update owner mismatch");
        }
        try {
            return conditionalResult(students.updateContacts(expected, phone, email));
        } catch (IllegalArgumentException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException failure) {
            return saveFailure(failure);
        } catch (UnsupportedOperationException unsupported) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "contact updates unavailable");
        }
    }

    private static ServiceResult<StudentRecord> conditionalResult(StudentRecord saved) {
        return saved == null ? ServiceResult.failure(StatusCode.CONFLICT, "档案已发生变化，请刷新后重试")
                : ServiceResult.ok(saved);
    }

    private static ServiceResult<StudentRecord> saveFailure(IllegalStateException failure) {
        return "userId is already bound to another student".equals(failure.getMessage())
                ? ServiceResult.failure(StatusCode.CONFLICT, failure.getMessage())
                : ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to save student");
    }
}

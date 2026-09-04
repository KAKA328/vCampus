package cn.vcampus.student;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Small thread-safe repository used when the server runs without an Access file. */
public final class InMemoryStudentRepository implements StudentRepository {
    private final Map<String, StudentRecord> records = new LinkedHashMap<String, StudentRecord>();

    @Override
    public synchronized StudentRecord findById(String studentId) {
        return records.get(requireText(studentId, "studentId"));
    }

    @Override
    public synchronized StudentRecord findByUserId(String userId) {
        String normalized = requireText(userId, "userId");
        for (StudentRecord record : records.values()) {
            if (normalized.equals(record.getUserId())) return record;
        }
        return null;
    }

    @Override
    public synchronized List<StudentRecord> findByClass(String classId) {
        String normalized = requireText(classId, "classId");
        List<StudentRecord> result = new ArrayList<StudentRecord>();
        for (StudentRecord record : records.values()) {
            if (normalized.equals(record.getClassId())) result.add(record);
        }
        Collections.sort(result, (left, right) -> left.getStudentId().compareTo(right.getStudentId()));
        return result;
    }

    @Override
    public synchronized List<StudentRecord> findByMajor(String majorName) {
        String normalized = requireText(majorName, "majorName");
        List<StudentRecord> result = new ArrayList<StudentRecord>();
        for (StudentRecord record : records.values()) {
            if (normalized.equals(record.getMajorName())) result.add(record);
        }
        Collections.sort(result, (left, right) -> left.getStudentId().compareTo(right.getStudentId()));
        return result;
    }

    @Override
    public synchronized List<StudentRecord> findByIds(List<String> studentIds) {
        if (studentIds == null) throw new IllegalArgumentException("studentIds must not be null");
        Set<String> normalizedIds = new LinkedHashSet<String>();
        for (String studentId : studentIds) {
            normalizedIds.add(requireText(studentId, "studentId"));
        }
        List<StudentRecord> result = new ArrayList<StudentRecord>();
        for (String studentId : normalizedIds) {
            StudentRecord record = records.get(studentId);
            if (record != null) result.add(record);
        }
        return result;
    }

    @Override
    public synchronized StudentRecord save(StudentRecord record) {
        if (record == null) throw new IllegalArgumentException("record must not be null");
        String studentId = requireText(record.getStudentId(), "studentId");
        requireText(record.getName(), "name");
        requireText(record.getStatus(), "status");
        if (record.getUserId() != null && findByUserId(record.getUserId()) != null
                && !studentId.equals(findByUserId(record.getUserId()).getStudentId())) {
            throw new IllegalStateException("userId is already bound to another student");
        }
        records.put(studentId, record);
        return record;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

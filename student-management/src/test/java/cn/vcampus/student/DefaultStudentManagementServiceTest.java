package cn.vcampus.student;

import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DefaultStudentManagementServiceTest {
    @Test
    public void findsExistingStudentAndReportsMissingStudent() {
        StubRepository repository = new StubRepository();
        StudentRecord record = student("S001", "u001");
        repository.records.add(record);
        StudentManagementService service = new DefaultStudentManagementService(repository);

        assertEquals(StatusCode.OK, service.findById("S001").getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findById("S999").getStatus());
        assertEquals(StatusCode.OK, service.findByUserId("u001").getStatus());
        assertEquals(StatusCode.NOT_FOUND, service.findByUserId("unbound").getStatus());
    }

    @Test
    public void mapsInvalidQueriesToBadRequest() {
        StudentManagementService service = new DefaultStudentManagementService(new StubRepository());

        assertEquals(StatusCode.BAD_REQUEST, service.findById(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.findByUserId(" ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.findByClass(null).getStatus());
    }

    @Test
    public void savesRecordAndReturnsIt() {
        StubRepository repository = new StubRepository();
        StudentRecord record = student("S002", "u002");

        assertEquals(StatusCode.OK, new DefaultStudentManagementService(repository).save(record).getStatus());
        assertNotNull(repository.findById("S002"));
    }

    @Test
    public void mapsDuplicateAccountToConflict() {
        StubRepository repository = new StubRepository();
        repository.duplicateUser = true;

        assertEquals(StatusCode.CONFLICT,
                new DefaultStudentManagementService(repository).save(student("S003", "u001")).getStatus());
    }

    private static StudentRecord student(String id, String userId) {
        return new StudentRecord(id, userId, "测试学生", "男", "计算机学院", "软件工程",
                "SE2023-01", 2023, "在读", "13800000000", "student@example.com");
    }

    private static final class StubRepository implements StudentRepository {
        private final List<StudentRecord> records = new ArrayList<StudentRecord>();
        private boolean duplicateUser;

        @Override public StudentRecord findById(String studentId) {
            if (studentId == null || studentId.trim().isEmpty()) throw new IllegalArgumentException("studentId must not be blank");
            for (StudentRecord record : records) if (studentId.equals(record.getStudentId())) return record;
            return null;
        }
        @Override public StudentRecord findByUserId(String userId) {
            if (userId == null || userId.trim().isEmpty()) {
                throw new IllegalArgumentException("userId must not be blank");
            }
            for (StudentRecord record : records) {
                if (userId.equals(record.getUserId())) return record;
            }
            return null;
        }
        @Override public List<StudentRecord> findByClass(String classId) {
            if (classId == null || classId.trim().isEmpty()) throw new IllegalArgumentException("classId must not be blank");
            return Collections.emptyList();
        }
        @Override public StudentRecord save(StudentRecord record) {
            if (record == null) throw new IllegalArgumentException("record must not be null");
            if (duplicateUser) throw new IllegalStateException("userId is already bound to another student");
            records.add(record);
            return record;
        }
    }
}

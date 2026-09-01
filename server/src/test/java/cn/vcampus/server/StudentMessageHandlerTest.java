package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.StudentUpdateCommand;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StudentMessageHandlerTest {
    private CapturingStudentService students;
    private StudentMessageHandler handler;
    private Session studentSession;
    private Session academicSession;

    @BeforeEach
    void setUp() {
        students = new CapturingStudentService();
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        users.register(new UserCredentials("student001", "Demo123", "学生一", Role.STUDENT.name()));
        users.register(new UserCredentials("academic001", "Demo123", "教务一", Role.ACADEMIC_ADMIN.name()));
        studentSession = users.login(new UserCredentials("student001", "Demo123", "学生一", Role.STUDENT.name())).getData();
        academicSession = users.login(new UserCredentials("academic001", "Demo123", "教务一", Role.ACADEMIC_ADMIN.name())).getData();
        handler = new StudentMessageHandler(students, users);
    }

    @Test
    void studentQueryUsesUserIdFromSessionForOwnRecord() {
        Message response = handler.handle(Message.request("student-self", MessageType.STUDENT_QUERY,
                StudentQueryCommand.self(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals("student001", students.lastUserId);
        assertInstanceOf(StudentRecord.class, response.getPayload());
    }

    @Test
    void academicAdminCanQueryStudentsByClass() {
        Message response = handler.handle(Message.request("student-class", MessageType.STUDENT_QUERY,
                StudentQueryCommand.byClass(academicSession.getToken(), "SE2023-01")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals("SE2023-01", students.lastClassId);
        assertInstanceOf(List.class, response.getPayload());
    }

    @Test
    void academicAdminCanSaveStudentRecord() {
        StudentRecord record = new StudentRecord("20260001", "学生一", "女",
                "计算机科学与工程学院", "软件工程", "SE2023-01", 2023,
                "在读", "13800000000", "student001@example.com");

        Message response = handler.handle(Message.request("student-update", MessageType.STUDENT_UPDATE,
                new StudentUpdateCommand(academicSession.getToken(), record)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(record, response.getPayload());
        assertEquals("20260001", students.saved.get(0).getStudentId());
    }

    @Test
    void studentCannotSaveStudentRecord() {
        Message response = handler.handle(Message.request("student-update-denied", MessageType.STUDENT_UPDATE,
                new StudentUpdateCommand(studentSession.getToken(),
                        new StudentRecord("20260001", "学生一", "SE2023-01"))));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertEquals(0, students.saved.size());
    }

    private static final class CapturingStudentService implements StudentManagementService {
        private String lastUserId;
        private String lastClassId;
        private final List<StudentRecord> saved = new ArrayList<StudentRecord>();

        @Override
        public ServiceResult<StudentRecord> findByUserId(String userId) {
            lastUserId = userId;
            return ServiceResult.ok(new StudentRecord("20260001", "学生一", "SE2023-01"));
        }

        @Override
        public ServiceResult<StudentRecord> findById(String studentId) {
            return ServiceResult.ok(new StudentRecord(studentId, "学生一", "SE2023-01"));
        }

        @Override
        public ServiceResult<List<StudentRecord>> findByClass(String classId) {
            lastClassId = classId;
            return ServiceResult.ok(Collections.singletonList(
                    new StudentRecord("20260001", "学生一", classId)));
        }

        @Override
        public ServiceResult<StudentRecord> save(StudentRecord record) {
            saved.add(record);
            return ServiceResult.ok(record);
        }
    }
}

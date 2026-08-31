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
import cn.vcampus.user.UserCredentials;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentMessageHandlerTest {
    private final StubStudents students = new StubStudents();
    private final InMemoryUserManagementService users = new InMemoryUserManagementService();
    private StudentMessageHandler handler;
    private String studentToken;
    private String teacherToken;

    @BeforeEach
    void setUp() {
        students.records.add(student("S001", "stu001", "张三", "13800000001"));
        students.records.add(student("S002", "stu002", "李四", "13800000002"));
        users.register(new UserCredentials("stu001", "Demo123", "张三", Role.STUDENT.name()));
        users.register(new UserCredentials("teacher001", "Demo123", "教师", Role.TEACHER.name()));
        studentToken = users.login(new UserCredentials("stu001", "Demo123", "张三", Role.STUDENT.name()))
                .getData().getToken();
        teacherToken = users.login(new UserCredentials("teacher001", "Demo123", "教师", Role.TEACHER.name()))
                .getData().getToken();
        handler = new StudentMessageHandler(students, users);
    }

    @Test
    void studentCanReadOnlyOwnRecord() {
        assertEquals(StatusCode.OK, handler.handle(request(
                StudentQueryCommand.byId(studentToken, "S001"))).getStatusCode());
        assertEquals(StatusCode.FORBIDDEN, handler.handle(request(
                StudentQueryCommand.byId(studentToken, "S002"))).getStatusCode());
    }

    @Test
    void teacherCanReadStudentButCannotUpdate() {
        assertEquals(StatusCode.OK, handler.handle(request(
                StudentQueryCommand.byId(teacherToken, "S002"))).getStatusCode());
        assertEquals(StatusCode.FORBIDDEN, handler.handle(request(
                new StudentUpdateCommand(teacherToken, students.records.get(0)))).getStatusCode());
    }

    @Test
    void studentCanUpdateContactOnly() {
        StudentRecord original = students.records.get(0);
        StudentRecord contactChanged = student("S001", "stu001", "张三", "13900000000");
        assertEquals(StatusCode.OK, handler.handle(request(
                new StudentUpdateCommand(studentToken, contactChanged))).getStatusCode());

        StudentRecord nameChanged = student("S001", "stu001", "张三改名", "13900000000");
        assertEquals(StatusCode.FORBIDDEN, handler.handle(request(
                new StudentUpdateCommand(studentToken, nameChanged))).getStatusCode());
        assertEquals("张三", original.getName());
    }

    @Test
    void classQueryRequiresAcademicAdministratorScope() {
        assertEquals(StatusCode.FORBIDDEN, handler.handle(request(
                StudentQueryCommand.byClass(studentToken, "SE2023-01"))).getStatusCode());
    }

    private static Message request(Object payload) {
        MessageType type = payload instanceof StudentUpdateCommand
                ? MessageType.STUDENT_UPDATE : MessageType.STUDENT_QUERY;
        return Message.request("student-request", type, payload);
    }

    private static StudentRecord student(String id, String userId, String name, String phone) {
        return new StudentRecord(id, userId, name, "男", "计算机学院", "软件工程",
                "SE2023-01", 2023, "在读", phone, "student@example.com");
    }

    private static final class StubStudents implements StudentManagementService {
        private final List<StudentRecord> records = new ArrayList<StudentRecord>();

        @Override public ServiceResult<StudentRecord> findById(String id) {
            for (StudentRecord record : records) {
                if (record.getStudentId().equals(id)) return ServiceResult.ok(record);
            }
            return ServiceResult.failure(StatusCode.NOT_FOUND, "student not found");
        }

        @Override public ServiceResult<List<StudentRecord>> findByClass(String classId) {
            return ServiceResult.ok(Arrays.asList(records.get(0), records.get(1)));
        }

        @Override public ServiceResult<StudentRecord> save(StudentRecord record) {
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getStudentId().equals(record.getStudentId())) records.set(i, record);
            }
            return ServiceResult.ok(record);
        }
    }
}

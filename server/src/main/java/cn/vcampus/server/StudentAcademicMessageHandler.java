package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicReviewService;
import cn.vcampus.student.StudentAcademicQueryV1Command;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.CreditSummary;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Student-only academic reads; profile-read permission never grants other students' grades. */
final class StudentAcademicMessageHandler {
    private final StudentManagementService students;
    private final AcademicReviewService academics;
    private final UserManagementService users;

    StudentAcademicMessageHandler(StudentManagementService students, AcademicReviewService academics,
            UserManagementService users) {
        if (students == null || academics == null || users == null) {
            throw new IllegalArgumentException("academic query dependencies are required");
        }
        this.students = students;
        this.academics = academics;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.STUDENT_ACADEMIC_QUERY_V1,
                    null), StatusCode.BAD_REQUEST, "request is required");
        }
        if (request.getType() != MessageType.STUDENT_ACADEMIC_QUERY_V1) {
            return Message.response(request, StatusCode.NOT_FOUND, "unsupported academic query");
        }
        if (!(request.getPayload() instanceof StudentAcademicQueryV1Command)) {
            return Message.response(request, StatusCode.BAD_REQUEST, "invalid academic query");
        }
        StudentAcademicQueryV1Command command = (StudentAcademicQueryV1Command) request.getPayload();
        // Java deserialization does not invoke the command constructor.
        if (command.getToken() == null || command.getToken().trim().isEmpty()
                || command.getQueryType() == null) {
            return Message.response(request, StatusCode.BAD_REQUEST, "invalid academic query");
        }
        ServiceResult<Boolean> permission = users.authorize(command.getToken(),
                Permission.STUDENT_READ.getCode());
        if (permission.getStatus() != StatusCode.OK) {
            return response(request, permission);
        }
        if (!Boolean.TRUE.equals(permission.getData())) {
            return Message.response(request, StatusCode.FORBIDDEN, "academic query denied");
        }
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) {
            return response(request, session);
        }
        if (session.getData().getUser().getRole() != Role.STUDENT) {
            return Message.response(request, StatusCode.FORBIDDEN, "student self query only");
        }
        ServiceResult<StudentRecord> student = students.findByUserId(
                session.getData().getUser().getUserId());
        if (student.getStatus() != StatusCode.OK) {
            return response(request, student);
        }
        String studentId = student.getData().getStudentId();
        switch (command.getQueryType()) {
            case HISTORY:
                return response(request, academics.historyFor(studentId));
            case PENDING_RETAKES:
                return response(request, academics.pendingRetakes(studentId));
            case CREDITS:
                ServiceResult<java.util.List<CourseHistoryRecord>> history = academics.historyFor(studentId);
                if (history.getStatus() != StatusCode.OK) return response(request, history);
                try {
                    return response(request, ServiceResult.ok(CreditSummary.from(studentId, history.getData())));
                } catch (IllegalArgumentException | ArithmeticException invalid) {
                    return Message.response(request, StatusCode.SERVER_ERROR, "学分数据异常");
                }
            default:
                return Message.response(request, StatusCode.BAD_REQUEST, "unknown academic query");
        }
    }

    private static Message response(Message request, ServiceResult<?> result) {
        return Message.response(request, result.getStatus(),
                result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage());
    }
}

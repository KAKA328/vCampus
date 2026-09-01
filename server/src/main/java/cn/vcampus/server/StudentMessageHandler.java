package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentUpdateCommand;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Converts student-management Socket messages to service calls. */
final class StudentMessageHandler {
    private final StudentManagementService students;
    private final UserManagementService users;

    StudentMessageHandler(StudentManagementService students, UserManagementService users) {
        if (students == null || users == null) {
            throw new IllegalArgumentException("student handler dependencies must not be null");
        }
        this.students = students;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.STUDENT_QUERY, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case STUDENT_QUERY:
                    result = query(payload(request, StudentQueryCommand.class));
                    break;
                case STUDENT_UPDATE:
                    result = update(payload(request, StudentUpdateCommand.class));
                    break;
                default:
                    result = ServiceResult.failure(StatusCode.NOT_FOUND,
                            "student handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(StudentQueryCommand command) {
        ServiceResult<Session> current = requirePermission(command.getToken(), Permission.STUDENT_READ);
        if (current.getStatus() != StatusCode.OK) return current;
        if (current.getData().getUser().getRole() == Role.STUDENT) {
            return students.findByUserId(current.getData().getUser().getUserId());
        }
        if (command.getQueryType() == StudentQueryCommand.QueryType.BY_CLASS) {
            return students.findByClass(command.getClassId());
        }
        if (command.getQueryType() == StudentQueryCommand.QueryType.BY_ID) {
            return students.findById(command.getStudentId());
        }
        return students.findByUserId(current.getData().getUser().getUserId());
    }

    private ServiceResult<?> update(StudentUpdateCommand command) {
        ServiceResult<Session> current = requirePermission(command.getToken(), Permission.STUDENT_WRITE);
        return current.getStatus() == StatusCode.OK ? students.save(command.getRecord()) : current;
    }

    private ServiceResult<Session> requirePermission(String token, Permission permission) {
        ServiceResult<Boolean> authorized = users.authorize(token, permission.getCode());
        if (authorized.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorized.getStatus(), authorized.getMessage());
        }
        return users.currentSession(token);
    }

    private static <T> T payload(Message request, Class<T> type) {
        if (!type.isInstance(request.getPayload())) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(request.getPayload());
    }
}

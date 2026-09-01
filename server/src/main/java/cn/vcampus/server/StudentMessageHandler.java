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
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Adapts student-management results to the shared Socket message protocol. */
final class StudentMessageHandler {
    private final StudentManagementService students;
    private final UserManagementService users;

    StudentMessageHandler(StudentManagementService students, UserManagementService users) {
        if (students == null || users == null) {
            throw new IllegalArgumentException("students and users must not be null");
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
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "student handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(StudentQueryCommand command) {
        ServiceResult<Session> scope = authorize(command.getToken(), Permission.STUDENT_READ);
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        Role role = scope.getData().getUser().getRole();
        if (command.getQueryType() == StudentQueryCommand.QueryType.SELF) {
            return students.findByUserId(scope.getData().getUser().getUserId());
        }
        if (command.getQueryType() == StudentQueryCommand.QueryType.BY_CLASS) {
            if (role != Role.ADMIN && role != Role.ACADEMIC_ADMIN) {
                return ServiceResult.failure(StatusCode.FORBIDDEN, "class student query denied");
            }
            return students.findByClass(command.getValue());
        }

        ServiceResult<StudentRecord> record = students.findById(command.getValue());
        if (record.getStatus() != StatusCode.OK) {
            return record;
        }
        if (role == Role.STUDENT && !owns(scope.getData(), record.getData())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "student scope denied");
        }
        return record;
    }

    private ServiceResult<StudentRecord> update(StudentUpdateCommand command) {
        ServiceResult<Session> scope = authorize(command.getToken(), Permission.STUDENT_READ);
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        Role role = scope.getData().getUser().getRole();
        StudentRecord record = command.getRecord();
        if (role == Role.STUDENT) {
            ServiceResult<StudentRecord> existing = students.findById(record.getStudentId());
            if (existing.getStatus() != StatusCode.OK) {
                return existing;
            }
            if (!owns(scope.getData(), existing.getData()) || !contactOnly(existing.getData(), record)) {
                return ServiceResult.failure(StatusCode.FORBIDDEN, "student update scope denied");
            }
        } else {
            ServiceResult<Boolean> writePermission = users.authorize(
                    command.getToken(), Permission.STUDENT_WRITE.getCode());
            if (writePermission.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(writePermission.getStatus(), writePermission.getMessage());
            }
        }
        return students.save(record);
    }

    private ServiceResult<Session> authorize(String token, Permission permission) {
        ServiceResult<Boolean> permissionResult = users.authorize(token, permission.getCode());
        if (permissionResult.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(permissionResult.getStatus(), permissionResult.getMessage());
        }
        return users.currentSession(token);
    }

    private static boolean owns(Session session, StudentRecord record) {
        return session.getUser().getUserId().equals(record.getUserId())
                || session.getUser().getUserId().equals(record.getStudentId());
    }

    private static boolean contactOnly(StudentRecord oldRecord, StudentRecord newRecord) {
        return equals(oldRecord.getStudentId(), newRecord.getStudentId())
                && equals(oldRecord.getUserId(), newRecord.getUserId())
                && equals(oldRecord.getName(), newRecord.getName())
                && equals(oldRecord.getGender(), newRecord.getGender())
                && equals(oldRecord.getDepartmentName(), newRecord.getDepartmentName())
                && equals(oldRecord.getMajorName(), newRecord.getMajorName())
                && equals(oldRecord.getClassId(), newRecord.getClassId())
                && oldRecord.getEnrollmentYear() == newRecord.getEnrollmentYear()
                && equals(oldRecord.getStatus(), newRecord.getStatus());
    }

    private static boolean equals(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}

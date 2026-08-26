package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.InMemoryAcademicReviewService;
import cn.vcampus.student.StudentManagementService;
import cn.vcampus.student.StudentQueryCommand;
import cn.vcampus.student.StudentReviewCommand;
import cn.vcampus.student.StudentSaveCommand;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Converts student-management requests to shared Socket responses. */
final class StudentMessageHandler {
    private final StudentManagementService students;
    private final InMemoryAcademicReviewService reviews;
    private final UserManagementService users;

    StudentMessageHandler(StudentManagementService students,
            InMemoryAcademicReviewService reviews,
            UserManagementService users) {
        if (students == null || reviews == null || users == null) {
            throw new IllegalArgumentException("students, reviews and users must not be null");
        }
        this.students = students;
        this.reviews = reviews;
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
                    result = update(payload(request, StudentSaveCommand.class));
                    break;
                case STUDENT_REVIEW:
                    result = review(payload(request, StudentReviewCommand.class));
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
        ServiceResult<Session> scope = authorizeStudentReadScope(command.getToken(), command.getStudentId());
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        if (command.getStudentId() != null) {
            return students.findById(command.getStudentId());
        }
        return students.findByClass(command.getClassId());
    }

    private ServiceResult<?> update(StudentSaveCommand command) {
        ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.STUDENT_WRITE.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        return students.save(command.getRecord());
    }

    private ServiceResult<?> review(StudentReviewCommand command) {
        ServiceResult<Session> current = users.currentSession(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        Role role = current.getData().getUser().getRole();
        if (role == Role.STUDENT) {
            if (!current.getData().getUser().getUserId().equals(command.getStudentId())) {
                return ServiceResult.failure(StatusCode.FORBIDDEN, "student review scope denied");
            }
            ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.STUDENT_READ.getCode());
            if (authorization.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
            }
            return reviews.review(command.getStudentId(), command.getRequiredCredits());
        }

        ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.ACADEMIC_REVIEW.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        return reviews.review(command.getStudentId(), command.getRequiredCredits());
    }

    private ServiceResult<Session> authorizeStudentReadScope(String token, String studentId) {
        ServiceResult<Boolean> authorization = users.authorize(token, Permission.STUDENT_READ.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        ServiceResult<Session> current = users.currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return current;
        }
        Role role = current.getData().getUser().getRole();
        if (role == Role.ADMIN || role == Role.ACADEMIC_ADMIN || role == Role.TEACHER) {
            return current;
        }
        if (role == Role.STUDENT && studentId != null
                && current.getData().getUser().getUserId().equals(studentId)) {
            return current;
        }
        return ServiceResult.failure(StatusCode.FORBIDDEN, "student scope denied");
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}

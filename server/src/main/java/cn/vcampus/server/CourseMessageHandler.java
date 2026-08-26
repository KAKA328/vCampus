package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseQueryCommand;
import cn.vcampus.course.CourseSelectionCommand;
import cn.vcampus.course.CourseSelectionService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/**
 * 将选课模块的业务结果转换为统一的 Socket 消息响应。
 *
 * <p>该处理器仅负责一条选课请求的校验和转发；服务器总入口何时调用它，
 * 由组长在 ServerApplication 中统一接入。</p>
 */
final class CourseMessageHandler {
    private final CourseSelectionService courses;
    private final UserManagementService users;

    CourseMessageHandler(CourseSelectionService courses, UserManagementService users) {
        if (courses == null || users == null) {
            throw new IllegalArgumentException("courses and users must not be null");
        }
        this.courses = courses;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.COURSE_QUERY, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }

        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case COURSE_QUERY:
                    result = query(request);
                    break;
                case COURSE_SELECT:
                    CourseSelectionCommand select = payload(request, CourseSelectionCommand.class);
                    result = select(select);
                    break;
                case COURSE_DROP:
                    CourseSelectionCommand drop = payload(request, CourseSelectionCommand.class);
                    result = drop(drop);
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "course handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<Void> select(CourseSelectionCommand command) {
        ServiceResult<Session> scope = authorizeStudentScope(
                command.getToken(), command.getStudentId(), Permission.COURSE_SELECT);
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        return courses.select(command.getStudentId(), command.getCourseId());
    }

    private ServiceResult<?> query(Message request) {
        if (request.getPayload() == null) {
            return courses.listCourses();
        }

        CourseQueryCommand command = payload(request, CourseQueryCommand.class);
        switch (command.getQueryType()) {
            case ALL_COURSES:
                return courses.listCourses();
            case SELECTED_COURSES:
                ServiceResult<Session> scope = authorizeStudentScope(
                        command.getToken(), command.getStudentId(), Permission.COURSE_READ);
                if (scope.getStatus() != StatusCode.OK) {
                    return ServiceResult.failure(scope.getStatus(), scope.getMessage());
                }
                return courses.selectedCourses(command.getStudentId());
            default:
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "unknown course query type");
        }
    }

    private ServiceResult<Void> drop(CourseSelectionCommand command) {
        ServiceResult<Session> scope = authorizeStudentScope(
                command.getToken(), command.getStudentId(), Permission.COURSE_SELECT);
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        return courses.drop(command.getStudentId(), command.getCourseId());
    }

    private ServiceResult<Session> authorizeStudentScope(String token, String studentId, Permission permission) {
        ServiceResult<Boolean> authorization = users.authorize(token, permission.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }

        ServiceResult<Session> current = users.currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return current;
        }

        Session session = current.getData();
        Role role = session.getUser().getRole();
        if (role == Role.ADMIN) {
            return current;
        }
        if (role == Role.STUDENT && session.getUser().getUserId().equals(studentId)) {
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

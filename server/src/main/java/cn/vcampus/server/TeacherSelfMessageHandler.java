package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;

/** Read-only teacher self profile, including inactive employment status. */
final class TeacherSelfMessageHandler {
    private final TeacherProfileService teachers;
    private final UserManagementService users;

    TeacherSelfMessageHandler(TeacherProfileService teachers, UserManagementService users) {
        if (teachers == null || users == null) throw new IllegalArgumentException("services are required");
        this.teachers = teachers;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.TEACHER_SELF_QUERY_V1, null),
                    StatusCode.BAD_REQUEST, "request is required");
        }
        if (!(request.getPayload() instanceof TeacherSelfQueryV1Command)) {
            return Message.response(request, StatusCode.BAD_REQUEST, "invalid teacher query");
        }
        String token = ((TeacherSelfQueryV1Command) request.getPayload()).getToken();
        if (token == null || token.trim().isEmpty()) {
            return Message.response(request, StatusCode.BAD_REQUEST, "token is required");
        }
        ServiceResult<Boolean> permission = users.authorize(token, Permission.USER_SELF_READ.getCode());
        if (permission.getStatus() != StatusCode.OK || !Boolean.TRUE.equals(permission.getData())) {
            return Message.response(request, permission.getStatus() == StatusCode.OK
                    ? StatusCode.FORBIDDEN : permission.getStatus(), "teacher self query denied");
        }
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) {
            return Message.response(request, session.getStatus(), session.getMessage());
        }
        if (session.getData().getUser().getRole() != Role.TEACHER) {
            return Message.response(request, StatusCode.FORBIDDEN, "teacher self query only");
        }
        ServiceResult<TeacherProfile> result = teachers.findByUserId(session.getData().getUser().getUserId());
        return Message.response(request, result.getStatus(),
                result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage());
    }
}

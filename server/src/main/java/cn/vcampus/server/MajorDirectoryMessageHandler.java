package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;

final class MajorDirectoryMessageHandler {
    private final MajorDirectoryService majors;
    private final UserManagementService users;
    MajorDirectoryMessageHandler(MajorDirectoryService majors, UserManagementService users) {
        this.majors = majors; this.users = users;
    }
    Message handle(Message request) {
        if (!(request.getPayload() instanceof MajorDirectoryQueryV1Command)) {
            return Message.response(request, StatusCode.BAD_REQUEST, "专业目录查询参数不正确");
        }
        MajorDirectoryQueryV1Command command = (MajorDirectoryQueryV1Command) request.getPayload();
        try { command.validate(); }
        catch (IllegalArgumentException invalid) { return Message.response(request, StatusCode.BAD_REQUEST, invalid.getMessage()); }
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) {
            return Message.response(request, session.getStatus(), session.getMessage());
        }
        Role role = session.getData().getUser().getRole();
        if (role != Role.ADMIN && role != Role.ACADEMIC_ADMIN) {
            return Message.response(request, StatusCode.FORBIDDEN, "仅教务或系统管理员可查询培养方案专业目录");
        }
        ServiceResult<?> result = majors.listActive();
        return Message.response(request, result.getStatus(),
                result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage());
    }
}

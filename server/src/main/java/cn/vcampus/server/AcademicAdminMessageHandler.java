package cn.vcampus.server;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.user.*;

final class AcademicAdminMessageHandler {
    private final AcademicAdminService service;
    private final UserManagementService users;
    AcademicAdminMessageHandler(AcademicAdminService service, UserManagementService users) {
        this.service = service; this.users = users;
    }
    Message handle(Message request) {
        if (request.getType() == MessageType.ACADEMIC_ADMIN_OVERVIEW_V1
                && request.getPayload() instanceof AcademicAdminOverviewV1Command) {
            return handleOverview(request, (AcademicAdminOverviewV1Command) request.getPayload());
        }
        AcademicAdminCommand command;
        if (request.getType() == MessageType.ACADEMIC_ADMIN_V1
                && request.getPayload() instanceof AcademicAdminCommandV1) {
            command = (AcademicAdminCommandV1) request.getPayload();
        } else if (request.getType() == MessageType.ACADEMIC_ADMIN_V2
                && request.getPayload() instanceof AcademicAdminCommandV2) {
            command = (AcademicAdminCommandV2) request.getPayload();
        } else {
            return Message.response(request, StatusCode.BAD_REQUEST, "错误的教务命令");
        }
        try { command.validate(); }
        catch (IllegalArgumentException invalid) {
            return Message.response(request, StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) return Message.response(request, session.getStatus(), session.getMessage());
        Role role = session.getData().getUser().getRole();
        if (role != Role.ACADEMIC_ADMIN) {
            return Message.response(request, StatusCode.FORBIDDEN, "仅教务管理员可办理");
        }
        boolean write = command.getAction() == AcademicAdminCommandV1.Action.REVIEW
                || command.getAction() == AcademicAdminCommandV1.Action.GRADUATE;
        ServiceResult<Boolean> permission = users.authorize(command.getToken(),
                (write ? Permission.ACADEMIC_REVIEW : Permission.STUDENT_READ).getCode());
        if (permission.getStatus() != StatusCode.OK || !Boolean.TRUE.equals(permission.getData())) {
            return Message.response(request, permission.getStatus() == StatusCode.OK
                    ? StatusCode.FORBIDDEN : permission.getStatus(), "没有操作权限");
        }
        ServiceResult<?> result = service.execute(command, session.getData().getUser().getUserId());
        return Message.response(request, result.getStatus(),
                result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage());
    }

    private Message handleOverview(Message request, AcademicAdminOverviewV1Command command) {
        try { command.validate(); }
        catch (IllegalArgumentException invalid) {
            return Message.response(request, StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        ServiceResult<Session> session = users.currentSession(command.getToken());
        if (session.getStatus() != StatusCode.OK) {
            return Message.response(request, session.getStatus(), session.getMessage());
        }
        if (session.getData().getUser().getRole() != Role.ACADEMIC_ADMIN) {
            return Message.response(request, StatusCode.FORBIDDEN, "仅教务管理员可办理");
        }
        ServiceResult<Boolean> permission = users.authorize(command.getToken(),
                Permission.STUDENT_READ.getCode());
        if (permission.getStatus() != StatusCode.OK || !Boolean.TRUE.equals(permission.getData())) {
            return Message.response(request, permission.getStatus() == StatusCode.OK
                    ? StatusCode.FORBIDDEN : permission.getStatus(), "没有操作权限");
        }
        ServiceResult<GraduationReviewOverview> result = service.overview(command.getStudentId());
        return Message.response(request, result.getStatus(),
                result.getStatus() == StatusCode.OK ? result.getData() : result.getMessage());
    }
}

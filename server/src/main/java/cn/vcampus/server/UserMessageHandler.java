package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.AuthorizationRequest;
import cn.vcampus.user.PasswordChangeCommand;
import cn.vcampus.user.PasswordResetRequestCommand;
import cn.vcampus.user.PasswordResetReviewCommand;
import cn.vcampus.user.Permission;
import cn.vcampus.user.UserCommand;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.user.UserCredentials;
import cn.vcampus.user.UserImportCommand;
import cn.vcampus.user.UserRegistrationCommand;
import cn.vcampus.user.UserRoleChangeCommand;
import cn.vcampus.user.UserStatusCommand;

/** Adapts user-management service results to the shared Socket message protocol. */
final class UserMessageHandler {
    private final UserManagementService service;

    UserMessageHandler(UserManagementService service) { this.service = service; }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.LOGIN, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case REGISTER:
                    UserRegistrationCommand registration = payload(request, UserRegistrationCommand.class);
                    result = service.authorize(registration.getToken(), Permission.USER_MANAGE.getCode());
                    if (result.getStatus() == StatusCode.OK) {
                        result = service.register(registration.getCredentials());
                    }
                    break;
                case LOGIN:
                    result = service.login(payload(request, UserCredentials.class));
                    break;
                case USER_IMPORT:
                    UserImportCommand importCommand = payload(request, UserImportCommand.class);
                    result = service.importUsers(importCommand.getToken(), importCommand.getRows());
                    break;
                case USER_LIST:
                    result = service.listAccounts(payload(request, String.class));
                    break;
                case USER_ENABLE:
                    result = service.setAccountActive(payload(request, UserStatusCommand.class));
                    break;
                case USER_DISABLE:
                    result = service.setAccountActive(payload(request, UserStatusCommand.class));
                    break;
                case USER_ROLE_CHANGE:
                    result = service.changeUserRole(payload(request, UserRoleChangeCommand.class));
                    break;
                case USER_AUDIT_LIST:
                    result = service.listAuditEvents(payload(request, String.class));
                    break;
                case PASSWORD_RESET_REQUEST:
                    result = service.requestPasswordReset(payload(request, PasswordResetRequestCommand.class));
                    break;
                case PASSWORD_RESET_LIST:
                    result = service.listPasswordResetApplications(payload(request, String.class));
                    break;
                case PASSWORD_RESET_REVIEW:
                    result = service.reviewPasswordReset(payload(request, PasswordResetReviewCommand.class));
                    break;
                case PASSWORD_CHANGE:
                    result = service.changeForcedPassword(payload(request, PasswordChangeCommand.class));
                    break;
                case UNREGISTER:
                    UserCommand unregister = payload(request, UserCommand.class);
                    result = service.unregister(unregister.getUserId(), unregister.getToken());
                    break;
                case LOGOUT:
                    result = service.logout(payload(request, String.class));
                    break;
                case AUTHORIZE:
                    AuthorizationRequest authorization = payload(request, AuthorizationRequest.class);
                    result = service.authorize(authorization.getToken(), authorization.getPermission());
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND, "user handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) throw new IllegalArgumentException("unexpected payload type");
        return type.cast(payload);
    }
}

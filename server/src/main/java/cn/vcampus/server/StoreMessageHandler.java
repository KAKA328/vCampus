package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.StoreCommand;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.Permission;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserManagementService;

/** Converts store requests to shared Socket responses. */
final class StoreMessageHandler {
    private final StoreService store;
    private final UserManagementService users;

    StoreMessageHandler(StoreService store, UserManagementService users) {
        if (store == null || users == null) {
            throw new IllegalArgumentException("store and users must not be null");
        }
        this.store = store;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.STORE_QUERY, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case STORE_QUERY:
                    result = query((String) request.getPayload());
                    break;
                case STORE_PURCHASE:
                    result = purchase(payload(request, StoreCommand.class));
                    break;
                case STORE_ORDER_QUERY:
                    result = orders(payload(request, StoreOrderQueryCommand.class));
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND,
                            "store handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (ClassCastException | IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<?> query(String token) {
        ServiceResult<Boolean> authorization = users.authorize(token, Permission.STORE_READ.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        return store.listProducts();
    }

    private ServiceResult<?> purchase(StoreCommand command) {
        ServiceResult<Session> scope = authorizeBuyerScope(command.getToken(), command.getBuyerId());
        if (scope.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(scope.getStatus(), scope.getMessage());
        }
        return store.purchase(command.getBuyerId(), command.getProductId(), command.getQuantity());
    }

    private ServiceResult<?> orders(StoreOrderQueryCommand command) {
        ServiceResult<Session> current = users.currentSession(command.getToken());
        if (current.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(current.getStatus(), current.getMessage());
        }
        Role role = current.getData().getUser().getRole();
        if (command.isAllOrders()) {
            ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.STORE_MANAGE.getCode());
            if (authorization.getStatus() != StatusCode.OK) {
                return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
            }
            return store.allOrders();
        }
        ServiceResult<Boolean> authorization = users.authorize(command.getToken(), Permission.STORE_PURCHASE.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        if (role != Role.ADMIN && role != Role.STORE_MANAGER
                && !current.getData().getUser().getUserId().equals(command.getBuyerId())) {
            return ServiceResult.failure(StatusCode.FORBIDDEN, "store order scope denied");
        }
        return store.ordersFor(command.getBuyerId());
    }

    private ServiceResult<Session> authorizeBuyerScope(String token, String buyerId) {
        ServiceResult<Boolean> authorization = users.authorize(token, Permission.STORE_PURCHASE.getCode());
        if (authorization.getStatus() != StatusCode.OK) {
            return ServiceResult.failure(authorization.getStatus(), authorization.getMessage());
        }
        ServiceResult<Session> current = users.currentSession(token);
        if (current.getStatus() != StatusCode.OK) {
            return current;
        }
        Role role = current.getData().getUser().getRole();
        if (role == Role.ADMIN || role == Role.STORE_MANAGER) {
            return current;
        }
        if ((role == Role.STUDENT || role == Role.TEACHER)
                && current.getData().getUser().getUserId().equals(buyerId)) {
            return current;
        }
        return ServiceResult.failure(StatusCode.FORBIDDEN, "store buyer scope denied");
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}

package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.user.Session;

class StoreMessageHandler {
    private final StoreService store;
    private final UserManagementService users;

    StoreMessageHandler(StoreService store, UserManagementService users) {
        this.store = store;
        this.users = users;
    }

    Message handle(Message request) {
        if (request == null) {
            return Message.response(Message.request("invalid", MessageType.LOGIN, null),
                    StatusCode.BAD_REQUEST, "request is invalid");
        }

        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                // 仓库查询请求
                case STORE_QUERY:
                    StoreQueryCommand payload = payload(request, StoreQueryCommand.class);
                    ServiceResult<Boolean> auth0 = users.authorize(payload.getToken(), "STORE_READ");
                    if (auth0.getStatus() != StatusCode.OK) {
                        result = auth0;
                        break;
                    }
                    result = store.listProducts();
                    break;
                // 仓库购买请求
                case STORE_PURCHASE:
                    StorePurchaseCommand spc = payload(request, StorePurchaseCommand.class);
                    // 验证权限
                    ServiceResult<Boolean> auth1 = users.authorize(spc.getToken(), "STORE_PURCHASE");
                    if (auth1.getStatus() != StatusCode.OK) {
                        result = auth1;
                        break;
                    }
                    // 通过token获取userId作为唯一可信身份标识
                    String currToken1 = spc.getToken();
                    ServiceResult<Session> sessionResult = users.currentSession(currToken1);
                    if (sessionResult.getStatus() != StatusCode.OK) {
                        result = sessionResult;
                        break;
                    }
                    // 提取可信userId
                    String userId1 = sessionResult.getData().getUser().getUserId();
                    result = store.purchase(userId1, spc.getProductId(), spc.getQuantity());
                    break;
                // 仓库订单查询请求
                case STORE_ORDER_QUERY:
                    StoreOrderQueryCommand soqc = payload(request, StoreOrderQueryCommand.class);
                    ServiceResult<Boolean> auth2 = users.authorize(soqc.getToken(), "STORE_READ");
                    if (auth2.getStatus() != StatusCode.OK) {
                        result = auth2;
                        break;
                    }
                    // 通过token获取userId作为唯一可信身份标识
                    String currToken2 = soqc.getToken();
                    ServiceResult<Session> sessionResult2 = users.currentSession(currToken2);
                    if (sessionResult2.getStatus() != StatusCode.OK) {
                        result = sessionResult2;
                        break;
                    }
                    // 提取可信userId
                    String userId2 = sessionResult2.getData().getUser().getUserId();
                    result = store.findOrdersByUserId(userId2);
                    break;
                default:
                    result = ServiceResult.failure(StatusCode.NOT_FOUND, "not implemented");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    // 取出请求的 payload 并进行类型检查
    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload(); // 取出 payload
        if (!type.isInstance(payload)) // 检查是不是期望的类型
            throw new IllegalArgumentException("unexpected payload type");
        return type.cast(payload); // 类型转换并返回
    }
}

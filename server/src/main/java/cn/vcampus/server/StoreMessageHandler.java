package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.UserManagementService;
import cn.vcampus.store.StoreOrderQueryCommand;

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
                    result = store.purchase(spc.getStudentId(), spc.getProductId(), spc.getQuantity());
                    break;
                // 仓库订单查询请求
                case STORE_ORDER_QUERY:
                    StoreOrderQueryCommand soqc = payload(request, StoreOrderQueryCommand.class);
                    ServiceResult<Boolean> auth2 = users.authorize(soqc.getToken(), "STORE_READ");
                    if (auth2.getStatus() != StatusCode.OK) {
                        result = auth2;
                        break;
                    }
                    result = store.findOrdersByStudentId(soqc.getStudentId());
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

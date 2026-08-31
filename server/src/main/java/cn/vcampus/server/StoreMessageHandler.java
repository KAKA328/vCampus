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
import cn.vcampus.store.StoreRestockCommand;
import cn.vcampus.store.StoreProductAddCommand;
import cn.vcampus.store.StoreProductUpdateCommand;
import cn.vcampus.store.StoreProductDeactivateCommand;
import cn.vcampus.store.CartAddCommand;
import cn.vcampus.store.CartRemoveCommand;
import cn.vcampus.store.CartQueryCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
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
                    result = payload.getCategory() == null || payload.getCategory().trim().isEmpty()
                            ? store.listProducts() : store.listProducts(payload.getCategory());
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
                case STORE_RESTOCK:
                    StoreRestockCommand restock = payload(request, StoreRestockCommand.class);
                    ServiceResult<Void> restockAuth = requirePermission(restock.getToken(), "STORE_MANAGE");
                    result = restockAuth.getStatus() != StatusCode.OK ? restockAuth
                            : store.restock(requireUserId(restock.getToken()), restock.getProductId(), restock.getAdditionalStock());
                    break;
                case STORE_PRODUCT_ADD:
                    StoreProductAddCommand add = payload(request, StoreProductAddCommand.class);
                    ServiceResult<Void> addAuth = requirePermission(add.getToken(), "STORE_MANAGE");
                    result = addAuth.getStatus() != StatusCode.OK ? addAuth
                            : store.addProduct(add.getName(), add.getPrice(), add.getStock(), add.getDescription(), add.getCategory());
                    break;
                case STORE_PRODUCT_UPDATE:
                    StoreProductUpdateCommand update = payload(request, StoreProductUpdateCommand.class);
                    ServiceResult<Void> updateAuth = requirePermission(update.getToken(), "STORE_MANAGE");
                    result = updateAuth.getStatus() != StatusCode.OK ? updateAuth
                            : store.updateProduct(update.getProductId(), update.getName(), update.getPrice(),
                                    update.getDescription(), update.getCategory());
                    break;
                case STORE_PRODUCT_DEACTIVATE:
                    StoreProductDeactivateCommand deactivate = payload(request, StoreProductDeactivateCommand.class);
                    ServiceResult<Void> deactivateAuth = requirePermission(deactivate.getToken(), "STORE_MANAGE");
                    result = deactivateAuth.getStatus() != StatusCode.OK ? deactivateAuth
                            : store.deactivateProduct(requireUserId(deactivate.getToken()), deactivate.getProductId());
                    break;
                case STORE_CART_ADD:
                    CartAddCommand cartAdd = payload(request, CartAddCommand.class);
                    ServiceResult<Void> cartAddAuth = requirePermission(cartAdd.getToken(), "STORE_PURCHASE");
                    result = cartAddAuth.getStatus() != StatusCode.OK ? cartAddAuth
                            : store.addToCart(requireUserId(cartAdd.getToken()), cartAdd.getProductId(), cartAdd.getQuantity());
                    break;
                case STORE_CART_REMOVE:
                    CartRemoveCommand cartRemove = payload(request, CartRemoveCommand.class);
                    ServiceResult<Void> cartRemoveAuth = requirePermission(cartRemove.getToken(), "STORE_PURCHASE");
                    result = cartRemoveAuth.getStatus() != StatusCode.OK ? cartRemoveAuth
                            : store.removeFromCart(requireUserId(cartRemove.getToken()), cartRemove.getCartItemId());
                    break;
                case STORE_CART_QUERY:
                    CartQueryCommand cartQuery = payload(request, CartQueryCommand.class);
                    ServiceResult<Void> cartQueryAuth = requirePermission(cartQuery.getToken(), "STORE_READ");
                    result = cartQueryAuth.getStatus() != StatusCode.OK ? cartQueryAuth
                            : store.getCart(requireUserId(cartQuery.getToken()));
                    break;
                case STORE_CART_CHECKOUT:
                    CartCheckoutCommand checkout = payload(request, CartCheckoutCommand.class);
                    ServiceResult<Void> checkoutAuth = requirePermission(checkout.getToken(), "STORE_PURCHASE");
                    result = checkoutAuth.getStatus() != StatusCode.OK ? checkoutAuth
                            : store.checkout(requireUserId(checkout.getToken()));
                    break;
                case STORE_ORDER_LIST_ALL:
                    StoreOrderListAllCommand all = payload(request, StoreOrderListAllCommand.class);
                    ServiceResult<Void> allAuth = requirePermission(all.getToken(), "STORE_MANAGE");
                    result = allAuth.getStatus() != StatusCode.OK ? allAuth : store.findAllOrders();
                    break;
                case STORE_HOT_PRODUCTS:
                    StoreHotProductsCommand hot = payload(request, StoreHotProductsCommand.class);
                    ServiceResult<Void> hotAuth = requirePermission(hot.getToken(), "STORE_READ");
                    result = hotAuth.getStatus() != StatusCode.OK ? hotAuth : store.listHotProducts(hot.getLimit());
                    break;
                default:
                    result = ServiceResult.failure(StatusCode.NOT_FOUND, "not implemented");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private ServiceResult<Void> requirePermission(String token, String permission) {
        ServiceResult<Boolean> auth = users.authorize(token, permission);
        return auth.getStatus() == StatusCode.OK ? ServiceResult.ok(null)
                : ServiceResult.failure(auth.getStatus(), auth.getMessage());
    }

    private String requireUserId(String token) {
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK) throw new IllegalArgumentException("invalid session");
        return session.getData().getUser().getUserId();
    }

    // 取出请求的 payload 并进行类型检查
    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload(); // 取出 payload
        if (!type.isInstance(payload)) // 检查是不是期望的类型
            throw new IllegalArgumentException("unexpected payload type");
        return type.cast(payload); // 类型转换并返回
    }
}

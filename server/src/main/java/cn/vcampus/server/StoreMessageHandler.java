package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StoreRestockCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreProductAddCommand;
import cn.vcampus.store.StoreProductUpdateCommand;
import cn.vcampus.store.StoreProductDeactivateCommand;
import cn.vcampus.store.CartAddCommand;
import cn.vcampus.store.CartRemoveCommand;
import cn.vcampus.store.CartQueryCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
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
                    if (payload.getCategory() != null) {
                        result = store.listProducts(payload.getCategory());
                    } else {
                        result = store.listProducts();
                    }
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
                // 更新库存请求
                case STORE_RESTOCK:
                    StoreRestockCommand src = payload(request, StoreRestockCommand.class);
                    ServiceResult<Boolean> auth3 = users.authorize(src.getToken(), "STORE_MANAGE");
                    if (auth3.getStatus() != StatusCode.OK) {
                        result = auth3;
                        break;
                    }
                    String currToken3 = src.getToken();
                    ServiceResult<Session> sessionResult3 = users.currentSession(currToken3);
                    if (sessionResult3.getStatus() != StatusCode.OK) {
                        result = sessionResult3;
                        break;
                    }
                    String userId3 = sessionResult3.getData().getUser().getUserId();
                    result = store.restock(userId3, src.getProductId(), src.getAdditionalStock());
                    break;
                // 添加商品请求
                case STORE_PRODUCT_ADD:
                    StoreProductAddCommand spac = payload(request, StoreProductAddCommand.class);
                    ServiceResult<Boolean> auth4 = users.authorize(spac.getToken(), "STORE_MANAGE");
                    if (auth4.getStatus() != StatusCode.OK) {
                        result = auth4;
                        break;
                    }
                    result = store.addProduct(spac.getName(), spac.getPrice(), spac.getStock(), spac.getDescription(),
                            spac.getCategory());
                    break;
                // 更新商品请求
                case STORE_PRODUCT_UPDATE:
                    StoreProductUpdateCommand spuc = payload(request, StoreProductUpdateCommand.class);
                    ServiceResult<Boolean> auth5 = users.authorize(spuc.getToken(), "STORE_MANAGE");
                    if (auth5.getStatus() != StatusCode.OK) {
                        result = auth5;
                        break;
                    }
                    result = store.updateProduct(spuc.getProductId(), spuc.getName(), spuc.getPrice(),
                            spuc.getDescription(), spuc.getCategory());
                    break;
                // 下架商品请求
                case STORE_PRODUCT_DEACTIVATE:
                    StoreProductDeactivateCommand spd = payload(request, StoreProductDeactivateCommand.class);
                    ServiceResult<Boolean> auth6 = users.authorize(spd.getToken(), "STORE_MANAGE");
                    if (auth6.getStatus() != StatusCode.OK) {
                        result = auth6;
                        break;
                    }
                    String currToken6 = spd.getToken();
                    ServiceResult<Session> sessionResult6 = users.currentSession(currToken6);
                    if (sessionResult6.getStatus() != StatusCode.OK) {
                        result = sessionResult6;
                        break;
                    }
                    String userId6 = sessionResult6.getData().getUser().getUserId();
                    result = store.deactivateProduct(userId6, spd.getProductId());
                    break;
                // 购物车添加请求
                case CART_ADD:
                    CartAddCommand cadd = payload(request, CartAddCommand.class);
                    ServiceResult<Boolean> auth7 = users.authorize(cadd.getToken(), "STORE_PURCHASE");
                    if (auth7.getStatus() != StatusCode.OK) {
                        result = auth7;
                        break;
                    }
                    String currToken7 = cadd.getToken();
                    ServiceResult<Session> sessionResult7 = users.currentSession(currToken7);
                    if (sessionResult7.getStatus() != StatusCode.OK) {
                        result = sessionResult7;
                        break;
                    }
                    String userId7 = sessionResult7.getData().getUser().getUserId();
                    result = store.addToCart(userId7, cadd.getProductId(), cadd.getQuantity());
                    break;
                // 购物车移除请求
                case CART_REMOVE:
                    CartRemoveCommand cremove = payload(request, CartRemoveCommand.class);
                    ServiceResult<Boolean> auth8 = users.authorize(cremove.getToken(), "STORE_PURCHASE");
                    if (auth8.getStatus() != StatusCode.OK) {
                        result = auth8;
                        break;
                    }
                    String currToken8 = cremove.getToken();
                    ServiceResult<Session> sessionResult8 = users.currentSession(currToken8);
                    if (sessionResult8.getStatus() != StatusCode.OK) {
                        result = sessionResult8;
                        break;
                    }
                    String userId8 = sessionResult8.getData().getUser().getUserId();
                    result = store.removeFromCart(userId8, cremove.getCartItemId());
                    break;
                // 购物车查询请求
                case CART_QUERY:
                    CartQueryCommand cquery = payload(request, CartQueryCommand.class);
                    ServiceResult<Boolean> auth9 = users.authorize(cquery.getToken(), "STORE_READ");
                    if (auth9.getStatus() != StatusCode.OK) {
                        result = auth9;
                        break;
                    }
                    String currToken9 = cquery.getToken();
                    ServiceResult<Session> sessionResult9 = users.currentSession(currToken9);
                    if (sessionResult9.getStatus() != StatusCode.OK) {
                        result = sessionResult9;
                        break;
                    }
                    String userId9 = sessionResult9.getData().getUser().getUserId();
                    result = store.getCart(userId9);
                    break;
                // 购物车结算请求
                case CART_CHECKOUT:
                    CartCheckoutCommand ccheckout = payload(request, CartCheckoutCommand.class);
                    ServiceResult<Boolean> auth10 = users.authorize(ccheckout.getToken(), "STORE_PURCHASE");
                    if (auth10.getStatus() != StatusCode.OK) {
                        result = auth10;
                        break;
                    }
                    String currToken10 = ccheckout.getToken();
                    ServiceResult<Session> sessionResult10 = users.currentSession(currToken10);
                    if (sessionResult10.getStatus() != StatusCode.OK) {
                        result = sessionResult10;
                        break;
                    }
                    String userId10 = sessionResult10.getData().getUser().getUserId();
                    result = store.checkout(userId10);
                    break;
                // 管理员查询全部订单请求
                case STORE_ORDER_LIST_ALL:
                    StoreOrderListAllCommand solac = payload(request, StoreOrderListAllCommand.class);
                    ServiceResult<Boolean> auth11 = users.authorize(solac.getToken(), "STORE_MANAGE");
                    if (auth11.getStatus() != StatusCode.OK) {
                        result = auth11;
                        break;
                    }
                    result = store.findAllOrders();
                    break;
                // 热销商品排行请求
                case STORE_HOT_PRODUCTS:
                    StoreHotProductsCommand shpc = payload(request, StoreHotProductsCommand.class);
                    ServiceResult<Boolean> auth12 = users.authorize(shpc.getToken(), "STORE_READ");
                    if (auth12.getStatus() != StatusCode.OK) {
                        result = auth12;
                        break;
                    }
                    result = store.listHotProducts(shpc.getLimit());
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

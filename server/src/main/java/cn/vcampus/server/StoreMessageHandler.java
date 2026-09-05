package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
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
import cn.vcampus.store.CartUpdateCommand;
import cn.vcampus.store.CartQueryCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
import cn.vcampus.store.StoreAccountQueryCommand;
import cn.vcampus.store.StoreAccountRechargeCommand;
import cn.vcampus.store.StoreAccountAdjustCommand;
import cn.vcampus.user.Session;
import cn.vcampus.user.AuditEvent;
import cn.vcampus.user.AuditLogRepository;
import cn.vcampus.store.Product;
import java.time.Instant;

class StoreMessageHandler {
    private final StoreService store;
    private final UserManagementService users;
    // 商店敏感操作审计留痕（可空）：仅管理端 case 成功后写一条，best-effort，绝不影响业务响应
    private final AuditLogRepository auditLog;

    StoreMessageHandler(StoreService store, UserManagementService users) {
        this(store, users, null);
    }

    StoreMessageHandler(StoreService store, UserManagementService users, AuditLogRepository auditLog) {
        this.store = store;
        this.users = users;
        this.auditLog = auditLog;
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
                    ServiceResult<Void> queryAuth = requirePermission(payload.getToken(), "STORE_READ");
                    if (queryAuth.getStatus() != StatusCode.OK) {
                        result = queryAuth;
                        break;
                    }
                    result = payload.getCategory() == null || payload.getCategory().trim().isEmpty()
                            ? store.listProducts()
                            : store.listProducts(payload.getCategory());
                    break;
                // 仓库购买请求
                case STORE_PURCHASE:
                    StorePurchaseCommand spc = payload(request, StorePurchaseCommand.class);
                    ServiceResult<Void> purchaseAuth = requirePermission(spc.getToken(), "STORE_PURCHASE");
                    result = purchaseAuth.getStatus() != StatusCode.OK ? purchaseAuth
                            : store.purchase(requireUserId(spc.getToken()), spc.getProductId(), spc.getQuantity());
                    break;
                // 仓库订单查询请求
                case STORE_ORDER_QUERY:
                    StoreOrderQueryCommand soqc = payload(request, StoreOrderQueryCommand.class);
                    ServiceResult<Void> orderQueryAuth = requirePermission(soqc.getToken(), "STORE_READ");
                    result = orderQueryAuth.getStatus() != StatusCode.OK ? orderQueryAuth
                            : store.findOrdersByUserId(requireUserId(soqc.getToken()));
                    break;
                case STORE_RESTOCK:
                    StoreRestockCommand restock = payload(request, StoreRestockCommand.class);
                    ServiceResult<Void> restockAuth = requirePermission(restock.getToken(), "STORE_MANAGE");
                    result = restockAuth.getStatus() != StatusCode.OK ? restockAuth
                            : store.restock(restock.getProductId(), restock.getAdditionalStock());
                    if (result.getStatus() == StatusCode.OK)
                        recordAudit(requireUserId(restock.getToken()), "STORE_RESTOCK", "PRODUCT",
                                restock.getProductId());
                    break;
                case STORE_PRODUCT_ADD:
                    StoreProductAddCommand add = payload(request, StoreProductAddCommand.class);
                    ServiceResult<Void> addAuth = requirePermission(add.getToken(), "STORE_MANAGE");
                    result = addAuth.getStatus() != StatusCode.OK ? addAuth
                            : store.addProduct(add.getName(), add.getPrice(), add.getStock(), add.getDescription(),
                                    add.getCategory());
                    if (result.getStatus() == StatusCode.OK && result.getData() instanceof Product)
                        recordAudit(requireUserId(add.getToken()), "STORE_PRODUCT_ADD", "PRODUCT",
                                ((Product) result.getData()).getProductId());
                    break;
                case STORE_PRODUCT_UPDATE:
                    StoreProductUpdateCommand update = payload(request, StoreProductUpdateCommand.class);
                    ServiceResult<Void> updateAuth = requirePermission(update.getToken(), "STORE_MANAGE");
                    result = updateAuth.getStatus() != StatusCode.OK ? updateAuth
                            : store.updateProduct(update.getProductId(), update.getName(), update.getPrice(),
                                    update.getDescription(), update.getCategory());
                    if (result.getStatus() == StatusCode.OK)
                        recordAudit(requireUserId(update.getToken()), "STORE_PRODUCT_UPDATE", "PRODUCT",
                                update.getProductId());
                    break;
                case STORE_PRODUCT_DEACTIVATE:
                    StoreProductDeactivateCommand deactivate = payload(request, StoreProductDeactivateCommand.class);
                    ServiceResult<Void> deactivateAuth = requirePermission(deactivate.getToken(), "STORE_MANAGE");
                    result = deactivateAuth.getStatus() != StatusCode.OK ? deactivateAuth
                            : store.deactivateProduct(deactivate.getProductId());
                    if (result.getStatus() == StatusCode.OK)
                        recordAudit(requireUserId(deactivate.getToken()), "STORE_PRODUCT_DEACTIVATE", "PRODUCT",
                                deactivate.getProductId());
                    break;
                case STORE_CART_ADD:
                    CartAddCommand cartAdd = payload(request, CartAddCommand.class);
                    ServiceResult<Void> cartAddAuth = requirePermission(cartAdd.getToken(), "STORE_PURCHASE");
                    result = cartAddAuth.getStatus() != StatusCode.OK ? cartAddAuth
                            : store.addToCart(requireUserId(cartAdd.getToken()), cartAdd.getProductId(),
                                    cartAdd.getQuantity());
                    break;
                case STORE_CART_REMOVE:
                    CartRemoveCommand cartRemove = payload(request, CartRemoveCommand.class);
                    ServiceResult<Void> cartRemoveAuth = requirePermission(cartRemove.getToken(), "STORE_PURCHASE");
                    result = cartRemoveAuth.getStatus() != StatusCode.OK ? cartRemoveAuth
                            : store.removeFromCart(requireUserId(cartRemove.getToken()), cartRemove.getCartItemId());
                    break;
                // 改数量：STORE_PURCHASE 权限，userId 取自 token，服务层再校验条目归属，防指定他人条目越权
                case STORE_CART_UPDATE:
                    CartUpdateCommand cartUpdate = payload(request, CartUpdateCommand.class);
                    ServiceResult<Void> cartUpdateAuth = requirePermission(cartUpdate.getToken(), "STORE_PURCHASE");
                    result = cartUpdateAuth.getStatus() != StatusCode.OK ? cartUpdateAuth
                            : store.updateCartQuantity(requireUserId(cartUpdate.getToken()),
                                    cartUpdate.getCartItemId(), cartUpdate.getNewQuantity());
                    break;
                case STORE_CART_QUERY:
                    CartQueryCommand cartQuery = payload(request, CartQueryCommand.class);
                    ServiceResult<Void> cartQueryAuth = requirePermission(cartQuery.getToken(), "STORE_READ");
                    result = cartQueryAuth.getStatus() != StatusCode.OK ? cartQueryAuth
                            : store.getCart(requireUserId(cartQuery.getToken()));
                    break;
                // 购物车明细：复用 CartQueryCommand，返回读取时联表商品后的 CartLine 列表
                case STORE_CART_DETAIL:
                    CartQueryCommand cartDetail = payload(request, CartQueryCommand.class);
                    ServiceResult<Void> cartDetailAuth = requirePermission(cartDetail.getToken(), "STORE_READ");
                    result = cartDetailAuth.getStatus() != StatusCode.OK ? cartDetailAuth
                            : store.getCartDetails(requireUserId(cartDetail.getToken()));
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
                // 账户查询：STORE_READ 权限，userId 取自 token，返回余额（分）
                case STORE_ACCOUNT_QUERY:
                    StoreAccountQueryCommand accountQuery = payload(request, StoreAccountQueryCommand.class);
                    ServiceResult<Void> accountQueryAuth = requirePermission(accountQuery.getToken(), "STORE_READ");
                    result = accountQueryAuth.getStatus() != StatusCode.OK ? accountQueryAuth
                            : ServiceResult.ok(store.getBalance(requireUserId(accountQuery.getToken())));
                    break;
                // 本人充值：STORE_PURCHASE 权限，userId 取自 token，仅增加余额
                case STORE_ACCOUNT_RECHARGE:
                    StoreAccountRechargeCommand recharge = payload(request, StoreAccountRechargeCommand.class);
                    ServiceResult<Void> rechargeAuth = requirePermission(recharge.getToken(), "STORE_PURCHASE");
                    result = rechargeAuth.getStatus() != StatusCode.OK ? rechargeAuth
                            : store.recharge(requireUserId(recharge.getToken()), recharge.getAmountCents());
                    break;
                // 管理员校正：STORE_MANAGE 权限 + 角色 ∈ {ADMIN, STORE_MANAGER} 双重门槛，targetUserId 取自
                // payload
                case STORE_ACCOUNT_ADJUST:
                    StoreAccountAdjustCommand adjust = payload(request, StoreAccountAdjustCommand.class);
                    ServiceResult<Void> adjustAuth = requirePermission(adjust.getToken(), "STORE_MANAGE");
                    if (adjustAuth.getStatus() != StatusCode.OK) {
                        result = adjustAuth;
                        break;
                    }
                    ServiceResult<Session> adjustSession = users.currentSession(adjust.getToken());
                    if (adjustSession.getStatus() != StatusCode.OK) {
                        result = adjustSession;
                        break;
                    }
                    User adjustAdmin = adjustSession.getData().getUser();
                    if (adjustAdmin.getRole() != Role.ADMIN && adjustAdmin.getRole() != Role.STORE_MANAGER) {
                        result = ServiceResult.failure(StatusCode.FORBIDDEN, "role not allowed to adjust balance");
                        break;
                    }
                    result = store.adjustBalance(adjustAdmin.getUserId(), adjust.getTargetUserId(),
                            adjust.getNewBalanceCents());
                    if (result.getStatus() == StatusCode.OK)
                        recordAudit(adjustAdmin.getUserId(), "STORE_ACCOUNT_ADJUST", "ACCOUNT",
                                adjust.getTargetUserId());
                    break;
                // 本人流水：STORE_READ 权限，userId 取自 token，只能查自己的账，无法查他人流水
                case STORE_ACCOUNT_LEDGER:
                    StoreAccountQueryCommand ledgerQuery = payload(request, StoreAccountQueryCommand.class);
                    ServiceResult<Void> ledgerAuth = requirePermission(ledgerQuery.getToken(), "STORE_READ");
                    result = ledgerAuth.getStatus() != StatusCode.OK ? ledgerAuth
                            : store.listTransactions(requireUserId(ledgerQuery.getToken()));
                    break;
                default:
                    result = ServiceResult.failure(StatusCode.NOT_FOUND, "not implemented");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        } catch (RuntimeException unexpected) {
            // 兜底：仓储 IllegalStateException、null id 触发的 NPE 等一律收敛为 SERVER_ERROR，
            // 避免异常穿透 dispatch 被线程池 submit 的 FutureTask 静默吞掉、客户端只看到笼统网络错误
            return Message.response(request, StatusCode.SERVER_ERROR, "store request failed unexpectedly");
        }
    }

    private ServiceResult<Void> requirePermission(String token, String permission) {
        ServiceResult<Boolean> auth = users.authorize(token, permission);
        return auth.getStatus() == StatusCode.OK ? ServiceResult.ok(null)
                : ServiceResult.failure(auth.getStatus(), auth.getMessage());
    }

    private String requireUserId(String token) {
        ServiceResult<Session> session = users.currentSession(token);
        if (session.getStatus() != StatusCode.OK)
            throw new IllegalArgumentException("invalid session");
        return session.getData().getUser().getUserId();
    }

    // 记录一条商店敏感操作审计（best-effort）：auditLog 未注入或 actor/targetId 为空则跳过；
    // 审计本身失败绝不影响已完成的业务响应
    private void recordAudit(String actorUserId, String action, String targetType, String targetId) {
        if (auditLog == null || actorUserId == null || actorUserId.trim().isEmpty()
                || targetId == null || targetId.trim().isEmpty()) {
            return;
        }
        try {
            auditLog.record(new AuditEvent(actorUserId, action, targetType, targetId, Instant.now()));
        } catch (RuntimeException auditFailure) {
            // 留痕失败不阻断业务，仅打印告警供运维排查
            System.err.println("store audit failed: " + auditFailure.getMessage());
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

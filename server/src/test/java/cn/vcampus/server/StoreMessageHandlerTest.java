package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.CartItem;
import cn.vcampus.store.CartLine;
import cn.vcampus.store.CartQueryCommand;
import cn.vcampus.store.CartUpdateCommand;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StoreRestockCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StoreProductAddCommand;
import cn.vcampus.store.StoreProductUpdateCommand;
import cn.vcampus.store.StoreProductDeactivateCommand;
import cn.vcampus.store.StoreProductReactivateCommand;
import cn.vcampus.store.CartAddCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
import cn.vcampus.store.StoreAccountQueryCommand;
import cn.vcampus.store.StoreAccountRechargeCommand;
import cn.vcampus.store.StoreAccountAdjustCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.AuditEvent;
import cn.vcampus.user.AuditLogRepository;
import cn.vcampus.user.InMemoryAuditLogRepository;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreMessageHandlerTest {
    private CapturingStoreService store;
    private InMemoryUserManagementService users;
    private StoreMessageHandler handler;
    private Session studentSession;
    private Session managerSession;
    private Session librarianSession;
    private Session adminSession;

    @BeforeEach
    void setUp() {
        store = new CapturingStoreService();
        users = new InMemoryUserManagementService();
        handler = new StoreMessageHandler(store, users);
        UserCredentials student = new UserCredentials(
                "student001", "password", "测试学生", Role.STUDENT.name());
        users.register(student);
        studentSession = users.login(student).getData();
        UserCredentials manager = new UserCredentials(
                "manager001", "password", "商店管理员", Role.STORE_MANAGER.name());
        users.register(manager);
        managerSession = users.login(manager).getData();
        // 图书馆员无商店权限，用作无权限对照组
        UserCredentials librarian = new UserCredentials(
                "librarian001", "password", "图书馆员", Role.LIBRARIAN.name());
        users.register(librarian);
        librarianSession = users.login(librarian).getData();
        // 系统管理员，用于校正余额的角色授权对照组
        UserCredentials admin = new UserCredentials(
                "admin001", "password", "系统管理员", Role.ADMIN.name());
        users.register(admin);
        adminSession = users.login(admin).getData();
    }

    @Test
    void productQueryRequiresTokenOnlyCommand() {
        Message response = handler.handle(Message.request(
                "store-query", MessageType.STORE_QUERY,
                new StoreQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.listCalled);
    }

    @Test
    void productQueryRejectsMissingTokenPayload() {
        Message response = handler.handle(Message.request(
                "store-query-invalid", MessageType.STORE_QUERY, null));

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, store.listCallCount);
    }

    @Test
    void purchaseUsesUserIdResolvedFromSession() {
        Message response = handler.handle(Message.request(
                "store-purchase", MessageType.STORE_PURCHASE,
                new StorePurchaseCommand(studentSession.getToken(), "P001", 2)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals("student001", store.lastPurchaseUserId);
        assertEquals("P001", store.lastPurchaseProductId);
        assertEquals(2, store.lastPurchaseQuantity);
    }

    @Test
    void orderQueryUsesUserIdResolvedFromSession() {
        Message response = handler.handle(Message.request(
                "store-orders", MessageType.STORE_ORDER_QUERY,
                new StoreOrderQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals("student001", store.lastOrderQueryUserId);
    }

    @Test
    void invalidTokenCannotAccessStore() {
        Message response = handler.handle(Message.request(
                "store-invalid-token", MessageType.STORE_QUERY,
                new StoreQueryCommand("invalid-token")));

        assertEquals(StatusCode.UNAUTHORIZED, response.getStatusCode());
        assertEquals(0, store.listCallCount);
    }

    @Test
    void productQueryPassesOptionalCategoryToService() {
        Message response = handler.handle(Message.request(
                "store-category", MessageType.STORE_QUERY,
                new StoreQueryCommand(studentSession.getToken(), "文具")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals("文具", store.lastCategory);
    }

    @Test
    void managerCanReachRestockCommand() {
        Message response = handler.handle(Message.request(
                "store-restock", MessageType.STORE_RESTOCK,
                new StoreRestockCommand(managerSession.getToken(), "P001", 10)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.restockCalled);
        assertEquals("P001", store.lastRestockProductId);
        assertEquals(10, store.lastRestockAmount);
    }

    @Test
    void testStoreRestockUnauthorized() {
        Message response = handler.handle(Message.request(
                "store-restock-forbidden", MessageType.STORE_RESTOCK,
                new StoreRestockCommand(studentSession.getToken(), "P001", 10)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.restockCalled);
    }

    @Test
    void testStoreProductAddAuthorized() {
        Message response = handler.handle(Message.request(
                "store-product-add", MessageType.STORE_PRODUCT_ADD,
                new StoreProductAddCommand(managerSession.getToken(), "笔记本", 9.9, 5, "测试商品", "文具")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.addProductCalled);
        assertEquals("笔记本", store.lastAddProductName);
        assertEquals(9.9, store.lastAddProductPrice, 0.001);
        assertEquals(5, store.lastAddProductStock);
    }

    @Test
    void testStoreProductAddUnauthorized() {
        Message response = handler.handle(Message.request(
                "store-product-add-forbidden", MessageType.STORE_PRODUCT_ADD,
                new StoreProductAddCommand(studentSession.getToken(), "笔记本", 9.9, 5, "测试商品", "文具")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.addProductCalled);
    }

    @Test
    void testStoreProductUpdateAuthorized() {
        Message response = handler.handle(Message.request(
                "store-product-update", MessageType.STORE_PRODUCT_UPDATE,
                new StoreProductUpdateCommand(managerSession.getToken(), "P001", "签字笔", 2.5, "升级描述", "文具", 3)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.updateProductCalled);
        assertEquals("P001", store.lastUpdateProductId);
        assertEquals(2.5, store.lastUpdateProductPrice, 0.001);
        assertEquals(3, store.lastUpdateProductVersion);// A2：版本快照原样透传到服务层
    }

    @Test
    void testStoreProductDeactivateAuthorized() {
        Message response = handler.handle(Message.request(
                "store-product-deactivate", MessageType.STORE_PRODUCT_DEACTIVATE,
                new StoreProductDeactivateCommand(managerSession.getToken(), "P001")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.deactivateCalled);
        assertEquals("P001", store.lastDeactivateProductId);
    }

    @Test
    void testStoreProductReactivateAuthorized() {
        Message response = handler.handle(Message.request(
                "store-product-reactivate", MessageType.STORE_PRODUCT_REACTIVATE,
                new StoreProductReactivateCommand(managerSession.getToken(), "P001")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.reactivateCalled);
        assertEquals("P001", store.lastReactivateProductId);
    }

    @Test
    void testCartAddAuthorized() {
        Message response = handler.handle(Message.request(
                "store-cart-add", MessageType.STORE_CART_ADD,
                new CartAddCommand(studentSession.getToken(), "P001", 3)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.cartAddCalled);
        assertEquals("student001", store.lastCartAddUserId);
        assertEquals("P001", store.lastCartAddProductId);
        assertEquals(3, store.lastCartAddQuantity);
    }

    @Test
    void testCartAddUnauthorized() {
        Message response = handler.handle(Message.request(
                "store-cart-add-forbidden", MessageType.STORE_CART_ADD,
                new CartAddCommand(librarianSession.getToken(), "P001", 3)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.cartAddCalled);
    }

    @Test
    void testCartCheckoutAuthorized() {
        Message response = handler.handle(Message.request(
                "store-cart-checkout", MessageType.STORE_CART_CHECKOUT,
                new CartCheckoutCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.checkoutCalled);
        assertEquals("student001", store.lastCheckoutUserId);
    }

    @Test
    void testStoreOrderListAllAuthorized() {
        Message response = handler.handle(Message.request(
                "store-order-list-all", MessageType.STORE_ORDER_LIST_ALL,
                new StoreOrderListAllCommand(managerSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.findAllOrdersCalled);
    }

    @Test
    void testStoreHotProductsAuthorized() {
        Message response = handler.handle(Message.request(
                "store-hot-products", MessageType.STORE_HOT_PRODUCTS,
                new StoreHotProductsCommand(studentSession.getToken(), 5)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.hotProductsCalled);
        assertEquals(5, store.lastHotLimit);
    }

    @Test
    void testAccountQueryAuthorized() {
        store.balanceToReturn = 12345L;
        Message response = handler.handle(Message.request(
                "account-query", MessageType.STORE_ACCOUNT_QUERY,
                new StoreAccountQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.getBalanceCalled);
        assertEquals("student001", store.lastBalanceUserId);
    }

    @Test
    void testAccountQueryUnauthorized() {
        Message response = handler.handle(Message.request(
                "account-query-forbidden", MessageType.STORE_ACCOUNT_QUERY,
                new StoreAccountQueryCommand(librarianSession.getToken())));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.getBalanceCalled);
    }

    @Test
    void testAccountRechargeAuthorized() {
        Message response = handler.handle(Message.request(
                "account-recharge", MessageType.STORE_ACCOUNT_RECHARGE,
                new StoreAccountRechargeCommand(studentSession.getToken(), 5000L)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.rechargeCalled);
        assertEquals("student001", store.lastRechargeUserId);
        assertEquals(5000L, store.lastRechargeCents);
    }

    @Test
    void testAccountRechargeUnauthorized() {
        Message response = handler.handle(Message.request(
                "account-recharge-forbidden", MessageType.STORE_ACCOUNT_RECHARGE,
                new StoreAccountRechargeCommand(librarianSession.getToken(), 5000L)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.rechargeCalled);
    }

    @Test
    void testAccountAdjustByStoreManagerAuthorized() {
        Message response = handler.handle(Message.request(
                "account-adjust-manager", MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(managerSession.getToken(), "student001", 8888L)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.adjustCalled);
        assertEquals("manager001", store.lastAdjustAdminId);
        assertEquals("student001", store.lastAdjustTargetUserId);
        assertEquals(8888L, store.lastAdjustNewBalanceCents);
    }

    @Test
    void testAccountAdjustByAdminAuthorized() {
        Message response = handler.handle(Message.request(
                "account-adjust-admin", MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(adminSession.getToken(), "student001", 6666L)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.adjustCalled);
        assertEquals("admin001", store.lastAdjustAdminId);
        assertEquals("student001", store.lastAdjustTargetUserId);
    }

    @Test
    void testAccountAdjustByStudentForbidden() {
        Message response = handler.handle(Message.request(
                "account-adjust-student", MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(studentSession.getToken(), "student001", 100L)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.adjustCalled);
    }

    @Test
    void testAccountAdjustTargetOtherUserByNonAdminRejected() {
        // 普通学生指定他人 targetUserId，被 STORE_MANAGE 权限门槛拦下（服务端为准，客户端隐藏按钮只是 UX）
        Message response = handler.handle(Message.request(
                "account-adjust-other", MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(studentSession.getToken(), "manager001", 1L)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.adjustCalled);
    }

    @Test
    void testCartUpdateUsesUserIdResolvedFromSession() {
        Message response = handler.handle(Message.request(
                "store-cart-update", MessageType.STORE_CART_UPDATE,
                new CartUpdateCommand(studentSession.getToken(), "C001", 5)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.cartUpdateCalled);
        assertEquals("student001", store.lastCartUpdateUserId);
        assertEquals("C001", store.lastCartUpdateItemId);
        assertEquals(5, store.lastCartUpdateQuantity);
    }

    @Test
    void testCartUpdateUnauthorized() {
        Message response = handler.handle(Message.request(
                "store-cart-update-forbidden", MessageType.STORE_CART_UPDATE,
                new CartUpdateCommand(librarianSession.getToken(), "C001", 5)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.cartUpdateCalled);
    }

    @Test
    void testCartDetailReturnsJoinedProductSnapshot() {
        Message response = handler.handle(Message.request(
                "store-cart-detail", MessageType.STORE_CART_DETAIL,
                new CartQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.cartDetailCalled);
        assertEquals("student001", store.lastCartDetailUserId);
        @SuppressWarnings("unchecked")
        List<CartLine> lines = (List<CartLine>) response.getPayload();
        assertEquals(1, lines.size());
        assertEquals("笔记本", lines.get(0).getProductName());
        assertEquals(1980L, lines.get(0).getSubtotalCents());
    }

    @Test
    void testCartDetailUnauthorized() {
        Message response = handler.handle(Message.request(
                "store-cart-detail-forbidden", MessageType.STORE_CART_DETAIL,
                new CartQueryCommand(librarianSession.getToken())));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.cartDetailCalled);
    }

    @Test
    void testAccountLedgerReturnsOwnTransactionsOnly() {
        Message response = handler.handle(Message.request(
                "account-ledger", MessageType.STORE_ACCOUNT_LEDGER,
                new StoreAccountQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.ledgerCalled);
        // 流水只能查自己：payload 只有 token，userId 一律从 token 解析，客户端无法指定他人
        assertEquals("student001", store.lastLedgerUserId);
        @SuppressWarnings("unchecked")
        List<WalletTransaction> ledger = (List<WalletTransaction>) response.getPayload();
        assertEquals(1, ledger.size());
        assertEquals(WalletTransactionType.RECHARGE, ledger.get(0).getType());
    }

    @Test
    void testAccountLedgerUnauthorized() {
        Message response = handler.handle(Message.request(
                "account-ledger-forbidden", MessageType.STORE_ACCOUNT_LEDGER,
                new StoreAccountQueryCommand(librarianSession.getToken())));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertFalse(store.ledgerCalled);
    }

    @Test
    void testCartUpdateRejectsUnexpectedPayloadType() {
        // payload 类型不符时抛 IllegalArgumentException，Handler 外层统一转 BAD_REQUEST，不进业务层
        Message response = handler.handle(Message.request(
                "store-cart-update-invalid", MessageType.STORE_CART_UPDATE,
                new Object()));

        assertEquals(StatusCode.BAD_REQUEST, response.getStatusCode());
        assertFalse(store.cartUpdateCalled);
    }

    // 新-2：管理员改商品成功后记一条审计（actor/action/targetType/targetId 均正确）
    @Test
    void testProductUpdateRecordsAudit() {
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        StoreMessageHandler audited = new StoreMessageHandler(store, users, auditLog);

        Message response = audited.handle(Message.request(
                "store-product-update-audit", MessageType.STORE_PRODUCT_UPDATE,
                new StoreProductUpdateCommand(managerSession.getToken(), "P001", "签字笔", 2.5, "升级描述", "文具")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<AuditEvent> events = auditLog.findAll();
        assertEquals(1, events.size());
        assertEquals("manager001", events.get(0).getActorUserId());
        assertEquals("STORE_PRODUCT_UPDATE", events.get(0).getAction());
        assertEquals("PRODUCT", events.get(0).getTargetType());
        assertEquals("P001", events.get(0).getTargetId());
    }

    // 新-2：补货成功后记审计，targetId 为商品编号
    @Test
    void testRestockRecordsAudit() {
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        StoreMessageHandler audited = new StoreMessageHandler(store, users, auditLog);

        Message response = audited.handle(Message.request(
                "store-restock-audit", MessageType.STORE_RESTOCK,
                new StoreRestockCommand(managerSession.getToken(), "P001", 10)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<AuditEvent> events = auditLog.findAll();
        assertEquals(1, events.size());
        assertEquals("STORE_RESTOCK", events.get(0).getAction());
        assertEquals("PRODUCT", events.get(0).getTargetType());
        assertEquals("P001", events.get(0).getTargetId());
    }

    // 新-2：新增商品成功后记审计，targetId 取自服务端生成的商品编号
    @Test
    void testProductAddRecordsAudit() {
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        StoreMessageHandler audited = new StoreMessageHandler(store, users, auditLog);

        Message response = audited.handle(Message.request(
                "store-product-add-audit", MessageType.STORE_PRODUCT_ADD,
                new StoreProductAddCommand(managerSession.getToken(), "笔记本", 9.9, 5, "测试商品", "文具")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<AuditEvent> events = auditLog.findAll();
        assertEquals(1, events.size());
        assertEquals("STORE_PRODUCT_ADD", events.get(0).getAction());
        assertEquals("P-CAPTURED", events.get(0).getTargetId());// CapturingStoreService.addProduct 返回的编号
    }

    // 新-2：管理员校正余额成功后记审计，targetType=ACCOUNT、targetId=被校正用户
    @Test
    void testAccountAdjustRecordsAudit() {
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        StoreMessageHandler audited = new StoreMessageHandler(store, users, auditLog);

        Message response = audited.handle(Message.request(
                "account-adjust-audit", MessageType.STORE_ACCOUNT_ADJUST,
                new StoreAccountAdjustCommand(managerSession.getToken(), "student001", 8888L)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        List<AuditEvent> events = auditLog.findAll();
        assertEquals(1, events.size());
        assertEquals("manager001", events.get(0).getActorUserId());
        assertEquals("STORE_ACCOUNT_ADJUST", events.get(0).getAction());
        assertEquals("ACCOUNT", events.get(0).getTargetType());
        assertEquals("student001", events.get(0).getTargetId());
    }

    // 新-2：操作失败（无权限）不记审计
    @Test
    void testUnauthorizedOperationRecordsNoAudit() {
        AuditLogRepository auditLog = new InMemoryAuditLogRepository();
        StoreMessageHandler audited = new StoreMessageHandler(store, users, auditLog);

        Message response = audited.handle(Message.request(
                "store-restock-forbidden-audit", MessageType.STORE_RESTOCK,
                new StoreRestockCommand(studentSession.getToken(), "P001", 10)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
        assertTrue(auditLog.findAll().isEmpty());// 未成功不留痕
    }

    // 新-2：未注入审计仓库（null）时业务照常成功，不因缺审计而报错
    @Test
    void testNullAuditLogStillSucceeds() {
        Message response = handler.handle(Message.request(
                "store-restock-no-audit", MessageType.STORE_RESTOCK,
                new StoreRestockCommand(managerSession.getToken(), "P001", 10)));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.restockCalled);
    }

    // P1-1①：仓储抛 IllegalStateException 时，handle() 兜底为 SERVER_ERROR 而非异常穿透
    @Test
    void testStorageFailureReturnsServerError() {
        store.listFailure = new IllegalStateException("database unavailable");

        Message response = handler.handle(Message.request(
                "store-query-failure", MessageType.STORE_QUERY,
                new StoreQueryCommand(studentSession.getToken())));

        assertEquals(StatusCode.SERVER_ERROR, response.getStatusCode());
    }

    // P1-1①：购买路径仓储抛 IllegalStateException 同样收敛为 SERVER_ERROR
    @Test
    void testPurchaseStorageFailureReturnsServerError() {
        store.purchaseFailure = new IllegalStateException("database unavailable");

        Message response = handler.handle(Message.request(
                "store-purchase-failure", MessageType.STORE_PURCHASE,
                new StorePurchaseCommand(studentSession.getToken(), "P001", 1)));

        assertEquals(StatusCode.SERVER_ERROR, response.getStatusCode());
    }

    private static final class CapturingStoreService implements StoreService {
        private boolean listCalled;
        private int listCallCount;
        private String lastPurchaseUserId;
        private String lastPurchaseProductId;
        private int lastPurchaseQuantity;
        private String lastOrderQueryUserId;
        private String lastCategory;
        private String lastRestockProductId;
        private int lastRestockAmount;
        private boolean restockCalled;
        private boolean addProductCalled;
        private String lastAddProductName;
        private double lastAddProductPrice;
        private int lastAddProductStock;
        private boolean updateProductCalled;
        private String lastUpdateProductId;
        private double lastUpdateProductPrice;
        private int lastUpdateProductVersion;
        private boolean deactivateCalled;
        private String lastDeactivateProductId;
        private boolean reactivateCalled;
        private String lastReactivateProductId;
        private boolean cartAddCalled;
        private String lastCartAddUserId;
        private String lastCartAddProductId;
        private int lastCartAddQuantity;
        private boolean cartRemoveCalled;
        private String lastCartRemoveUserId;
        private String lastCartRemoveItemId;
        private boolean cartQueryCalled;
        private String lastCartQueryUserId;
        private boolean cartUpdateCalled;
        private String lastCartUpdateUserId;
        private String lastCartUpdateItemId;
        private int lastCartUpdateQuantity;
        private boolean cartDetailCalled;
        private String lastCartDetailUserId;
        private boolean ledgerCalled;
        private String lastLedgerUserId;
        private boolean checkoutCalled;
        private String lastCheckoutUserId;
        private boolean findAllOrdersCalled;
        private boolean hotProductsCalled;
        private int lastHotLimit;
        private boolean getBalanceCalled;
        private String lastBalanceUserId;
        private long balanceToReturn;
        private boolean rechargeCalled;
        private String lastRechargeUserId;
        private long lastRechargeCents;
        private boolean adjustCalled;
        private String lastAdjustAdminId;
        private String lastAdjustTargetUserId;
        private long lastAdjustNewBalanceCents;
        // 故障注入：置位后对应方法抛异常，用于验证 Handler 兜底把 RuntimeException 收敛为 SERVER_ERROR
        private RuntimeException listFailure;
        private RuntimeException purchaseFailure;

        @Override
        public ServiceResult<List<Product>> listProducts() {
            if (listFailure != null)
                throw listFailure;
            listCalled = true;
            listCallCount++;
            return ServiceResult.ok(Collections.<Product>emptyList());
        }

        @Override
        public ServiceResult<List<Product>> listProducts(String category) {
            lastCategory = category;
            return ServiceResult.ok(Collections.<Product>emptyList());
        }

        @Override
        public ServiceResult<Void> purchase(String userId, String productId, int quantity) {
            if (purchaseFailure != null)
                throw purchaseFailure;
            lastPurchaseUserId = userId;
            lastPurchaseProductId = productId;
            lastPurchaseQuantity = quantity;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<List<Order>> findOrdersByUserId(String userId) {
            lastOrderQueryUserId = userId;
            return ServiceResult.ok(Collections.<Order>emptyList());
        }

        @Override
        public ServiceResult<Void> restock(String productId, int additionalStock) {
            restockCalled = true;
            lastRestockProductId = productId;
            lastRestockAmount = additionalStock;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<Product> addProduct(String name, double price, int stock, String description,
                String category) {
            addProductCalled = true;
            lastAddProductName = name;
            lastAddProductPrice = price;
            lastAddProductStock = stock;
            return ServiceResult.ok(new Product("P-CAPTURED", "captured", 1, 1.0, "captured", "captured"));
        }

        @Override
        public ServiceResult<Product> updateProduct(String productId, String name, double price,
                String description, String category, int expectedVersion) {
            updateProductCalled = true;
            lastUpdateProductId = productId;
            lastUpdateProductPrice = price;
            lastUpdateProductVersion = expectedVersion;
            return ServiceResult.ok(new Product("P-CAPTURED", "captured", 1, 1.0, "captured", "captured"));
        }

        @Override
        public ServiceResult<Void> deactivateProduct(String productId) {
            deactivateCalled = true;
            lastDeactivateProductId = productId;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<Void> reactivateProduct(String productId) {
            reactivateCalled = true;
            lastReactivateProductId = productId;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
            cartAddCalled = true;
            lastCartAddUserId = userId;
            lastCartAddProductId = productId;
            lastCartAddQuantity = quantity;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
            cartRemoveCalled = true;
            lastCartRemoveUserId = userId;
            lastCartRemoveItemId = cartItemId;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<List<CartItem>> getCart(String userId) {
            cartQueryCalled = true;
            lastCartQueryUserId = userId;
            return ServiceResult.ok(Collections.<CartItem>emptyList());
        }

        @Override
        public ServiceResult<Void> updateCartQuantity(String userId, String cartItemId, int newQuantity) {
            cartUpdateCalled = true;
            lastCartUpdateUserId = userId;
            lastCartUpdateItemId = cartItemId;
            lastCartUpdateQuantity = newQuantity;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<List<CartLine>> getCartDetails(String userId) {
            cartDetailCalled = true;
            lastCartDetailUserId = userId;
            return ServiceResult.ok(Collections.singletonList(new CartLine(
                    "C001", "P001", "笔记本", 990L, 2, 1980L, true, LocalDateTime.now())));
        }

        @Override
        public ServiceResult<Void> checkout(String userId) {
            checkoutCalled = true;
            lastCheckoutUserId = userId;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<List<Order>> findAllOrders() {
            findAllOrdersCalled = true;
            return ServiceResult.ok(Collections.<Order>emptyList());
        }

        @Override
        public ServiceResult<List<Product>> listHotProducts(int limit) {
            hotProductsCalled = true;
            lastHotLimit = limit;
            return ServiceResult.ok(Collections.<Product>emptyList());
        }

        @Override
        public long getBalance(String userId) {
            getBalanceCalled = true;
            lastBalanceUserId = userId;
            return balanceToReturn;
        }

        @Override
        public ServiceResult<Void> recharge(String userId, long cents) {
            rechargeCalled = true;
            lastRechargeUserId = userId;
            lastRechargeCents = cents;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<Void> adjustBalance(String adminId, String userId, long newBalanceCents) {
            adjustCalled = true;
            lastAdjustAdminId = adminId;
            lastAdjustTargetUserId = userId;
            lastAdjustNewBalanceCents = newBalanceCents;
            return ServiceResult.ok(null);
        }

        @Override
        public ServiceResult<List<WalletTransaction>> listTransactions(String userId) {
            ledgerCalled = true;
            lastLedgerUserId = userId;
            return ServiceResult.ok(Collections.singletonList(new WalletTransaction(
                    "T001", userId, WalletTransactionType.RECHARGE, 5000L, 5000L, userId, null,
                    LocalDateTime.now())));
        }

    }
}

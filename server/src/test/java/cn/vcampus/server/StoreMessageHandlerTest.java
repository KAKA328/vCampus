package cn.vcampus.server;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.CartItem;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.store.StoreRestockCommand;
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StoreProductAddCommand;
import cn.vcampus.store.StoreProductUpdateCommand;
import cn.vcampus.store.StoreProductDeactivateCommand;
import cn.vcampus.store.CartAddCommand;
import cn.vcampus.store.CartCheckoutCommand;
import cn.vcampus.store.StoreOrderListAllCommand;
import cn.vcampus.store.StoreHotProductsCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
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
        // 图书馆员既无 STORE_PURCHASE 也无 STORE_READ，用作购物车/热销类操作的“无权限”对照组
        UserCredentials librarian = new UserCredentials(
                "librarian001", "password", "图书馆员", Role.LIBRARIAN.name());
        users.register(librarian);
        librarianSession = users.login(librarian).getData();
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
        assertEquals("manager001", store.lastRestockUserId);
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
                new StoreProductUpdateCommand(managerSession.getToken(), "P001", "签字笔", 2.5, "升级描述", "文具")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.updateProductCalled);
        assertEquals("P001", store.lastUpdateProductId);
        assertEquals(2.5, store.lastUpdateProductPrice, 0.001);
    }

    @Test
    void testStoreProductDeactivateAuthorized() {
        Message response = handler.handle(Message.request(
                "store-product-deactivate", MessageType.STORE_PRODUCT_DEACTIVATE,
                new StoreProductDeactivateCommand(managerSession.getToken(), "P001")));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertTrue(store.deactivateCalled);
        assertEquals("manager001", store.lastDeactivateUserId);
        assertEquals("P001", store.lastDeactivateProductId);
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

    private static final class CapturingStoreService implements StoreService {
        private boolean listCalled;
        private int listCallCount;
        private String lastPurchaseUserId;
        private String lastPurchaseProductId;
        private int lastPurchaseQuantity;
        private String lastOrderQueryUserId;
        private String lastCategory;
        private String lastRestockUserId;
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
        private boolean deactivateCalled;
        private String lastDeactivateUserId;
        private String lastDeactivateProductId;
        private boolean cartAddCalled;
        private String lastCartAddUserId;
        private String lastCartAddProductId;
        private int lastCartAddQuantity;
        private boolean cartRemoveCalled;
        private String lastCartRemoveUserId;
        private String lastCartRemoveItemId;
        private boolean cartQueryCalled;
        private String lastCartQueryUserId;
        private boolean checkoutCalled;
        private String lastCheckoutUserId;
        private boolean findAllOrdersCalled;
        private boolean hotProductsCalled;
        private int lastHotLimit;

        @Override
        public ServiceResult<List<Product>> listProducts() {
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
        public ServiceResult<Void> restock(String userId, String productId, int additionalStock) {
            restockCalled = true;
            lastRestockUserId = userId;
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
                String description, String category) {
            updateProductCalled = true;
            lastUpdateProductId = productId;
            lastUpdateProductPrice = price;
            return ServiceResult.ok(new Product("P-CAPTURED", "captured", 1, 1.0, "captured", "captured"));
        }

        @Override
        public ServiceResult<Void> deactivateProduct(String userId, String productId) {
            deactivateCalled = true;
            lastDeactivateUserId = userId;
            lastDeactivateProductId = productId;
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

    }
}

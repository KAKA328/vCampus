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
import cn.vcampus.store.StorePurchaseCommand;
import cn.vcampus.store.StoreQueryCommand;
import cn.vcampus.store.StoreService;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreMessageHandlerTest {
    private CapturingStoreService store;
    private InMemoryUserManagementService users;
    private StoreMessageHandler handler;
    private Session studentSession;

    @BeforeEach
    void setUp() {
        store = new CapturingStoreService();
        users = new InMemoryUserManagementService();
        handler = new StoreMessageHandler(store, users);
        UserCredentials student = new UserCredentials(
                "student001", "password", "测试学生", Role.STUDENT.name());
        users.register(student);
        studentSession = users.login(student).getData();
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

    private static final class CapturingStoreService implements StoreService {
        private boolean listCalled;
        private int listCallCount;
        private String lastPurchaseUserId;
        private String lastPurchaseProductId;
        private int lastPurchaseQuantity;
        private String lastOrderQueryUserId;

        @Override
        public ServiceResult<List<Product>> listProducts() {
            listCalled = true;
            listCallCount++;
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
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Product> addProduct(String name, double price, int stock, String description,
                String category) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Product> updateProduct(String productId, String name, double price,
                String description, String category) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Void> deactivateProduct(String userId, String productId) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<List<CartItem>> getCart(String userId) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<Void> checkout(String userId) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<List<Order>> findAllOrders() {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<List<Product>> listHotProducts(int limit) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }

        @Override
        public ServiceResult<List<Product>> listProducts(String category) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
        }
    }
}

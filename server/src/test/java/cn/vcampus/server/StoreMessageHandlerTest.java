package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.InMemoryStoreService;
import cn.vcampus.store.StoreCommand;
import cn.vcampus.store.StoreOrderQueryCommand;
import cn.vcampus.user.InMemoryUserManagementService;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import org.junit.jupiter.api.Test;

class StoreMessageHandlerTest {
    @Test
    void studentCannotPurchaseForAnotherBuyer() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session student = login(users, "20230001", Role.STUDENT);
        InMemoryStoreService store = new InMemoryStoreService();
        StoreMessageHandler handler = new StoreMessageHandler(store, users);

        Message response = handler.handle(Message.request("purchase", MessageType.STORE_PURCHASE,
                new StoreCommand(student.getToken(), "20230002", "P001", 1)));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void storeManagerCanQueryAllOrdersButCannotUseCoursePermission() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session storeManager = login(users, "store001", Role.STORE_MANAGER);
        InMemoryStoreService store = new InMemoryStoreService();
        StoreMessageHandler handler = new StoreMessageHandler(store, users);

        Message response = handler.handle(Message.request("orders", MessageType.STORE_ORDER_QUERY,
                StoreOrderQueryCommand.allOrders(storeManager.getToken())));

        assertEquals(StatusCode.OK, response.getStatusCode());
        assertEquals(StatusCode.FORBIDDEN,
                users.authorize(storeManager.getToken(), "COURSE_SELECT").getStatus());
    }

    @Test
    void academicAdminCannotEnterStore() {
        InMemoryUserManagementService users = new InMemoryUserManagementService();
        Session academic = login(users, "academic001", Role.ACADEMIC_ADMIN);
        StoreMessageHandler handler = new StoreMessageHandler(new InMemoryStoreService(), users);

        Message response = handler.handle(Message.request("orders", MessageType.STORE_ORDER_QUERY,
                StoreOrderQueryCommand.ownOrders(academic.getToken(), "academic001")));

        assertEquals(StatusCode.FORBIDDEN, response.getStatusCode());
    }

    private static Session login(InMemoryUserManagementService users, String userId, Role role) {
        UserCredentials credentials = new UserCredentials(userId, "password", userId, role.name());
        users.register(credentials);
        return users.login(credentials).getData();
    }
}

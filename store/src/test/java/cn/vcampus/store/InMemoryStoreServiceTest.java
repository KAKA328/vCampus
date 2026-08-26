package cn.vcampus.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryStoreServiceTest {
    @Test
    void purchaseCreatesOrderAndReducesStock() {
        InMemoryStoreService service = new InMemoryStoreService();

        ServiceResult<StoreOrder> purchase = service.purchase("20230001", "P001", 2);
        ServiceResult<List<StoreOrder>> orders = service.ordersFor("20230001");

        assertEquals(StatusCode.OK, purchase.getStatus());
        assertEquals("20230001", purchase.getData().getBuyerId());
        assertEquals(1, orders.getData().size());
        assertEquals(18, service.listProducts().getData().get(0).getStock());
    }

    @Test
    void rejectsPurchaseWhenStockIsInsufficient() {
        InMemoryStoreService service = new InMemoryStoreService();

        assertEquals(StatusCode.CONFLICT, service.purchase("20230001", "P001", 999).getStatus());
    }
}

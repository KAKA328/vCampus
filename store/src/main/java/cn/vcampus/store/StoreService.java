package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Store inventory and purchase contract. */
public interface StoreService {
    ServiceResult<List<Product>> listProducts();
    ServiceResult<StoreOrder> purchase(String buyerId, String productId, int quantity);
    ServiceResult<List<StoreOrder>> ordersFor(String buyerId);
    ServiceResult<List<StoreOrder>> allOrders();
}

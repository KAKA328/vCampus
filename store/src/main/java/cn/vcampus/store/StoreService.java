package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Store inventory and purchase contract. */
public interface StoreService {
    ServiceResult<List<Product>> listProducts();

    ServiceResult<Void> purchase(String studentId, String productId, int quantity);

    ServiceResult<List<Order>> findOrdersByStudentId(String studentId);
}

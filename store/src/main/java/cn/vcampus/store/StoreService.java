package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Store inventory and purchase contract. */
public interface StoreService {
    // 列出商品
    ServiceResult<List<Product>> listProducts();

    // 购买商品
    ServiceResult<Void> purchase(String userId, String productId, int quantity);

    // 查找用户订单
    ServiceResult<List<Order>> findOrdersByUserId(String userId);

    // 管理员商品管理
    ServiceResult<Void> restock(String userId, String productId, int additionalStock);

    ServiceResult<Product> addProduct(String name, double price, int stock, String description, String category);

    ServiceResult<Product> updateProduct(String productId, String name, double price, String description,
            String category);

    ServiceResult<Void> deactivateProduct(String userId, String productId);

    // 购物车
    ServiceResult<Void> addToCart(String userId, String productId, int quantity);

    ServiceResult<Void> removeFromCart(String userId, String cartItemId);

    ServiceResult<List<CartItem>> getCart(String userId);

    ServiceResult<Void> checkout(String userId);

    // 增值功能
    ServiceResult<List<Order>> findAllOrders();

    ServiceResult<List<Product>> listHotProducts(int limit);

    ServiceResult<List<Product>> listProducts(String category);

    // 账户：查询余额（分），无账户返回 0
    long getBalance(String userId);

    // 账户：本人充值（分），仅增加，cents 必须为正
    ServiceResult<Void> recharge(String userId, long cents);

    // 账户：管理员校正余额（分），目标余额非负，绝对设置
    ServiceResult<Void> adjustBalance(String adminId, String userId, long newBalanceCents);
}

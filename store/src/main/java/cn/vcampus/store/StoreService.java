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

    // 管理员商品管理：操作者身份由通信层的 STORE_MANAGE 权限门槛保证，服务层不再接收无用参数
    ServiceResult<Void> restock(String productId, int additionalStock);

    ServiceResult<Product> addProduct(String name, double price, int stock, String description, String category);

    // expectedVersion：客户端加载商品时的版本快照（A2 乐观并发）；与存储版本不符返回 CONFLICT，前端重读重试
    ServiceResult<Product> updateProduct(String productId, String name, double price, String description,
            String category, int expectedVersion);

    ServiceResult<Void> deactivateProduct(String productId);

    // 重新上架：把已下架商品的 active 翻回 true，只改上架位、不碰库存/价格等其他字段
    ServiceResult<Void> reactivateProduct(String productId);

    // 购物车
    ServiceResult<Void> addToCart(String userId, String productId, int quantity);

    ServiceResult<Void> removeFromCart(String userId, String cartItemId);

    // 购物车：修改条目数量，条目必须归属 userId 本人，否则按不存在处理
    ServiceResult<Void> updateCartQuantity(String userId, String cartItemId, int newQuantity);

    ServiceResult<List<CartItem>> getCart(String userId);

    // 购物车：读取时与商品联表，返回带商品名/单价/小计的明细行
    ServiceResult<List<CartLine>> getCartDetails(String userId);

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

    // 账户：本人流水（分），按记账时间升序，无流水返回空列表
    ServiceResult<List<WalletTransaction>> listTransactions(String userId);
}

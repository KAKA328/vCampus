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

    // 购物车批量删除：一次删多条本人条目；列表为空返回 BAD_REQUEST，一条都不属于本人返回 NOT_FOUND
    ServiceResult<Void> removeFromCart(String userId, List<String> cartItemIds);

    // 购物车结算选中：仅结算选中子集，语义与整单 checkout 一致（逐项原子扣库存→扣款→建单），
    // 成功后只删除选中条目；列表为空返回 BAD_REQUEST，任一 id 不属于本人或不存在返回 NOT_FOUND
    ServiceResult<Void> checkoutItems(String userId, List<String> cartItemIds);

    // 增值功能
    ServiceResult<List<Order>> findAllOrders();

    ServiceResult<List<Product>> listHotProducts(int limit);

    ServiceResult<List<Product>> listProducts(String category);

    // 含下架商品查询：includeInactive=true 时把已下架商品一并返回，供「已下架视图/重新上架」与买家浏览下架陈列使用。
    // 通信层只要求 STORE_READ（不额外要求 STORE_MANAGE），买家也可带此位浏览下架陈列；但购买/加购仍由服务层拒绝下架品
    ServiceResult<List<Product>> listProducts(String category, boolean includeInactive);

    // 多字段拼接查询：keyword 忽略大小写匹配名称或说明（可空=不限）、category 精确匹配（可空=全部）、
    // minPrice/maxPrice 闭区间（可空=该侧不限），includeInactive 语义同 listProducts；各条件取交集
    ServiceResult<List<Product>> searchProducts(String keyword, String category, Double minPrice, Double maxPrice,
            boolean includeInactive);

    // 账户：查询余额（分），无账户返回 0
    long getBalance(String userId);

    // 账户：本人充值（分），仅增加，cents 必须为正
    ServiceResult<Void> recharge(String userId, long cents);

    // 账户：管理员校正余额（分），目标余额非负，绝对设置
    ServiceResult<Void> adjustBalance(String adminId, String userId, long newBalanceCents);

    // 账户：本人流水（分），按记账时间升序，无流水返回空列表
    ServiceResult<List<WalletTransaction>> listTransactions(String userId);
}

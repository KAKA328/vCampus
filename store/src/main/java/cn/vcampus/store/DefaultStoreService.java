package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// 默认商店业务，通过提供商品仓库和订单仓库以实现业务逻辑
public final class DefaultStoreService implements StoreService {
    private final ProductRepository products;// 商品仓库
    private final OrderRepository orders;// 订单仓库

    // 依赖注入
    public DefaultStoreService(ProductRepository products, OrderRepository orders) {
        this.products = products;
        this.orders = orders;
    }

    // 列出所有商品，使用serviceresult类的ok方法打包返回
    @Override
    public final ServiceResult<List<Product>> listProducts() {
        return ServiceResult.ok(products.findAll());
    }

    // 购买方法
    @Override
    public final ServiceResult<Void> purchase(String userId, String productId, int quantity) {
        Product toBuy = products.findById(productId);
        // 没有目标商品
        if (toBuy == null)
            return ServiceResult.failure(StatusCode.NOT_FOUND, "Product not found");
        // 数量不合法
        else if (quantity <= 0)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "Quantity must be positive");
        // 商品数量不够
        else if (toBuy.getStock() < quantity)
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "No enough stock");
        // 商品存在且数量充足
        else {
            // 计算总价
            double totalPrice = toBuy.getPrice() * quantity;
            // 刷新库存
            products.updateStock(productId, toBuy.getStock() - quantity);
            // 创建订单,使用随机订单编号
            Order newOrder = new Order(UUID.randomUUID().toString(), userId, productId, quantity, totalPrice,
                    LocalDateTime.now(), toBuy.getName(), toBuy.getPrice());
            orders.create(newOrder);
            // 返回成功
            return ServiceResult.ok(null);
        }
    }

    // 根据用户ID查询订单
    @Override
    public final ServiceResult<List<Order>> findOrdersByUserId(String userId) {
        return ServiceResult.ok(orders.findByUserId(userId));
    }

    // 补货
    @Override
    public final ServiceResult<Void> restock(String userId, String productId, int additionalStock) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 新增商品
    @Override
    public final ServiceResult<Product> addProduct(String name, double price, int stock, String description,
            String category) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 更新商品非库存字段
    @Override
    public final ServiceResult<Product> updateProduct(String productId, String name, double price,
            String description, String category) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 下架商品
    @Override
    public final ServiceResult<Void> deactivateProduct(String userId, String productId) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 加入购物车
    @Override
    public final ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 删除购物车条目
    @Override
    public final ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 查询购物车
    @Override
    public final ServiceResult<List<CartItem>> getCart(String userId) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 购物车结账
    @Override
    public final ServiceResult<Void> checkout(String userId) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 查询所有订单
    @Override
    public final ServiceResult<List<Order>> findAllOrders() {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 热销商品排行
    @Override
    public final ServiceResult<List<Product>> listHotProducts(int limit) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }

    // 按分类列出商品
    @Override
    public final ServiceResult<List<Product>> listProducts(String category) {
        return ServiceResult.failure(StatusCode.BAD_REQUEST, "not implemented yet");
    }
}

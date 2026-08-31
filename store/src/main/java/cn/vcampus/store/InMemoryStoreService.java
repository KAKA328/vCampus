package cn.vcampus.store;

import java.util.List;
import cn.vcampus.common.ServiceResult;

// 内存商店服务，创建仓库，调用defaultstoreservice(默认仓库服务)
// 使用委托模式，由defaultstoreservice实现业务
public final class InMemoryStoreService implements StoreService {
    private final StoreService delegate;// 委托对象

    // 无参构造函数
    public InMemoryStoreService() {
        this(new InMemoryProductRepository(), new InMemoryOrderRepository());// 生成仓库
    }

    // 包内可见构造函数，传入自定义的Repository
    InMemoryStoreService(ProductRepository products, OrderRepository orders) {
        this.delegate = new DefaultStoreService(products, orders);
    }

    @Override
    public final ServiceResult<List<Product>> listProducts() {
        return delegate.listProducts();
    }

    @Override
    public final ServiceResult<Void> purchase(String userId, String productId, int quantity) {
        return delegate.purchase(userId, productId, quantity);
    }

    @Override
    public final ServiceResult<List<Order>> findOrdersByUserId(String userId) {
        return delegate.findOrdersByUserId(userId);
    }

    @Override
    public final ServiceResult<Void> restock(String userId, String productId, int additionalStock) {
        return delegate.restock(userId, productId, additionalStock);
    }

    @Override
    public final ServiceResult<Product> addProduct(String name, double price, int stock, String description,
            String category) {
        return delegate.addProduct(name, price, stock, description, category);
    }

    @Override
    public final ServiceResult<Product> updateProduct(String productId, String name, double price,
            String description, String category) {
        return delegate.updateProduct(productId, name, price, description, category);
    }

    @Override
    public final ServiceResult<Void> deactivateProduct(String userId, String productId) {
        return delegate.deactivateProduct(userId, productId);
    }

    @Override
    public final ServiceResult<Void> addToCart(String userId, String productId, int quantity) {
        return delegate.addToCart(userId, productId, quantity);
    }

    @Override
    public final ServiceResult<Void> removeFromCart(String userId, String cartItemId) {
        return delegate.removeFromCart(userId, cartItemId);
    }

    @Override
    public final ServiceResult<List<CartItem>> getCart(String userId) {
        return delegate.getCart(userId);
    }

    @Override
    public final ServiceResult<Void> checkout(String userId) {
        return delegate.checkout(userId);
    }

    @Override
    public final ServiceResult<List<Order>> findAllOrders() {
        return delegate.findAllOrders();
    }

    @Override
    public final ServiceResult<List<Product>> listHotProducts(int limit) {
        return delegate.listHotProducts(limit);
    }

    @Override
    public final ServiceResult<List<Product>> listProducts(String category) {
        return delegate.listProducts(category);
    }
}

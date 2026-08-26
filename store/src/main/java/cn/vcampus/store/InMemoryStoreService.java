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
    public final ServiceResult<Void> purchase(String studentId, String productId, int quantity) {
        return delegate.purchase(studentId, productId, quantity);
    }

    @Override
    public final ServiceResult<List<Order>> findOrdersByStudentId(String studentId) {
        return delegate.findOrdersByStudentId(studentId);
    }
}

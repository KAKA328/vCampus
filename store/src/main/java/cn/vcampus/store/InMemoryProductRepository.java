package cn.vcampus.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 实现内存型商品仓库接口
public final class InMemoryProductRepository implements ProductRepository {
    // 商品id向商品对象的映射
    private final Map<String, Product> products = new ConcurrentHashMap<String, Product>();

    @Override
    public final List<Product> findAll() {
        return new ArrayList<Product>(this.products.values());
    }

    @Override
    public final Product findById(String id) {
        return this.products.get(id);
    }

    @Override
    public final void save(Product product) {
        this.products.put(product.getProductId(), product);
    }

    @Override
    public final boolean updateStock(String productId, int newStock) {
        // 如果商品存在，改动存储数量，
        // 如果不存在，返回false
        if (this.findById(productId) != null) {
            Product oldPro = this.findById(productId);
            Product newPro = new Product(oldPro.getProductId(), oldPro.getName(), newStock, oldPro.getPrice(),
                    oldPro.getDescription(),
                    oldPro.getCategory());
            this.products.replace(productId, newPro);
            return true;
        }
        return false;
    }

    // 占位实现，Day 2 完成
    @Override
    public final boolean addStock(String productId, int amount) {
        return false;
    }

    // 占位实现，Day 2 完成
    @Override
    public final boolean updateProduct(Product product) {
        return false;
    }

    // 占位实现，Day 2 完成
    @Override
    public final boolean deleteById(String productId) {
        return false;
    }

}

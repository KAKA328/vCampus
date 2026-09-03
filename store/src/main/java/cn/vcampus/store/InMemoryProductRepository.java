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
    public synchronized final boolean updateStock(String productId, int newStock) {
        // 如果商品存在，改动存储数量，
        // 如果不存在，返回false
        if (this.findById(productId) != null) {
            Product oldPro = this.findById(productId);
            if (newStock < 0)
                return false;
            Product newPro = new Product(oldPro.getProductId(), oldPro.getName(), newStock, oldPro.getPrice(),
                    oldPro.getDescription(), oldPro.getCategory(), oldPro.isActive());
            this.products.replace(productId, newPro);
            return true;
        }
        return false;
    }

    @Override
    public synchronized final boolean addStock(String productId, int amount) {
        if (amount <= 0)
            return false;
        Product product = products.get(productId);
        return product != null && updateStock(productId, product.getStock() + amount);
    }

    @Override
    public synchronized final boolean updateProduct(Product product) {
        if (product == null || !products.containsKey(product.getProductId()))
            return false;
        products.put(product.getProductId(), product);
        return true;
    }

    @Override
    public synchronized final boolean deleteById(String productId) {
        return products.remove(productId) != null;
    }

    @Override
    public synchronized final boolean deductStock(String productId, int qty) {
        Product product = this.products.get(productId);
        if (product == null || product.getStock() < qty)
            return false;
        Product newPro = new Product(product.getProductId(), product.getName(), product.getStock() - qty,
                product.getPrice(), product.getDescription(), product.getCategory(), product.isActive());
        this.products.put(productId, newPro);
        return true;
    }
}

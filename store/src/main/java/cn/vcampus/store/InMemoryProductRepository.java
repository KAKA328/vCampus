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
        // 如果商品存在，改动存储数量，如果不存在，返回false
        if (this.findById(productId) != null) {
            Product oldPro = this.findById(productId);
            Product newPro = new Product(oldPro.getProductId(), oldPro.getName(), newStock, oldPro.getPrice(),
                    oldPro.getDescription(),
                    oldPro.getCategory(), oldPro.isActive());
            this.products.replace(productId, newPro);
            return true;
        }
        return false;
    }

    // 增加库存，商品存在则替换为新对象，不存在返回 false
    @Override
    public final boolean addStock(String productId, int amount) {
        Product oldPro = this.findById(productId);
        if (oldPro == null)
            return false;
        Product newPro = new Product(oldPro.getProductId(), oldPro.getName(), oldPro.getStock() + amount,
                oldPro.getPrice(), oldPro.getDescription(), oldPro.getCategory(), oldPro.isActive());
        this.products.replace(productId, newPro);
        return true;
    }

    // 更新商品非库存字段，商品存在则替换，不存在返回 false
    @Override
    public final boolean updateProduct(Product product) {
        Product oldPro = this.findById(product.getProductId());
        if (oldPro == null)
            return false;
        Product updatedPro = new Product(oldPro.getProductId(), product.getName(), oldPro.getStock(),
                product.getPrice(),
                product.getDescription(), product.getCategory(), product.isActive());
        this.products.replace(product.getProductId(), updatedPro);
        return true;
    }

    // 按Id删除商品
    @Override
    public final boolean deleteById(String productId) {
        return this.products.remove(productId) != null;
    }

}

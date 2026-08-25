package cn.vcampus.store;

import java.util.List;

public interface ProductRepository {
    List<Product> findAll();// 获取所有商品

    Product findById(String id);// 根据id获取商品

    void save(Product product);// 保存商品

    boolean updateStock(String productId, int newStock);// 根据id更新商品库存
}

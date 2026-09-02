package cn.vcampus.store;

import java.util.List;

public interface ProductRepository {
    List<Product> findAll();// 获取所有商品

    Product findById(String id);// 根据id获取商品

    void save(Product product);// 保存商品

    boolean updateStock(String productId, int newStock);// 根据id更新商品库存

    boolean addStock(String productId, int amount);// 根据id增加商品库存

    boolean updateProduct(Product product);// 根据id更新商品信息

    boolean deleteById(String productId);// 根据id删除商品
}

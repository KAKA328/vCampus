package cn.vcampus.store;

import java.util.List;
import java.util.Map;

public interface OrderRepository {
    boolean create(Order order);// 创建订单

    List<Order> findByUserId(String userId);// 根据用户编号查询订单

    List<Order> findAll();// 查询所有订单

    List<Map.Entry<String, Integer>> findSalesVolume(); // 查询销量， 返回[商品编号， 销量]
}

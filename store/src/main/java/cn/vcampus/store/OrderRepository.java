package cn.vcampus.store;

import java.util.List;

public interface OrderRepository {
    boolean create(Order order);// 创建订单

    List<Order> findByUserId(String userId);// 根据用户编号查询订单
}

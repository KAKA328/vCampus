package cn.vcampus.store;

import java.util.List;

public interface OrderRepository {
    boolean create(Order order);// 创建订单

    List<Order> findByStudentId(String studentId);// 根据学生编号查询订单
}

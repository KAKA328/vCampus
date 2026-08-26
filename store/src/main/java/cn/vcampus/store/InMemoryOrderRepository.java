package cn.vcampus.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new ConcurrentHashMap<String, Order>();// 根据订单ID存储订单

    private Map<String, List<Order>> studentIdMap = new ConcurrentHashMap<String, List<Order>>();// 根据学生ID存储订单

    @Override
    public final boolean create(Order order) {
        // 如果商品已经存在，返回false
        if (orders.containsKey(order.getOrderId()))
            return false;
        else {
            String studentId = order.getStudentId();// 当前订单学生Id
            orders.put(order.getOrderId(), order);// 存储到订单字典
            if (studentIdMap.containsKey(studentId)) {
                studentIdMap.get(studentId).add(order);// 添加到学生订单列表里面
            } else {
                studentIdMap.put(studentId, new ArrayList<Order>());
                studentIdMap.get(studentId).add(order);
            }
            return true;
        }
    }

    // 根据学生ID查找订单
    @Override
    public final List<Order> findByStudentId(String studentId) {
        List<Order> result = studentIdMap.get(studentId);
        return result != null ? result : new ArrayList<Order>();// 如果学生没有订单，返回空列表
    }
}

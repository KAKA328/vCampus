package cn.vcampus.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new ConcurrentHashMap<String, Order>();// 根据订单ID存储订单

    private Map<String, List<Order>> userIdMap = new ConcurrentHashMap<String, List<Order>>();// 根据用户ID存储订单

    @Override
    public final boolean create(Order order) {
        // 如果商品已经存在，返回false
        if (orders.containsKey(order.getOrderId()))
            return false;
        else {
            String userId = order.getUserId();// 当前订单用户Id
            orders.put(order.getOrderId(), order);// 存储到订单字典
            if (userIdMap.containsKey(userId)) {
                userIdMap.get(userId).add(order);// 添加到用户订单列表里面
            } else {
                userIdMap.put(userId, new ArrayList<Order>());
                userIdMap.get(userId).add(order);
            }
            return true;
        }
    }

    // 根据用户ID查找订单
    @Override
    public final List<Order> findByUserId(String userId) {
        List<Order> result = userIdMap.get(userId);
        return result != null ? result : new ArrayList<Order>();// 如果用户没有订单，返回空列表
    }
}

package cn.vcampus.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> orders = new ConcurrentHashMap<String, Order>();// 根据订单ID存储订单

    private final Map<String, List<Order>> userIdMap = new ConcurrentHashMap<String, List<Order>>();// 根据用户ID存储订单

    @Override
    public synchronized final boolean create(Order order) {
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
    public synchronized final List<Order> findByUserId(String userId) {
        List<Order> result = userIdMap.get(userId);
        return result != null ? new ArrayList<Order>(result) : new ArrayList<Order>();// 如果用户没有订单，返回空列表
    }

    @Override
    public synchronized final boolean deleteById(String orderId) {
        Order removed = orders.remove(orderId);
        if (removed == null) return false;
        List<Order> userOrders = userIdMap.get(removed.getUserId());
        if (userOrders != null) {
            userOrders.removeIf(order -> order.getOrderId().equals(orderId));
            if (userOrders.isEmpty()) userIdMap.remove(removed.getUserId());
        }
        return true;
    }

    @Override
    public synchronized final List<Order> findAll() {
        return new ArrayList<Order>(orders.values());
    }

    @Override
    public synchronized final List<Object[]> findSalesVolume() {
        Map<String, Integer> volumeByProduct = new ConcurrentHashMap<String, Integer>();
        for (Order order : orders.values()) {
            Integer current = volumeByProduct.get(order.getProductId());
            volumeByProduct.put(order.getProductId(), (current == null ? 0 : current) + order.getQuantity());
        }
        List<Object[]> result = new ArrayList<Object[]>();
        for (Map.Entry<String, Integer> entry : volumeByProduct.entrySet()) {
            result.add(new Object[] { entry.getKey(), entry.getValue() });
        }
        java.util.Collections.sort(result, (left, right) -> {
            int byVolume = Integer.compare((Integer) right[1], (Integer) left[1]);
            return byVolume != 0 ? byVolume : String.valueOf(left[0]).compareTo(String.valueOf(right[0]));
        });
        return result;
    }
}

package cn.vcampus.store;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // 管理员查找所有订单
    @Override
    public final List<Order> findAll() {
        return new ArrayList<Order>(orders.values());
    }

    // 管理员查看热门商品
    @Override
    public final List<Map.Entry<String, Integer>> findSalesVolume() {
        Map<String, Integer> productSales = new HashMap<String, Integer>();
        for (Order order : orders.values()) {
            String currId = order.getProductId();
            if (productSales.containsKey(currId)) {
                productSales.replace(currId, productSales.get(currId) + order.getQuantity());
            } else {
                productSales.put(currId, order.getQuantity());
            }
        }
        List<Map.Entry<String, Integer>> sortedList = productSales.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> new AbstractMap.SimpleImmutableEntry<String, Integer>(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return sortedList;
    }
}

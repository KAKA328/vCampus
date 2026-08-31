package cn.vcampus.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

public final class InMemoryCartRepository implements CartRepository {
    private final Map<String, CartItem> items = new ConcurrentHashMap<String, CartItem>();// 所有购物车项
    private final Map<String, List<CartItem>> userIdMap = new ConcurrentHashMap<String, List<CartItem>>();// 用户购物车

    @Override
    public List<CartItem> findByUserId(String userId) {
        List<CartItem> cart = userIdMap.get(userId);
        if (cart == null)
            return new ArrayList<CartItem>(); // 用户不存在，返回空列表
        else
            return new ArrayList<CartItem>(cart);// 返回用户购物车副本
    }

    private List<CartItem> findByUserIdToUpdate(String userId) {
        List<CartItem> cart = userIdMap.get(userId);
        if (cart == null)
            return new ArrayList<CartItem>(); // 用户不存在，返回空列表
        else
            return cart;// 直接返回用户购物车原始视图
    }

    @Override
    public boolean addItem(CartItem item) {
        // 如果有当前用户的购物车
        if (userIdMap.containsKey(item.getUserId())) {
            List<CartItem> cart = userIdMap.get(item.getUserId());

            boolean alreadyContainsProduct = false;
            // 遍历购物车，若已存在当前商品了，仅仅更新数量
            for (int i = 0; i < cart.size(); i++) {
                CartItem cartItem = cart.get(i);
                if (cartItem.getProductId().equals(item.getProductId())) {
                    alreadyContainsProduct = true;
                    int newQuantity = cartItem.getQuantity() + item.getQuantity();
                    CartItem newItem = new CartItem(
                            cartItem.getCartItemId(),
                            cartItem.getUserId(),
                            cartItem.getProductId(),
                            newQuantity,
                            cartItem.getAddedAt());
                    cart.set(i, newItem);
                    items.replace(newItem.getCartItemId(), newItem);
                    break;
                }
            }
            // 如果购物车中没有该商品，直接添加新项
            if (!alreadyContainsProduct) {
                cart.add(item);
                items.put(item.getCartItemId(), item);
            }
            userIdMap.replace(item.getUserId(), cart);
            return true;
        } else {
            // 如果用户不存在，创建新列表并放入 Map
            List<CartItem> cart = new ArrayList<>();
            cart.add(item);
            userIdMap.put(item.getUserId(), cart);
            items.put(item.getCartItemId(), item);
            return true;
        }
    }

    @Override
    public boolean removeItem(String cartItemId) {
        if (items.containsKey(cartItemId)) {
            // 如果items里面包含该购物车项，删除该购物车项并且更新用户购物车
            String userId = items.get(cartItemId).getUserId();
            List<CartItem> cart = findByUserIdToUpdate(userId);
            cart.removeIf(item -> item.getCartItemId().equals(cartItemId));
            items.remove(cartItemId);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean updateQuantity(String cartItemId, int newQuantity) {
        if (!items.containsKey(cartItemId))
            return false;// 如果当前购物车项不存在，返回false
        else {
            CartItem currItem = items.get(cartItemId);
            CartItem newItem = new CartItem(cartItemId, currItem.getUserId(),
                    currItem.getProductId(), newQuantity, currItem.getAddedAt());
            // 更新items；
            items.replace(cartItemId, newItem);
            // 更新userIdMap
            List<CartItem> cart = findByUserIdToUpdate(currItem.getUserId());
            for (int i = 0; i < cart.size(); ++i) {
                CartItem item = cart.get(i);
                if (item.getCartItemId().equals(cartItemId)) {
                    cart.set(i, newItem);
                }
            }
            return true;
        }
    }

    @Override
    public void clearByUserId(String userId) {
        // 根据userIdMap找到对应用户的购物，删除items中对应的项
        if (!userIdMap.containsKey(userId))
            return;
        else {
            List<CartItem> cartOfCurrentUser = findByUserId(userId);
            for (CartItem item : cartOfCurrentUser) {
                items.remove(item.getCartItemId());
            }
            // 最后清空该用户的购物车
            userIdMap.remove(userId);
        }
    }
}

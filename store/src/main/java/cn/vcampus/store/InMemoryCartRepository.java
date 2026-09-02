package cn.vcampus.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe cart repository for local demos and service tests. */
public final class InMemoryCartRepository implements CartRepository {
    private final Map<String, CartItem> items = new ConcurrentHashMap<String, CartItem>();

    @Override
    public synchronized List<CartItem> findByUserId(String userId) {
        List<CartItem> result = new ArrayList<CartItem>();
        for (CartItem item : items.values()) {
            if (item.getUserId().equals(userId)) result.add(item);
        }
        return result;
    }

    @Override
    public synchronized boolean addItem(CartItem item) {
        if (item == null) return false;
        for (CartItem existing : items.values()) {
            if (existing.getUserId().equals(item.getUserId())
                    && existing.getProductId().equals(item.getProductId())) {
                return updateQuantity(existing.getCartItemId(), existing.getQuantity() + item.getQuantity());
            }
        }
        return items.putIfAbsent(item.getCartItemId(), item) == null;
    }

    @Override
    public synchronized boolean removeItem(String cartItemId) {
        return items.remove(cartItemId) != null;
    }

    @Override
    public synchronized boolean updateQuantity(String cartItemId, int newQuantity) {
        CartItem old = items.get(cartItemId);
        if (old == null || newQuantity <= 0) return false;
        items.put(cartItemId, new CartItem(old.getCartItemId(), old.getUserId(), old.getProductId(),
                newQuantity, old.getAddedAt()));
        return true;
    }

    @Override
    public synchronized void clearByUserId(String userId) {
        for (CartItem item : findByUserId(userId)) items.remove(item.getCartItemId());
    }
}

package cn.vcampus.store;

import java.util.List;

public interface CartRepository {

    List<CartItem> findByUserId(String userId); // 根据用户编号查询购物车项

    boolean addItem(CartItem item); // 添加购物车项或者增加已有项的商品数量

    boolean removeItem(String cartItemId); // 根据购物车项编号删除购物车项

    boolean updateQuantity(String cartItemId, int newQuantity); // 根据购物车项编号更新购物车项数量

    void clearByUserId(String userId); // 下单之后清空购物车
}

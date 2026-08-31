package cn.vcampus.store;

import java.io.Serializable;
import java.time.LocalDateTime;

public final class CartItem implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String cartItemId;// 购物车条目编号
    private final String userId;// 所属用户编号
    private final String productId;// 商品编号
    private final int quantity;// 数量
    private final LocalDateTime addedAt;// 加入时间

    public CartItem(String cartItemId, String userId, String productId, int quantity, LocalDateTime addedAt) {
        this.cartItemId = checkStr(cartItemId, "cartItemId");
        this.userId = checkStr(userId, "userId");
        this.productId = checkStr(productId, "productId");
        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be greater than zero");
        this.quantity = quantity;
        if (addedAt == null)
            throw new IllegalArgumentException("Added at cannot be null");
        this.addedAt = addedAt;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public String getUserId() {
        return userId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    private static String checkStr(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value.trim();
    }
}

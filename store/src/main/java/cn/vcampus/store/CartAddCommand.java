package cn.vcampus.store;

import java.io.Serializable;

public final class CartAddCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final String productId;// 商品ID
    private final int quantity;// 商品数量

    public CartAddCommand(String token, String productId, int quantity) {
        this.token = checkStr(token, "token");
        this.productId = checkStr(productId, "productId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        this.quantity = quantity;
    }

    public String getToken() {
        return token;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

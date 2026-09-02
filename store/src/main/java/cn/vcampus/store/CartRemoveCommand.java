package cn.vcampus.store;

import java.io.Serializable;

public final class CartRemoveCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final String cartItemId;// 购物车项ID

    public CartRemoveCommand(String token, String cartItemId) {
        this.token = checkStr(token, "token");
        this.cartItemId = checkStr(cartItemId, "cartItemId");
    }

    public String getToken() {
        return token;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

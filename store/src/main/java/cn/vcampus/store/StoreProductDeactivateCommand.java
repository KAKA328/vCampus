package cn.vcampus.store;

import java.io.Serializable;

public final class StoreProductDeactivateCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final String productId;// 商品ID

    public StoreProductDeactivateCommand(String token, String productId) {
        this.token = checkStr(token, "token");
        this.productId = checkStr(productId, "productId");
    }

    public String getToken() {
        return token;
    }

    public String getProductId() {
        return productId;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

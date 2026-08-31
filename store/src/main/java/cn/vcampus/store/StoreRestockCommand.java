package cn.vcampus.store;

import java.io.Serializable;

public final class StoreRestockCommand implements Serializable {
    private final static long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final String productId;// 商品编号
    private final int additionalStock;// 补货数量

    public StoreRestockCommand(String token, String productId, int additionalStock) {
        this.token = checkStr(token, "token");
        this.productId = checkStr(productId, "productId");
        if (additionalStock <= 0)
            throw new IllegalArgumentException("additionalStock must be greater than zero");
        this.additionalStock = additionalStock;
    }

    public String getToken() {
        return token;
    }

    public String getProductId() {
        return productId;
    }

    public int getAdditionalStock() {
        return additionalStock;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}
package cn.vcampus.store;

import java.io.Serializable;

public final class StoreQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;// 用户令牌
    private final String category;// 商品类别

    public StoreQueryCommand(String token, String category) {
        this.token = checkStr(token, "token");
        this.category = category; // 不能检查字符串合法性，用于委托构造
    }

    public StoreQueryCommand(String token) {
        this(token, null);
    }

    public String getToken() {
        return token;
    }

    public String getCategory() {
        return category;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

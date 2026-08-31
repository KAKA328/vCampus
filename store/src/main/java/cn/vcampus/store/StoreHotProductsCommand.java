package cn.vcampus.store;

import java.io.Serializable;

public final class StoreHotProductsCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final int limit;// 取前几名

    public StoreHotProductsCommand(String token, int limit) {
        this.token = checkStr(token, "token");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
        this.limit = limit;
    }

    public String getToken() {
        return token;
    }

    public int getLimit() {
        return limit;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

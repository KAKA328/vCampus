package cn.vcampus.store;

import java.io.Serializable;

public final class StoreQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;

    public StoreQueryCommand(String token) {
        this.token = checkStr(token, "token");
    }

    public String getToken() {
        return token;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

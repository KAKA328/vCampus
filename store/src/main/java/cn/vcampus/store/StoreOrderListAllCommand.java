package cn.vcampus.store;

import java.io.Serializable;

// 管理员查看所有订单命令
public final class StoreOrderListAllCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 管理员令牌

    public StoreOrderListAllCommand(String token) {
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

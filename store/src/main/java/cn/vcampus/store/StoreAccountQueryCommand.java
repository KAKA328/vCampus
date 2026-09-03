package cn.vcampus.store;

import java.io.Serializable;

// 查询账户余额命令：仅携带 token，userId 由服务端从 token 解析，客户端无法伪造他人身份
public final class StoreAccountQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌

    public StoreAccountQueryCommand(String token) {
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

package cn.vcampus.store;

import java.io.Serializable;

// 本人充值命令：amountCents 为充值金额（单位分），必须为正；userId 由服务端从 token 解析
public final class StoreAccountRechargeCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final long amountCents;// 充值金额，单位分

    public StoreAccountRechargeCommand(String token, long amountCents) {
        this.token = checkStr(token, "token");
        if (amountCents <= 0) {
            throw new IllegalArgumentException("amountCents must be greater than zero");
        }
        this.amountCents = amountCents;
    }

    public String getToken() {
        return token;
    }

    public long getAmountCents() {
        return amountCents;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

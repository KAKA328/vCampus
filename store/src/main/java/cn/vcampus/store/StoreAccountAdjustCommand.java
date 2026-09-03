package cn.vcampus.store;

import java.io.Serializable;

// 管理员校正余额命令：targetUserId 为目标账户，newBalanceCents 为新的绝对余额（单位分），必须非负
public final class StoreAccountAdjustCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 管理员令牌
    private final String targetUserId;// 被校正账户的用户ID
    private final long newBalanceCents;// 目标余额，单位分

    public StoreAccountAdjustCommand(String token, String targetUserId, long newBalanceCents) {
        this.token = checkStr(token, "token");
        this.targetUserId = checkStr(targetUserId, "targetUserId");
        if (newBalanceCents < 0) {
            throw new IllegalArgumentException("newBalanceCents cannot be negative");
        }
        this.newBalanceCents = newBalanceCents;
    }

    public String getToken() {
        return token;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public long getNewBalanceCents() {
        return newBalanceCents;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

package cn.vcampus.store;

import java.io.Serializable;

public final class StoreQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;// 用户令牌
    private final String category;// 商品类别，可空=全部类别
    // 是否一并返回已下架商品（仅管理端视图用）：服务端要求 STORE_MANAGE 双门槛，普通买家带此位会被拒
    private final boolean includeInactive;

    public StoreQueryCommand(String token, String category, boolean includeInactive) {
        this.token = checkStr(token, "token");
        this.category = category; // 不能检查字符串合法性，用于委托构造
        this.includeInactive = includeInactive;
    }

    public StoreQueryCommand(String token, String category) {
        this(token, category, false);
    }

    public StoreQueryCommand(String token) {
        this(token, null, false);
    }

    public String getToken() {
        return token;
    }

    public String getCategory() {
        return category;
    }

    public boolean isIncludeInactive() {
        return includeInactive;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

package cn.vcampus.store;

import java.io.Serializable;

public final class StoreQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;// 用户令牌
    private final String keyword;// 关键词，可空=不限；服务端忽略大小写匹配商品名称或说明
    private final String category;// 商品类别，可空=全部类别
    private final Double minPrice;// 最低价格（元），可空=不限下界
    private final Double maxPrice;// 最高价格（元），可空=不限上界
    // 是否一并返回已下架商品（仅管理端视图用）：服务端要求 STORE_MANAGE 双门槛，普通买家带此位会被拒
    private final boolean includeInactive;

    // 全参构造：多字段拼接查询（关键词 + 类别 + 价格区间 + 是否含下架）
    public StoreQueryCommand(String token, String keyword, String category, Double minPrice, Double maxPrice,
            boolean includeInactive) {
        this.token = checkStr(token, "token");
        this.keyword = keyword; // 可空，不能检查字符串合法性
        this.category = category; // 不能检查字符串合法性，用于委托构造
        this.minPrice = minPrice; // 可空=不限下界
        this.maxPrice = maxPrice; // 可空=不限上界
        this.includeInactive = includeInactive;
    }

    public StoreQueryCommand(String token, String category, boolean includeInactive) {
        this(token, null, category, null, null, includeInactive);
    }

    public StoreQueryCommand(String token, String category) {
        this(token, null, category, null, null, false);
    }

    public StoreQueryCommand(String token) {
        this(token, null, null, null, null, false);
    }

    public String getToken() {
        return token;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getCategory() {
        return category;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
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

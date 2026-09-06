package cn.vcampus.store;

import java.io.Serializable;

public final class StoreProductUpdateCommand implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;// 用户令牌
    private final String productId;// 商品ID
    private final String name;// 商品名称
    private final double price;// 商品价格
    private final String description;// 商品描述
    private final String category;// 商品类别
    private final int version;// 乐观并发版本快照（A2）：客户端加载商品时携带，服务端 WHERE version=? 校验

    // A2 主构造器：version 为客户端加载时的期望版本，负数拒绝
    public StoreProductUpdateCommand(String token, String productId, String name, double price, String description,
            String category, int version) {
        this.token = checkStr(token, "token");
        this.productId = checkStr(productId, "productId");
        this.name = checkStr(name, "name");
        if (price < 0)
            throw new IllegalArgumentException("price must not be less than zero");
        if (version < 0)
            throw new IllegalArgumentException("version must not be negative");
        this.price = price;
        this.description = optionalStr(description);
        this.category = checkStr(category, "category");
        this.version = version;
    }

    // 兼容构造器：不带 version 时期望版本默认 0（仅测试装配用），生产路径由 RemoteStoreService 传真实版本
    public StoreProductUpdateCommand(String token, String productId, String name, double price, String description,
            String category) {
        this(token, productId, name, price, description, category, 0);
    }

    public String getToken() {
        return token;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public int getVersion() {
        return version;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }

    // 说明为可选字段：允许空/null，统一规范为 trim 后的串，与服务端 Product 实体、DB 可空列一致
    private static String optionalStr(String value) {
        return value == null ? "" : value.trim();
    }
}

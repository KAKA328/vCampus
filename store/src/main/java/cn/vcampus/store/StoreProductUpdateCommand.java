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

    public StoreProductUpdateCommand(String token, String productId, String name, double price, String description,
            String category) {
        this.token = checkStr(token, "token");
        this.productId = checkStr(productId, "productId");
        this.name = checkStr(name, "name");
        if (price < 0)
            throw new IllegalArgumentException("price must not be less than zero");
        this.price = price;
        this.description = checkStr(description, "description");
        this.category = checkStr(category, "category");
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

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

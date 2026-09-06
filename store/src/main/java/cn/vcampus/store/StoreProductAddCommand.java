package cn.vcampus.store;

import java.io.Serializable;

public final class StoreProductAddCommand implements Serializable {
    // 没有productId，新增商品由系统生成商品Id
    private static final long serialVersionUID = 1L;
    private final String token;// 用户令牌
    private final String name;// 商品名称
    private final double price;// 商品价格
    private final int stock;// 商品库存
    private final String description;// 商品描述
    private final String category;// 商品类别

    public StoreProductAddCommand(String token, String name, double price, int stock, String description,
            String category) {
        this.token = checkStr(token, "token");
        this.name = checkStr(name, "name");
        if (price < 0)
            throw new IllegalArgumentException("price must not be less than zero");
        this.price = price;
        if (stock < 0)
            throw new IllegalArgumentException("stock must not be less than zero");
        this.stock = stock;
        this.description = optionalStr(description);
        this.category = checkStr(category, "category");
    }

    public String getToken() {
        return token;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
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

    // 说明为可选字段：允许空/null，统一规范为 trim 后的串，与服务端 Product 实体、DB 可空列一致
    private static String optionalStr(String value) {
        return value == null ? "" : value.trim();
    }
}

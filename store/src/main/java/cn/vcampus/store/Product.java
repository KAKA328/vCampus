package cn.vcampus.store;

import java.io.Serializable;

/** Product value object. */
public final class Product implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String productId;// 商品编号
    private final String name;// 商品名称
    private final int stock;// 库存数量
    private final double price;// 商品价格
    private final String description;// 商品描述
    private final String category;// 商品类别
    private final boolean active;// 是否上架

    public Product(String productId, String name, int stock, double price, String description, String category,
            boolean active) {
        this.productId = checkStr(productId, "productId");
        this.name = checkStr(name, "name");
        if (stock < 0)
            throw new IllegalArgumentException("stock cannot be negative");
        if (!Double.isFinite(price) || price <= 0)
            throw new IllegalArgumentException("price must be a finite positive number");
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.category = checkStr(category, "category");
        this.active = active;
    }

    public Product(String productId, String name, int stock, double price, String description, String category) {
        this(productId, name, stock, price, description, category, true);
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
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

    public boolean isActive() {
        return active;
    }

    private static String checkStr(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value.trim();
    }
}

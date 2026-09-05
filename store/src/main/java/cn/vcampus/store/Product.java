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

    // 商品字段业务上限（DSH A3）：长度与 DB tblProduct 的 VARCHAR 列宽对齐，price 设防溢出上界；
    // 超限在构造期抛 IllegalArgumentException，由服务层 addProduct/updateProduct 捕获归为
    // BAD_REQUEST，
    // 不再让超长/超大值穿透到 Access 层触发 IllegalStateException → 笼统 SERVER_ERROR
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CATEGORY_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final double MAX_PRICE = 1_000_000_000.0D;// 10^9 元

    public Product(String productId, String name, int stock, double price, String description, String category,
            boolean active) {
        this.productId = checkStr(productId, "productId");
        this.name = checkLen(checkStr(name, "name"), MAX_NAME_LENGTH, "name");
        if (stock < 0)
            throw new IllegalArgumentException("stock cannot be negative");
        if (!Double.isFinite(price) || price <= 0 || price > MAX_PRICE)
            throw new IllegalArgumentException("price must be a finite positive number not exceeding " + MAX_PRICE);
        this.price = price;
        this.stock = stock;
        this.description = checkOptionalLen(description, MAX_DESCRIPTION_LENGTH, "description");
        this.category = checkLen(checkStr(category, "category"), MAX_CATEGORY_LENGTH, "category");
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

    // 必填串长度守卫：value 已是 trim 后的非空串，超过 DB 列宽即拒绝（DSH A3）
    private static String checkLen(String value, int maxLength, String field) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length " + maxLength);
        }
        return value;
    }

    // 可选串（说明）长度守卫：null/空放行（A1 已令说明可选），非空才限长（DSH A3）
    private static String checkOptionalLen(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds maximum length " + maxLength);
        }
        return value;
    }
}

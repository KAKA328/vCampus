package cn.vcampus.store;

import java.io.Serializable;

/** Product value object. */
public final class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String productId;
    private final String name;
    private final int stock;

    public Product(String productId, String name, int stock) {
        this.productId = requireText(productId, "productId");
        this.name = requireText(name, "name");
        if (stock < 0) {
            throw new IllegalArgumentException("stock cannot be negative");
        }
        this.stock = stock;
    }

    public String getProductId() { return productId; }
    public String getName() { return name; }
    public int getStock() { return stock; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

package cn.vcampus.store;

import java.io.Serializable;

/** Product value object. */
public final class Product implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String productId; private final String name; private final int stock;
    public Product(String productId, String name, int stock) { this.productId=productId; this.name=name; this.stock=stock; }
    public String getProductId() { return productId; } public String getName() { return name; } public int getStock() { return stock; }
}

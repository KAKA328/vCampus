package cn.vcampus.store;

import java.io.Serializable;

/** Store purchase order record. */
public final class StoreOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final String buyerId;
    private final String productId;
    private final String productName;
    private final int quantity;

    public StoreOrder(String orderId, String buyerId, String productId, String productName, int quantity) {
        this.orderId = requireText(orderId, "orderId");
        this.buyerId = requireText(buyerId, "buyerId");
        this.productId = requireText(productId, "productId");
        this.productName = requireText(productName, "productName");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.quantity = quantity;
    }

    public String getOrderId() { return orderId; }
    public String getBuyerId() { return buyerId; }
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

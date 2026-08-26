package cn.vcampus.store;

import java.io.Serializable;

/** Command for authenticated store purchase. */
public final class StoreCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String buyerId;
    private final String productId;
    private final int quantity;

    public StoreCommand(String token, String buyerId, String productId, int quantity) {
        this.token = requireText(token, "token");
        this.buyerId = requireText(buyerId, "buyerId");
        this.productId = requireText(productId, "productId");
        this.quantity = quantity;
    }

    public String getToken() { return token; }
    public String getBuyerId() { return buyerId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

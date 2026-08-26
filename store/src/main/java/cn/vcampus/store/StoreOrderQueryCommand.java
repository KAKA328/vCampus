package cn.vcampus.store;

import java.io.Serializable;

/** Command for querying store purchase orders. */
public final class StoreOrderQueryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String buyerId;
    private final boolean allOrders;

    private StoreOrderQueryCommand(String token, String buyerId, boolean allOrders) {
        this.token = requireText(token, "token");
        this.buyerId = buyerId == null ? null : requireText(buyerId, "buyerId");
        this.allOrders = allOrders;
    }

    public static StoreOrderQueryCommand ownOrders(String token, String buyerId) {
        return new StoreOrderQueryCommand(token, buyerId, false);
    }

    public static StoreOrderQueryCommand allOrders(String token) {
        return new StoreOrderQueryCommand(token, null, true);
    }

    public String getToken() { return token; }
    public String getBuyerId() { return buyerId; }
    public boolean isAllOrders() { return allOrders; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

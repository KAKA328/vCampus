package cn.vcampus.store;

import java.io.Serializable;
import java.time.LocalDateTime;

// 订单类
// 提供序列化接口，使订单对象可以被序列化为字节流用于长时存储
public final class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String orderId; // 订单编号
    private final String userId; // 下单用户编号
    private final String productId; // 购买的商品编号
    private final int quantity; // 购买数量
    private final double totalPrice; // 订单总价
    private final LocalDateTime orderDate; // 订单日期
    private final String productName;// 购买时的商品名称
    private final double unitPrice;// 购买时的商品单价

    public Order(String orderId, String userId, String productId, int quantity, double totalPrice,
            LocalDateTime orderDate, String productName, double unitPrice) {
        this.orderId = checkStr(orderId, "orderId");
        this.userId = checkStr(userId, "userId");
        this.productId = checkStr(productId, "productId");
        if (quantity <= 0)
            throw new IllegalArgumentException("quantity cannot be negative");
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.orderDate = orderDate;
        this.productName = checkStr(productName, "productName");
        if (unitPrice <= 0)
            throw new IllegalArgumentException("unitPrice cannot be negative");
        this.unitPrice = unitPrice;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getUserId() {
        return userId;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public String getProductName() {
        return productName;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

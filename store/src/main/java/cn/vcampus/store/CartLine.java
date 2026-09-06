package cn.vcampus.store;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 购物车明细读模型，全部金额以「分」为单位的 long 传递。
 * 由 getCartDetails 在读取时把购物车条目与商品实时联表得到，不落库、不加快照字段，
 * 因此商品改名或调价后购物车立刻显示新值，代价是商品被物理删除后该行会消失。
 * subtotalCents 与结账实扣金额同式计算（Math.round(单价元 × 数量 × 100)），是唯一可对账的金额；
 * unitPriceCents 仅供展示，unitPriceCents × quantity 可能因四舍五入与 subtotalCents 相差一两分，
 * 前端合计必须累加 subtotalCents，严禁用 unitPriceCents × quantity。
 */
public final class CartLine implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String cartItemId;// 购物车条目编号
    private final String productId;// 商品编号
    private final String productName;// 读取时的商品名称
    private final long unitPriceCents;// 读取时的商品单价，单位分，仅供展示
    private final int quantity;// 数量
    private final long subtotalCents;// 该行小计，单位分，与结账实扣同式
    private final boolean active;// 读取时商品是否在售
    private final LocalDateTime addedAt;// 加入时间

    public CartLine(String cartItemId, String productId, String productName, long unitPriceCents, int quantity,
            long subtotalCents, boolean active, LocalDateTime addedAt) {
        this.cartItemId = checkStr(cartItemId, "cartItemId");
        this.productId = checkStr(productId, "productId");
        this.productName = checkStr(productName, "productName");
        if (unitPriceCents < 0)
            throw new IllegalArgumentException("unitPriceCents cannot be negative");
        this.unitPriceCents = unitPriceCents;
        if (quantity <= 0)
            throw new IllegalArgumentException("quantity must be greater than zero");
        this.quantity = quantity;
        if (subtotalCents < 0)
            throw new IllegalArgumentException("subtotalCents cannot be negative");
        this.subtotalCents = subtotalCents;
        this.active = active;
        if (addedAt == null)
            throw new IllegalArgumentException("addedAt cannot be null");
        this.addedAt = addedAt;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

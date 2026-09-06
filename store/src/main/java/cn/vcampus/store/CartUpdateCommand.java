package cn.vcampus.store;

import java.io.Serializable;

// 修改购物车条目数量命令：仅携带 token 与条目编号，userId 由服务端从 token 解析，
// 条目归属校验在服务层完成，客户端无法通过指定他人 cartItemId 越权改数量
public final class CartUpdateCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final String cartItemId;// 购物车项ID
    private final int newQuantity;// 新数量

    public CartUpdateCommand(String token, String cartItemId, int newQuantity) {
        this.token = checkStr(token, "token");
        this.cartItemId = checkStr(cartItemId, "cartItemId");
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("newQuantity must be greater than zero");
        }
        this.newQuantity = newQuantity;
    }

    public String getToken() {
        return token;
    }

    public String getCartItemId() {
        return cartItemId;
    }

    public int getNewQuantity() {
        return newQuantity;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

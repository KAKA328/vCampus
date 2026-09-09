package cn.vcampus.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 购物车结算选中命令：仅结算勾选的子集条目，语义与整单结账一致
public final class CartCheckoutSelectedCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final List<String> cartItemIds;// 待结算的购物车项ID列表，不可为空

    public CartCheckoutSelectedCommand(String token, List<String> cartItemIds) {
        this.token = checkStr(token, "token");
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("cartItemIds cannot be empty");
        }
        this.cartItemIds = Collections.unmodifiableList(new ArrayList<String>(cartItemIds));
    }

    public String getToken() {
        return token;
    }

    public List<String> getCartItemIds() {
        return cartItemIds;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

package cn.vcampus.store;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// 购物车批量删除命令：一次删除多条本人购物车条目
public final class CartRemoveBatchCommand implements Serializable {
    private static final long serialVersionUID = 1L;// 序列化版本号
    private final String token;// 用户令牌
    private final List<String> cartItemIds;// 待删除的购物车项ID列表，不可为空

    public CartRemoveBatchCommand(String token, List<String> cartItemIds) {
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

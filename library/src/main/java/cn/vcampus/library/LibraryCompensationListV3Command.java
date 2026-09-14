package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryCompensationListV3Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 是否请求管理员可见的全部用户范围。 */
    private final boolean allUsers;

    /** 封装赔偿记录查询范围；全部用户范围仍须通过服务端授权。 */
    public LibraryCompensationListV3Command(String token, boolean allUsers) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.allUsers = allUsers;
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 是否请求全部用户记录；该标志不能代替服务端权限校验。 */
    public boolean isAllUsers() { return allUsers; }
}

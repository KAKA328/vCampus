package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryWalletQueryV3Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;

    /** 封装本人共享钱包查询，不能由客户端指定其他账户。 */
    public LibraryWalletQueryV3Command(String token) {
        this.token = LibraryCommandSupport.required(token, "token");
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
}

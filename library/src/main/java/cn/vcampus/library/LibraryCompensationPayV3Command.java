package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryCompensationPayV3Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 赔偿账单编号。 */
    private final String compensationId;

    /** 校验会话和赔偿单编号；实际扣款账户由服务端会话确定。 */
    public LibraryCompensationPayV3Command(String token, String compensationId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.compensationId = LibraryCommandSupport.required(compensationId, "compensationId");
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回赔偿账单编号。 */
    public String getCompensationId() { return compensationId; }
}

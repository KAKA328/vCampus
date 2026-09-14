package cn.vcampus.library;

import java.io.Serializable;

/** Explicit V3 contract; identity and monetary amounts are resolved by the server. */
public final class LibraryLossDeclareV3Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 单笔借阅记录编号。 */
    private final String recordId;

    /** 校验管理员会话与借阅记录编号，封装遗失登记请求。 */
    public LibraryLossDeclareV3Command(String token, String recordId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.recordId = LibraryCommandSupport.required(recordId, "recordId");
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回单笔借阅记录编号。 */
    public String getRecordId() { return recordId; }
}

package cn.vcampus.library;

import java.io.Serializable;

/** Return one active record owned by the current session user. */
public final class LibraryReturnV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 单笔借阅记录编号。 */
    private final String recordId;

    /** 封装待归还的借阅记录编号；读者身份由服务端会话推导。 */
    public LibraryReturnV2Command(String token, String recordId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.recordId = LibraryCommandSupport.required(recordId, "recordId");
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回单笔借阅记录编号。 */
    public String getRecordId() { return recordId; }
}

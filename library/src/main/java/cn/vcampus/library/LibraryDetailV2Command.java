package cn.vcampus.library;

import java.io.Serializable;

/** 携带会话令牌和图书编号的 V2 详情查询命令。 */
public final class LibraryDetailV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 馆藏图书编号。 */
    private final String bookId;

    /** 校验会话和图书编号，封装单本馆藏详情请求。 */
    public LibraryDetailV2Command(String token, String bookId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回馆藏图书编号。 */
    public String getBookId() { return bookId; }
}

package cn.vcampus.library;

import java.io.Serializable;

/** 管理员查询实体册编号与状态，不允许直接修改册状态。 */
public final class LibraryCopiesV4Command implements Serializable {
    /** 新命令的序列化版本。 */
    private static final long serialVersionUID = 1L;
    /** 已登录会话。 */
    private final String token;
    /** 要查询的馆藏编号。 */
    private final String bookId;
    /** 校验会话和馆藏编号。 */
    public LibraryCopiesV4Command(String token, String bookId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
    }
    /** 返回会话。 */
    public String getToken() { return token; }
    /** 返回馆藏编号。 */
    public String getBookId() { return bookId; }
}

package cn.vcampus.library;

import java.io.Serializable;

/** Librarian/admin command for adding a catalog entry and its opening stock. */
public final class LibraryAddBookV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 馆藏信息与库存快照。 */
    private final Book book;

    /** 校验会话令牌和新增馆藏对象，封装已有 V2 新增图书请求。 */
    public LibraryAddBookV2Command(String token, Book book) {
        this.token = LibraryCommandSupport.required(token, "token");
        if (book == null) throw new IllegalArgumentException("book must not be null");
        this.book = book;
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 按编号读取馆藏详情，不存在时返回原有失败状态。 */
    public Book getBook() { return book; }
}

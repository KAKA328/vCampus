package cn.vcampus.library;

import java.io.Serializable;

/** 管理员补充已有馆藏的独立 V2 命令；copies 是新增册数，不是目标库存。 */
public final class LibraryRestockV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 馆藏图书编号。 */
    private final String bookId;
    /** 本次新增的正整数册数。 */
    private final int copies;

    /** 校验会话、图书编号和正整数补充册数，封装已有 V2 补货请求。 */
    public LibraryRestockV2Command(String token, String bookId, int copies) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
        if (copies <= 0) throw new IllegalArgumentException("copies must be positive");
        this.copies = copies;
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回馆藏图书编号。 */
    public String getBookId() { return bookId; }
    /** 返回本次新增的正整数册数。 */
    public int getCopies() { return copies; }
}

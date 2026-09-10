package cn.vcampus.library;

import java.io.Serializable;

/** 管理员补充已有馆藏的独立 V2 命令；copies 是新增册数，不是目标库存。 */
public final class LibraryRestockV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String bookId;
    private final int copies;

    public LibraryRestockV2Command(String token, String bookId, int copies) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
        if (copies <= 0) throw new IllegalArgumentException("copies must be positive");
        this.copies = copies;
    }

    public String getToken() { return token; }
    public String getBookId() { return bookId; }
    public int getCopies() { return copies; }
}

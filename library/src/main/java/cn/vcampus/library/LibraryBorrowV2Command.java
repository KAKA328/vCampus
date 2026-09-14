package cn.vcampus.library;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single or batch borrow command. Borrower identity is resolved from token. */
public final class LibraryBorrowV2Command implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 服务端签发的会话令牌。 */
    private final String token;
    /** 本批请求借阅的不同图书编号。 */
    private final List<String> bookIds;

    /** 校验并复制本批图书编号；借阅人由服务端根据会话确定。 */
    public LibraryBorrowV2Command(String token, List<String> bookIds) {
        this.token = LibraryCommandSupport.required(token, "token");
        if (bookIds == null || bookIds.isEmpty()) throw new IllegalArgumentException("bookIds must not be empty");
        List<String> copy = new ArrayList<String>();
        for (String bookId : bookIds) copy.add(LibraryCommandSupport.required(bookId, "bookId"));
        this.bookIds = Collections.unmodifiableList(copy);
    }

    /** 把单本借阅转换为已有的批量借阅命令。 */
    public LibraryBorrowV2Command(String token, String bookId) {
        this(token, Collections.singletonList(bookId));
    }

    /** 返回服务端签发的会话令牌。 */
    public String getToken() { return token; }
    /** 返回本批请求借阅的不同图书编号。 */
    public List<String> getBookIds() { return bookIds; }
}

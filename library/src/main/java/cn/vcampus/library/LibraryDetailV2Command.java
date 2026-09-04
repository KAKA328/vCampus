package cn.vcampus.library;

import java.io.Serializable;

public final class LibraryDetailV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final String bookId;

    public LibraryDetailV2Command(String token, String bookId) {
        this.token = LibraryCommandSupport.required(token, "token");
        this.bookId = LibraryCommandSupport.required(bookId, "bookId");
    }

    public String getToken() { return token; }
    public String getBookId() { return bookId; }
}

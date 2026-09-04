package cn.vcampus.library;

import java.io.Serializable;

/** Librarian/admin command for adding a catalog entry and its opening stock. */
public final class LibraryAddBookV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final Book book;

    public LibraryAddBookV2Command(String token, Book book) {
        this.token = LibraryCommandSupport.required(token, "token");
        if (book == null) throw new IllegalArgumentException("book must not be null");
        this.book = book;
    }

    public String getToken() { return token; }
    public Book getBook() { return book; }
}

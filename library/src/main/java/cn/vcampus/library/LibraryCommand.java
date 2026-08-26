package cn.vcampus.library;

import java.io.Serializable;

/** Command for authenticated borrow/return operations. */
public final class LibraryCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String patronId;
    private final String bookId;

    public LibraryCommand(String token, String patronId, String bookId) {
        this.token = requireText(token, "token");
        this.patronId = requireText(patronId, "patronId");
        this.bookId = requireText(bookId, "bookId");
    }

    public String getToken() { return token; }
    public String getPatronId() { return patronId; }
    public String getBookId() { return bookId; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

package cn.vcampus.library;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Single or batch borrow command. Borrower identity is resolved from token. */
public final class LibraryBorrowV2Command implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String token;
    private final List<String> bookIds;

    public LibraryBorrowV2Command(String token, List<String> bookIds) {
        this.token = LibraryCommandSupport.required(token, "token");
        if (bookIds == null || bookIds.isEmpty()) throw new IllegalArgumentException("bookIds must not be empty");
        List<String> copy = new ArrayList<String>();
        for (String bookId : bookIds) copy.add(LibraryCommandSupport.required(bookId, "bookId"));
        this.bookIds = Collections.unmodifiableList(copy);
    }

    public LibraryBorrowV2Command(String token, String bookId) {
        this(token, Collections.singletonList(bookId));
    }

    public String getToken() { return token; }
    public List<String> getBookIds() { return bookIds; }
}

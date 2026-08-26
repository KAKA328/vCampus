package cn.vcampus.library;

import java.io.Serializable;

/** Book value object. */
public final class Book implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String bookId;
    private final String title;
    private final String author;
    private final int availableCopies;

    public Book(String bookId, String title, String author) {
        this(bookId, title, author, 1);
    }

    public Book(String bookId, String title, String author, int availableCopies) {
        this.bookId = requireText(bookId, "bookId");
        this.title = requireText(title, "title");
        this.author = requireText(author, "author");
        if (availableCopies < 0) {
            throw new IllegalArgumentException("availableCopies cannot be negative");
        }
        this.availableCopies = availableCopies;
    }

    public String getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public int getAvailableCopies() { return availableCopies; }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}

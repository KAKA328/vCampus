package cn.vcampus.library;

import java.io.Serializable;

/** Book value object. */
public final class Book implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String bookId; private final String title; private final String author;
    public Book(String bookId, String title, String author) { this.bookId=bookId; this.title=title; this.author=author; }
    public String getBookId() { return bookId; } public String getTitle() { return title; } public String getAuthor() { return author; }
}

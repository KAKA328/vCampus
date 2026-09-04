package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** Catalog entry and inventory snapshot for one book title. */
public final class Book implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String bookId;
    private final String title;
    private final String author;
    private final String isbn;
    private final String category;
    private final String publisher;
    private final int totalCopies;
    private final int availableCopies;
    private final String location;

    public Book(String bookId, String title, String author) {
        this(bookId, title, author, "", "", "", 1, 1, "");
    }

    public Book(String bookId, String title, String author, String isbn, String category,
            String publisher, int totalCopies, int availableCopies, String location) {
        this.bookId = requireText(bookId, "bookId");
        this.title = requireText(title, "title");
        this.author = requireText(author, "author");
        this.isbn = optionalText(isbn);
        this.category = optionalText(category);
        this.publisher = optionalText(publisher);
        this.location = optionalText(location);
        if (totalCopies < 0) throw new IllegalArgumentException("totalCopies must be >= 0");
        if (availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("availableCopies must be between 0 and totalCopies");
        }
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public Book withAvailableCopies(int updated) {
        return new Book(bookId, title, author, isbn, category, publisher, totalCopies, updated, location);
    }

    public String getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public String getCategory() { return category; }
    public String getPublisher() { return publisher; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public String getLocation() { return location; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Book)) return false;
        Book that = (Book) other;
        return totalCopies == that.totalCopies && availableCopies == that.availableCopies
                && bookId.equals(that.bookId) && title.equals(that.title) && author.equals(that.author)
                && isbn.equals(that.isbn) && category.equals(that.category)
                && publisher.equals(that.publisher) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, title, author, isbn, category, publisher,
                Integer.valueOf(totalCopies), Integer.valueOf(availableCopies), location);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) { return value == null ? "" : value.trim(); }
}

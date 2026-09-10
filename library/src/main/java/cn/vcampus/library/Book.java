package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** Catalog entry and inventory snapshot for one book title. */
public final class Book implements Serializable {
    private static final long serialVersionUID = 3L;

    private final String bookId;
    private final String title;
    private final String author;
    private final String isbn;
    private final String category;
    private final String publisher;
    private final double price;
    private final int totalCopies;
    private final int availableCopies;
    private final String location;

    public Book(String bookId, String title, String author) {
        this(bookId, title, author, "", "", "", 0.0d, 1, 1, "");
    }

    public Book(String bookId, String title, String author, String isbn, String category,
            String publisher, int totalCopies, int availableCopies, String location) {
        this(bookId, title, author, isbn, category, publisher, 0.0d,
                totalCopies, availableCopies, location);
    }

    public Book(String bookId, String title, String author, String isbn, String category,
            String publisher, double price, int totalCopies, int availableCopies, String location) {
        this.bookId = requireText(bookId, "bookId");
        this.title = requireText(title, "title");
        this.author = requireText(author, "author");
        this.isbn = optionalText(isbn);
        this.category = optionalText(category);
        this.publisher = optionalText(publisher);
        this.location = optionalText(location);
        if (!Double.isFinite(price) || price < 0.0d) {
            throw new IllegalArgumentException("price must be a finite non-negative number");
        }
        this.price = price;
        if (totalCopies < 0) throw new IllegalArgumentException("totalCopies must be >= 0");
        if (availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("availableCopies must be between 0 and totalCopies");
        }
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public Book withAvailableCopies(int updated) {
        return new Book(bookId, title, author, isbn, category, publisher, price,
                totalCopies, updated, location);
    }

    /** 校验并生成补货后的快照，同时增加总量、可借量，保持借出量不变。 */
    public Book withAdditionalCopies(int copies) {
        if (copies <= 0) throw new IllegalArgumentException("copies must be positive");
        if (totalCopies > Integer.MAX_VALUE - copies
                || availableCopies > Integer.MAX_VALUE - copies) {
            throw new IllegalArgumentException("book inventory exceeds the supported limit");
        }
        return new Book(bookId, title, author, isbn, category, publisher, price,
                totalCopies + copies, availableCopies + copies, location);
    }

    public String getBookId() { return bookId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public String getCategory() { return category; }
    public String getPublisher() { return publisher; }
    public double getPrice() { return price; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    public String getLocation() { return location; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Book)) return false;
        Book that = (Book) other;
        return Double.compare(price, that.price) == 0
                && totalCopies == that.totalCopies && availableCopies == that.availableCopies
                && bookId.equals(that.bookId) && title.equals(that.title) && author.equals(that.author)
                && isbn.equals(that.isbn) && category.equals(that.category)
                && publisher.equals(that.publisher) && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookId, title, author, isbn, category, publisher, Double.valueOf(price),
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

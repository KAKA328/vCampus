package cn.vcampus.library;

import java.io.Serializable;
import java.util.Objects;

/** Catalog entry and inventory snapshot for one book title. */
public final class Book implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 3L;

    /** 馆藏图书编号。 */
    private final String bookId;
    /** 图书名称。 */
    private final String title;
    /** 图书作者。 */
    private final String author;
    /** 图书 ISBN。 */
    private final String isbn;
    /** 馆藏分类。 */
    private final String category;
    /** 出版社。 */
    private final String publisher;
    /** 有限非负的图书原价，单位为元。 */
    private final double price;
    /** 馆藏总册数。 */
    private final int totalCopies;
    /** 当前可借册数。 */
    private final int availableCopies;
    /** 馆藏位置。 */
    private final String location;

    /** 创建默认价格为零、总量与可借量均为一册的简化馆藏条目。 */
    public Book(String bookId, String title, String author) {
        this(bookId, title, author, "", "", "", 0.0d, 1, 1, "");
    }

    /** 创建旧调用方使用的馆藏快照；未提供价格时默认为零元。 */
    public Book(String bookId, String title, String author, String isbn, String category,
            String publisher, int totalCopies, int availableCopies, String location) {
        this(bookId, title, author, isbn, category, publisher, 0.0d,
                totalCopies, availableCopies, location);
    }

    /** 校验必填文字、非负有限价格及库存范围，构造不可变馆藏快照。 */
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

    /** 返回新的可借库存快照，原对象保持不变。 */
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

    /** 返回馆藏图书编号。 */
    public String getBookId() { return bookId; }
    /** 返回图书名称。 */
    public String getTitle() { return title; }
    /** 返回图书作者。 */
    public String getAuthor() { return author; }
    /** 返回图书 ISBN。 */
    public String getIsbn() { return isbn; }
    /** 返回馆藏分类。 */
    public String getCategory() { return category; }
    /** 返回出版社。 */
    public String getPublisher() { return publisher; }
    /** 返回有限非负的图书原价，单位为元。 */
    public double getPrice() { return price; }
    /** 返回馆藏总册数。 */
    public int getTotalCopies() { return totalCopies; }
    /** 返回当前可借册数。 */
    public int getAvailableCopies() { return availableCopies; }
    /** 返回馆藏位置。 */
    public String getLocation() { return location; }

    /** 比较业务快照的全部值字段。 */
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

    /** 根据与相等性一致的字段计算哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(bookId, title, author, isbn, category, publisher, Double.valueOf(price),
                Integer.valueOf(totalCopies), Integer.valueOf(availableCopies), location);
    }

    /** 校验并规范化必填文本。 */
    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    /** 将可选文本规范化为空串或去除首尾空白的值。 */
    private static String optionalText(String value) { return value == null ? "" : value.trim(); }
}

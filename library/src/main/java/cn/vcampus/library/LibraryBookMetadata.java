package cn.vcampus.library;

import java.util.Objects;

/** 两种仓库共用的资料校验与比较规则；编辑绝不覆盖并发借还产生的库存。 */
public final class LibraryBookMetadata {
    /** 工具类无需实例。 */
    private LibraryBookMetadata() { }
    /** 检查原值及新值，重新构造 Book 以防绕过构造方法的反序列化输入。 */
    public static void validateEdit(String operatorId, Book expected, Book replacement) {
        LibraryCommandSupport.required(operatorId, "operatorId");
        if (expected == null || replacement == null) throw new IllegalArgumentException("book is required");
        validated(expected);
        validated(replacement);
        if (!expected.getBookId().equals(replacement.getBookId())
                || expected.getTotalCopies() != replacement.getTotalCopies()
                || expected.getAvailableCopies() != replacement.getAvailableCopies()) {
            throw new IllegalArgumentException("编辑资料不能修改图书号或库存，请使用增加库存功能");
        }
    }
    /** 只比较可编辑资料；并发借还及补库存不造成无意义的资料冲突。 */
    public static boolean same(Book first, Book second) {
        return first != null && second != null && first.getBookId().equals(second.getBookId())
                && first.getTitle().equals(second.getTitle()) && first.getAuthor().equals(second.getAuthor())
                && first.getIsbn().equals(second.getIsbn()) && first.getCategory().equals(second.getCategory())
                && first.getPublisher().equals(second.getPublisher()) && first.getLocation().equals(second.getLocation())
                && Double.compare(first.getPrice(), second.getPrice()) == 0;
    }
    /** 使用最新库存与新资料构造结果，库存只能由借还、补货及遗失改变。 */
    public static Book merge(Book replacement, Book current) {
        return new Book(current.getBookId(), replacement.getTitle(), replacement.getAuthor(), replacement.getIsbn(),
                replacement.getCategory(), replacement.getPublisher(), replacement.getPrice(),
                current.getTotalCopies(), current.getAvailableCopies(), replacement.getLocation());
    }
    /** 校验 Access 字段长度和价格，不截断用户输入。 */
    public static Book validated(Book book) {
        Objects.requireNonNull(book, "book");
        check(book.getBookId(), 32); check(book.getTitle(), 120); check(book.getAuthor(), 100);
        check(book.getIsbn(), 32); check(book.getCategory(), 64); check(book.getPublisher(), 100); check(book.getLocation(), 64);
        return new Book(book.getBookId(), book.getTitle(), book.getAuthor(), book.getIsbn(), book.getCategory(),
                book.getPublisher(), book.getPrice(), book.getTotalCopies(), book.getAvailableCopies(), book.getLocation());
    }
    /** 用于审计的完整可编辑资料，不包含密码或钱包信息。 */
    public static String auditText(Book book) {
        return "title=" + book.getTitle() + "\nauthor=" + book.getAuthor() + "\nisbn=" + book.getIsbn()
                + "\ncategory=" + book.getCategory() + "\npublisher=" + book.getPublisher()
                + "\nprice=" + book.getPrice() + "\nlocation=" + book.getLocation();
    }
    /** 拒绝超出持久化边界的文本。 */
    private static void check(String text, int maximum) {
        if (text == null || text.length() > maximum) throw new IllegalArgumentException("资料字段为空或超过长度限制");
    }
}

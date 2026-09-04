package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Thread-safe repository used by tests and server demo mode. */
public final class InMemoryLibraryRepository implements LibraryRepository {
    private final Map<String, Book> books = new LinkedHashMap<String, Book>();
    private final List<BorrowRecord> records = new ArrayList<BorrowRecord>();

    public InMemoryLibraryRepository() { }

    public InMemoryLibraryRepository(List<Book> initialBooks) {
        if (initialBooks == null) throw new IllegalArgumentException("initialBooks must not be null");
        for (Book book : initialBooks) {
            if (!addBook(book)) throw new IllegalArgumentException("duplicate book id: " + book.getBookId());
        }
    }

    public static InMemoryLibraryRepository withDemoCatalog() {
        List<Book> catalog = new ArrayList<Book>();
        catalog.add(new Book("B001", "Java核心技术（卷I）", "Cay S. Horstmann",
                "9787115547392", "计算机", "机械工业出版社", 3, 3, "A-01"));
        catalog.add(new Book("B002", "算法导论", "Thomas H. Cormen",
                "9787111407010", "计算机", "机械工业出版社", 2, 2, "A-02"));
        catalog.add(new Book("B003", "红楼梦", "曹雪芹",
                "9787020002207", "文学", "人民文学出版社", 2, 2, "B-01"));
        catalog.add(new Book("B004", "三体", "刘慈欣",
                "9787536692930", "科幻", "重庆出版社", 4, 4, "B-02"));
        catalog.add(new Book("B005", "高等数学（第七版）", "同济大学数学系",
                "9787040396638", "教材", "高等教育出版社", 5, 5, "C-01"));
        return new InMemoryLibraryRepository(catalog);
    }

    @Override
    public synchronized List<Book> search(String keyword, String category) {
        String key = normalize(keyword);
        String categoryKey = category == null ? null : normalize(category);
        List<Book> found = new ArrayList<Book>();
        for (Book book : books.values()) {
            if (categoryKey != null && !normalize(book.getCategory()).equals(categoryKey)) continue;
            if (key.isEmpty() || contains(book.getBookId(), key) || contains(book.getTitle(), key)
                    || contains(book.getAuthor(), key) || contains(book.getIsbn(), key)
                    || contains(book.getCategory(), key)) found.add(book);
        }
        return found;
    }

    @Override
    public synchronized Book findBook(String bookId) {
        return bookId == null ? null : books.get(bookId.trim());
    }

    @Override
    public synchronized boolean addBook(Book book) {
        if (book == null || books.containsKey(book.getBookId())) return false;
        books.put(book.getBookId(), book);
        return true;
    }

    @Override
    public synchronized ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bookId);
            if (book.getAvailableCopies() <= 0) {
                return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bookId);
            }
            if (findActive(userId, bookId) != null) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed: " + bookId);
            }
        }
        String orderId = id("BO");
        List<BorrowRecord> created = new ArrayList<BorrowRecord>();
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            books.put(bookId, book.withAvailableCopies(book.getAvailableCopies() - 1));
            BorrowRecord record = new BorrowRecord(orderId, id("BR"), userId, bookId,
                    borrowDate, dueDate, null, BorrowStatus.BORROWED);
            records.add(record);
            created.add(record);
        }
        return ServiceResult.ok(created);
    }

    @Override
    public synchronized ServiceResult<BorrowRecord> returnBook(
            String userId, String recordId, LocalDate returnDate) {
        for (int index = 0; index < records.size(); index++) {
            BorrowRecord record = records.get(index);
            if (!record.getRecordId().equals(recordId) || !record.getUserId().equals(userId)
                    || record.getStatus() != BorrowStatus.BORROWED) continue;
            Book book = books.get(record.getBookId());
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            BorrowRecord returned = record.returned(returnDate);
            records.set(index, returned);
            books.put(book.getBookId(), book.withAvailableCopies(book.getAvailableCopies() + 1));
            return ServiceResult.ok(returned);
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "active borrowing record not found");
    }

    @Override
    public synchronized List<BorrowRecord> findBorrowHistory(String userId) {
        List<BorrowRecord> history = new ArrayList<BorrowRecord>();
        for (BorrowRecord record : records) {
            if (record.getUserId().equals(userId)) history.add(record);
        }
        return history;
    }

    @Override
    public synchronized List<BorrowRecord> findAllBorrowHistory() {
        return new ArrayList<BorrowRecord>(records);
    }

    private BorrowRecord findActive(String userId, String bookId) {
        for (BorrowRecord record : records) {
            if (record.getStatus() == BorrowStatus.BORROWED
                    && record.getUserId().equals(userId) && record.getBookId().equals(bookId)) return record;
        }
        return null;
    }

    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString(); }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static boolean contains(String value, String key) { return normalize(value).contains(key); }
}

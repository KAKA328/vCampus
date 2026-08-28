package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Temporary in-memory library service for local demos and unit tests.
 * Replace with an Access-backed implementation later.
 */
public final class InMemoryLibraryService implements LibraryService {
    private static final int BORROW_DAYS = 30;

    private final Map<String, Book> books = new LinkedHashMap<String, Book>();
    private final List<BorrowRecord> records = new ArrayList<BorrowRecord>();
    private long recordSequence = 0;
    private long orderSequence = 0;

    public InMemoryLibraryService() {
        seedCatalog();
    }

    @Override public synchronized ServiceResult<List<Book>> search(String keyword) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Book> found = new ArrayList<Book>();
        for (Book book : books.values()) {
            if (matches(book, key)) {
                found.add(book);
            }
        }
        return ServiceResult.ok(found);
    }

    @Override public synchronized ServiceResult<Book> getBook(String bookId) {
        Book book = findBook(bookId);
        if (book == null) {
            return ServiceResult.<Book>failure(StatusCode.NOT_FOUND, "book not found");
        }
        return ServiceResult.ok(book);
    }

    @Override public synchronized ServiceResult<List<Book>> listByCategory(String category) {
        String key = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        List<Book> found = new ArrayList<Book>();
        for (Book book : books.values()) {
            if (book.getCategory().toLowerCase(Locale.ROOT).equals(key)) {
                found.add(book);
            }
        }
        return ServiceResult.ok(found);
    }

    @Override public synchronized ServiceResult<Void> addBook(Book book) {
        if (book == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "book must not be null");
        }
        if (books.containsKey(book.getBookId())) {
            return ServiceResult.failure(StatusCode.CONFLICT, "book id already exists");
        }
        books.put(book.getBookId(), book);
        return ServiceResult.ok(null);
    }

    @Override public synchronized ServiceResult<Void> borrow(String userId, String bookId) {
        return borrowBatch(userId, Collections.singletonList(bookId));
    }

    @Override public synchronized ServiceResult<Void> borrowBatch(String userId, List<String> bookIds) {
        if (blank(userId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        if (bookIds == null || bookIds.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookIds must not be empty");
        }
        String uid = userId.trim();
        List<String> ids = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String raw : bookIds) {
            if (blank(raw)) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId must not be blank");
            }
            String bid = raw.trim();
            if (!seen.add(bid)) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "duplicate book id: " + bid);
            }
            Book book = findBook(bid);
            if (book == null) {
                return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bid);
            }
            if (book.getAvailableCopies() <= 0) {
                return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bid);
            }
            if (findActiveRecord(uid, bid) != null) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed by this user: " + bid);
            }
            ids.add(bid);
        }
        String orderId = nextOrderId();
        LocalDate today = LocalDate.now();
        for (String bid : ids) {
            Book book = books.get(bid);
            books.put(bid, book.withAvailableCopies(book.getAvailableCopies() - 1));
            records.add(new BorrowRecord(orderId, nextRecordId(), uid, bid,
                    today, today.plusDays(BORROW_DAYS), null, BorrowStatus.BORROWED));
        }
        return ServiceResult.ok(null);
    }

    @Override public synchronized ServiceResult<Void> returnBook(String userId, String bookId) {
        if (blank(userId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        }
        BorrowRecord active = findActiveRecord(userId, bookId);
        if (active == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "no active borrowing record");
        }
        Book book = findBook(bookId);
        if (book == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        }
        int index = records.indexOf(active);
        records.set(index, active.returned(LocalDate.now()));
        books.put(bookId, book.withAvailableCopies(book.getAvailableCopies() + 1));
        return ServiceResult.ok(null);
    }

    @Override public synchronized ServiceResult<List<BorrowRecord>> borrowHistory(String userId) {
        String key = userId == null ? "" : userId.trim();
        List<BorrowRecord> history = new ArrayList<BorrowRecord>();
        for (BorrowRecord record : records) {
            if (record.getUserId().equals(key)) {
                history.add(record);
            }
        }
        return ServiceResult.ok(history);
    }

    private Book findBook(String bookId) {
        return bookId == null ? null : books.get(bookId.trim());
    }

    private BorrowRecord findActiveRecord(String userId, String bookId) {
        String uid = userId.trim();
        String bid = bookId.trim();
        for (BorrowRecord record : records) {
            if (record.getStatus() == BorrowStatus.BORROWED
                    && record.getUserId().equals(uid)
                    && record.getBookId().equals(bid)) {
                return record;
            }
        }
        return null;
    }

    private String nextRecordId() {
        recordSequence = recordSequence + 1;
        return "BR" + recordSequence;
    }

    private String nextOrderId() {
        orderSequence = orderSequence + 1;
        return "BO" + orderSequence;
    }

    private boolean matches(Book book, String key) {
        if (key.isEmpty()) {
            return true;
        }
        return contains(book.getTitle(), key) || contains(book.getAuthor(), key)
                || contains(book.getIsbn(), key) || contains(book.getBookId(), key)
                || contains(book.getCategory(), key);
    }

    private static boolean contains(String value, String key) {
        return value.toLowerCase(Locale.ROOT).contains(key);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void seedCatalog() {
        books.put("B001", new Book("B001", "Java核心技术（卷I）", "Cay S. Horstmann",
                "9787115547392", "计算机", "机械工业出版社", 3, 3, "A-01"));
        books.put("B002", new Book("B002", "算法导论", "Thomas H. Cormen",
                "9787111407010", "计算机", "机械工业出版社", 2, 2, "A-02"));
        books.put("B003", new Book("B003", "红楼梦", "曹雪芹",
                "9787020002207", "文学", "人民文学出版社", 2, 2, "B-01"));
        books.put("B004", new Book("B004", "三体", "刘慈欣",
                "9787536692930", "科幻", "重庆出版社", 4, 4, "B-02"));
        books.put("B005", new Book("B005", "高等数学（第七版）", "同济大学数学系",
                "9787040396638", "教材", "高等教育出版社", 5, 5, "C-01"));
    }
}

package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Repository-backed implementation shared by in-memory and Access modes. */
public final class DefaultLibraryService implements LibraryService {
    private static final int BORROW_DAYS = 30;

    private final LibraryRepository repository;
    private final Clock clock;

    public DefaultLibraryService(LibraryRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    DefaultLibraryService(LibraryRepository repository, Clock clock) {
        if (repository == null || clock == null) {
            throw new IllegalArgumentException("repository and clock must not be null");
        }
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public ServiceResult<List<Book>> search(String keyword) {
        return search(keyword, null);
    }

    @Override
    public ServiceResult<List<Book>> search(String keyword, String category) {
        String normalizedCategory = blank(category) ? null : category.trim();
        return ServiceResult.ok(immutable(repository.search(optionalText(keyword), normalizedCategory)));
    }

    @Override
    public ServiceResult<Book> getBook(String bookId) {
        if (blank(bookId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId must not be blank");
        Book book = repository.findBook(bookId.trim());
        return book == null ? ServiceResult.<Book>failure(StatusCode.NOT_FOUND, "book not found")
                : ServiceResult.ok(book);
    }

    @Override
    public ServiceResult<List<Book>> listByCategory(String category) {
        if (blank(category)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "category must not be blank");
        return ServiceResult.ok(immutable(repository.search("", category.trim())));
    }

    @Override
    public ServiceResult<Book> addBook(Book book) {
        if (book == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "book must not be null");
        return repository.addBook(book) ? ServiceResult.ok(book)
                : ServiceResult.<Book>failure(StatusCode.CONFLICT, "book id already exists");
    }

    @Override
    public ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId) {
        return borrowBatch(userId, Collections.singletonList(bookId));
    }

    @Override
    public ServiceResult<List<BorrowRecord>> borrowBatch(String userId, List<String> bookIds) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (bookIds == null || bookIds.isEmpty()) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookIds must not be empty");
        }
        List<String> normalized = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String bookId : bookIds) {
            if (blank(bookId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId must not be blank");
            String normalizedId = bookId.trim();
            if (!seen.add(normalizedId)) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, "duplicate book id: " + normalizedId);
            }
            normalized.add(normalizedId);
        }
        LocalDate today = LocalDate.now(clock);
        return repository.borrowBatch(userId.trim(), normalized, today, today.plusDays(BORROW_DAYS));
    }

    @Override
    public ServiceResult<BorrowRecord> returnBook(String userId, String recordId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (blank(recordId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "recordId must not be blank");
        return repository.returnBook(userId.trim(), recordId.trim(), LocalDate.now(clock));
    }

    @Override
    public ServiceResult<List<BorrowRecord>> borrowHistory(String userId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        return ServiceResult.ok(immutable(repository.findBorrowHistory(userId.trim())));
    }

    @Override
    public ServiceResult<List<BorrowRecord>> allBorrowHistory() {
        return ServiceResult.ok(immutable(repository.findAllBorrowHistory()));
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    private static String optionalText(String value) { return value == null ? "" : value.trim(); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

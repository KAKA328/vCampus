package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory library implementation for early integration and UI demos. */
public final class InMemoryLibraryService implements LibraryService {
    private final Map<String, Book> booksById = new LinkedHashMap<String, Book>();
    private final Map<String, Set<String>> borrowedBookIdsByPatron = new LinkedHashMap<String, Set<String>>();

    public InMemoryLibraryService() {
        this(Arrays.asList(
                new Book("B001", "Java 核心技术", "Cay Horstmann", 2),
                new Book("B002", "数据库系统概论", "王珊", 3),
                new Book("B003", "计算机网络", "谢希仁", 2)));
    }

    public InMemoryLibraryService(List<Book> books) {
        for (Book book : books) {
            booksById.put(book.getBookId(), book);
        }
    }

    @Override
    public synchronized ServiceResult<List<Book>> search(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Book> matches = new ArrayList<Book>();
        for (Book book : booksById.values()) {
            if (normalized.isEmpty()
                    || book.getBookId().toLowerCase().contains(normalized)
                    || book.getTitle().toLowerCase().contains(normalized)
                    || book.getAuthor().toLowerCase().contains(normalized)) {
                matches.add(book);
            }
        }
        return ServiceResult.ok(matches);
    }

    @Override
    public synchronized ServiceResult<Void> borrow(String patronId, String bookId) {
        String normalizedPatronId = normalize(patronId);
        String normalizedBookId = normalize(bookId);
        if (normalizedPatronId == null || normalizedBookId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "patronId and bookId must not be blank");
        }
        Book book = booksById.get(normalizedBookId);
        if (book == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        }
        Set<String> borrowed = borrowedBookIdsByPatron.get(normalizedPatronId);
        if (borrowed != null && borrowed.contains(normalizedBookId)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed by patron");
        }
        if (book.getAvailableCopies() <= 0) {
            return ServiceResult.failure(StatusCode.CONFLICT, "book has no available copies");
        }
        booksById.put(normalizedBookId,
                new Book(book.getBookId(), book.getTitle(), book.getAuthor(), book.getAvailableCopies() - 1));
        if (borrowed == null) {
            borrowed = new LinkedHashSet<String>();
            borrowedBookIdsByPatron.put(normalizedPatronId, borrowed);
        }
        borrowed.add(normalizedBookId);
        return ServiceResult.ok(null);
    }

    @Override
    public synchronized ServiceResult<Void> returnBook(String patronId, String bookId) {
        String normalizedPatronId = normalize(patronId);
        String normalizedBookId = normalize(bookId);
        if (normalizedPatronId == null || normalizedBookId == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "patronId and bookId must not be blank");
        }
        Book book = booksById.get(normalizedBookId);
        if (book == null) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        }
        Set<String> borrowed = borrowedBookIdsByPatron.get(normalizedPatronId);
        if (borrowed == null || !borrowed.remove(normalizedBookId)) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "borrow record not found");
        }
        if (borrowed.isEmpty()) {
            borrowedBookIdsByPatron.remove(normalizedPatronId);
        }
        booksById.put(normalizedBookId,
                new Book(book.getBookId(), book.getTitle(), book.getAuthor(), book.getAvailableCopies() + 1));
        return ServiceResult.ok(null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

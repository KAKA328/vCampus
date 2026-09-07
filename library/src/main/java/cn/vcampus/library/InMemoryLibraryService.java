package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** In-memory facade; the default keeps loan history empty for isolated tests. */
public final class InMemoryLibraryService implements LibraryService {
    private final DefaultLibraryService delegate;

    public InMemoryLibraryService() {
        this(InMemoryLibraryRepository.withDemoCatalog());
    }

    private InMemoryLibraryService(InMemoryLibraryRepository repository) {
        this.delegate = new DefaultLibraryService(repository);
    }

    /** Creates the interactive demo service with catalog and relative-date loan samples. */
    public static InMemoryLibraryService withDemoData() {
        return new InMemoryLibraryService(InMemoryLibraryRepository.withDemoData());
    }

    @Override public ServiceResult<List<Book>> search(String keyword) { return delegate.search(keyword); }
    @Override public ServiceResult<List<Book>> search(String keyword, String category) {
        return delegate.search(keyword, category);
    }
    @Override public ServiceResult<Book> getBook(String bookId) { return delegate.getBook(bookId); }
    @Override public ServiceResult<List<Book>> listByCategory(String category) { return delegate.listByCategory(category); }
    @Override public ServiceResult<Book> addBook(Book book) { return delegate.addBook(book); }
    @Override public ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId) {
        return delegate.borrow(userId, bookId);
    }
    @Override public ServiceResult<List<BorrowRecord>> borrowBatch(String userId, List<String> bookIds) {
        return delegate.borrowBatch(userId, bookIds);
    }
    @Override public ServiceResult<BorrowRecord> returnBook(String userId, String recordId) {
        return delegate.returnBook(userId, recordId);
    }
    @Override public ServiceResult<List<BorrowRecord>> borrowHistory(String userId) {
        return delegate.borrowHistory(userId);
    }
    @Override public ServiceResult<List<BorrowRecord>> allBorrowHistory() { return delegate.allBorrowHistory(); }
}

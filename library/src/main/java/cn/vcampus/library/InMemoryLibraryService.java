package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Demo-mode facade backed by an in-memory repository and five sample books. */
public final class InMemoryLibraryService implements LibraryService {
    private final DefaultLibraryService delegate;

    public InMemoryLibraryService() {
        this.delegate = new DefaultLibraryService(InMemoryLibraryRepository.withDemoCatalog());
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

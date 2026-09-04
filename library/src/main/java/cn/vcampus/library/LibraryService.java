package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Library catalog and borrowing business contract. Trusted user ids are supplied by the server. */
public interface LibraryService {
    ServiceResult<List<Book>> search(String keyword);
    ServiceResult<List<Book>> search(String keyword, String category);
    ServiceResult<Book> getBook(String bookId);
    ServiceResult<List<Book>> listByCategory(String category);
    ServiceResult<Book> addBook(Book book);
    ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId);
    ServiceResult<List<BorrowRecord>> borrowBatch(String userId, List<String> bookIds);
    ServiceResult<BorrowRecord> returnBook(String userId, String recordId);
    ServiceResult<List<BorrowRecord>> borrowHistory(String userId);
    ServiceResult<List<BorrowRecord>> allBorrowHistory();
}

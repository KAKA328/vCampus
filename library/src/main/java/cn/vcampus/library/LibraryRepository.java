package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDate;
import java.util.List;

/** Persistence boundary; borrow and return methods must be atomic. */
public interface LibraryRepository {
    List<Book> search(String keyword, String category);
    Book findBook(String bookId);
    boolean addBook(Book book);
    /** 原子补充已有图书库存；总量和可借量同时增加，不改变借出量。 */
    ServiceResult<Book> restock(String bookId, int copies);
    ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate);
    ServiceResult<BorrowRecord> returnBook(String userId, String recordId, LocalDate returnDate);
    List<BorrowRecord> findBorrowHistory(String userId);
    List<BorrowRecord> findAllBorrowHistory();
}

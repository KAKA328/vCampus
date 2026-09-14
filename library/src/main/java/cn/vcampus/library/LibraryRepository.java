package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.time.LocalDate;
import java.util.List;

/** Persistence boundary; borrow and return methods must be atomic. */
public interface LibraryRepository {
    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    List<Book> search(String keyword, String category);
    /** 根据图书编号读取馆藏快照。 */
    Book findBook(String bookId);
    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    boolean addBook(Book book);
    /** 原子补充已有图书库存；总量和可借量同时增加，不改变借出量。 */
    ServiceResult<Book> restock(String bookId, int copies);
    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
    ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate);
    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    ServiceResult<BorrowRecord> returnBook(String userId, String recordId, LocalDate returnDate);
    /** 读取指定用户的借阅历史。 */
    List<BorrowRecord> findBorrowHistory(String userId);
    /** 读取全校借阅历史。 */
    List<BorrowRecord> findAllBorrowHistory();
}

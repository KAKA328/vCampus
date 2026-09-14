package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** Library catalog and borrowing business contract. Trusted user ids are supplied by the server. */
public interface LibraryService {
    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    ServiceResult<List<Book>> search(String keyword);
    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    ServiceResult<List<Book>> search(String keyword, String category);
    /** 按编号读取馆藏详情，不存在时返回原有失败状态。 */
    ServiceResult<Book> getBook(String bookId);
    /** 按非空分类查询馆藏。 */
    ServiceResult<List<Book>> listByCategory(String category);
    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    ServiceResult<Book> addBook(Book book);
    /** 为已有图书补充正整数册数；管理权限由服务端会话边界校验。 */
    ServiceResult<Book> restock(String bookId, int copies);
    /** 以当前会话身份申请借阅一本或一批图书。 */
    ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId);
    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
    ServiceResult<List<BorrowRecord>> borrowBatch(String userId, List<String> bookIds);
    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    ServiceResult<BorrowRecord> returnBook(String userId, String recordId);
    /** 查询指定真实用户的借阅历史。 */
    ServiceResult<List<BorrowRecord>> borrowHistory(String userId);
    /** 查询全校借阅历史，外层入口负责管理权限。 */
    ServiceResult<List<BorrowRecord>> allBorrowHistory();
}

package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** 显式 V4 扩展边界；旧 LibraryService 和 Repository 方法签名保留。 */
public interface LibraryCatalogV4 {
    /** 管理员查询某一馆藏的实体册；不返回借阅人的私人信息。 */
    ServiceResult<List<LibraryCopy>> copies(String bookId);
    /** 查询已授权用户的名称快照；null 表示已获管理员授权的全部记录。 */
    ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId);
    /** 比较旧资料后更新元数据并记录操作者；不接受库存或图书编号变化。 */
    ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement);
}

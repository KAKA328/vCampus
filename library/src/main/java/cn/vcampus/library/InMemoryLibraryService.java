package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import java.util.List;

/** In-memory facade; the default keeps loan history empty for isolated tests. */
public final class InMemoryLibraryService implements LibraryService, LibraryCatalogV4 {
    /** 实际执行业务的委托实现。 */
    private final DefaultLibraryService delegate;
    /** 查询演示仓库的实体册。 */
    @Override public ServiceResult<List<LibraryCopy>> copies(String bookId) { return delegate.copies(bookId); }
    /** 查询演示仓库的借阅快照。 */
    @Override public ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId) { return delegate.loanSnapshots(userId); }
    /** 编辑演示馆藏资料，保留当前库存。 */
    @Override public ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement) {
        return delegate.updateBook(operatorId, expected, replacement);
    }

    /** 使用内置演示仓库创建兼容的内存服务入口。 */
    public InMemoryLibraryService() {
        this(InMemoryLibraryRepository.withDemoCatalog());
    }

    /** 使用指定内存仓库提供与持久化实现一致的图书馆业务。 */
    private InMemoryLibraryService(InMemoryLibraryRepository repository) {
        this.delegate = new DefaultLibraryService(repository);
    }

    /** Creates the interactive demo service with catalog and relative-date loan samples. */
    public static InMemoryLibraryService withDemoData() {
        return new InMemoryLibraryService(InMemoryLibraryRepository.withDemoData());
    }

    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    @Override public ServiceResult<List<Book>> search(String keyword) { return delegate.search(keyword); }
    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    @Override public ServiceResult<List<Book>> search(String keyword, String category) {
        return delegate.search(keyword, category);
    }
    /** 按编号读取馆藏详情，不存在时返回原有失败状态。 */
    @Override public ServiceResult<Book> getBook(String bookId) { return delegate.getBook(bookId); }
    /** 按非空分类查询馆藏。 */
    @Override public ServiceResult<List<Book>> listByCategory(String category) { return delegate.listByCategory(category); }
    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    @Override public ServiceResult<Book> addBook(Book book) { return delegate.addBook(book); }
    /** 原子增加已有馆藏的总量和可借量，保持借出数量不变。 */
    @Override public ServiceResult<Book> restock(String bookId, int copies) {
        return delegate.restock(bookId, copies);
    }
    /** 以当前会话身份申请借阅一本或一批图书。 */
    @Override public ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId) {
        return delegate.borrow(userId, bookId);
    }
    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
    @Override public ServiceResult<List<BorrowRecord>> borrowBatch(String userId, List<String> bookIds) {
        return delegate.borrowBatch(userId, bookIds);
    }
    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    @Override public ServiceResult<BorrowRecord> returnBook(String userId, String recordId) {
        return delegate.returnBook(userId, recordId);
    }
    /** 查询指定真实用户的借阅历史。 */
    @Override public ServiceResult<List<BorrowRecord>> borrowHistory(String userId) {
        return delegate.borrowHistory(userId);
    }
    /** 查询全校借阅历史，外层入口负责管理权限。 */
    @Override public ServiceResult<List<BorrowRecord>> allBorrowHistory() { return delegate.allBorrowHistory(); }
}

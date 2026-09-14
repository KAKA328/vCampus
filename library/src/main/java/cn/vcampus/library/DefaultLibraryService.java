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
public final class DefaultLibraryService implements LibraryService, LibraryCatalogV4 {
    /** 默认借阅期限天数。 */
    private static final int BORROW_DAYS = 30;

    /** 共享的馆藏与借阅仓库。 */
    private final LibraryRepository repository;
    /** 计算借阅和到期日期的时钟。 */
    private final Clock clock;

    /** 使用服务器默认时区的系统时钟提供借还业务。 */
    public DefaultLibraryService(LibraryRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** 注入仓库和时钟，以便隔离测试借阅期限与归还日期。 */
    DefaultLibraryService(LibraryRepository repository, Clock clock) {
        if (repository == null || clock == null) {
            throw new IllegalArgumentException("repository and clock must not be null");
        }
        this.repository = repository;
        this.clock = clock;
    }

    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    @Override
    public ServiceResult<List<Book>> search(String keyword) {
        return search(keyword, null);
    }

    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    @Override
    public ServiceResult<List<Book>> search(String keyword, String category) {
        String normalizedCategory = blank(category) ? null : category.trim();
        return ServiceResult.ok(immutable(repository.search(optionalText(keyword), normalizedCategory)));
    }

    /** 按编号读取馆藏详情，不存在时返回原有失败状态。 */
    @Override
    public ServiceResult<Book> getBook(String bookId) {
        if (blank(bookId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId must not be blank");
        Book book = repository.findBook(bookId.trim());
        return book == null ? ServiceResult.<Book>failure(StatusCode.NOT_FOUND, "book not found")
                : ServiceResult.ok(book);
    }

    /** 按非空分类查询馆藏。 */
    @Override
    public ServiceResult<List<Book>> listByCategory(String category) {
        if (blank(category)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "category must not be blank");
        return ServiceResult.ok(immutable(repository.search("", category.trim())));
    }

    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    @Override
    public ServiceResult<Book> addBook(Book book) {
        if (book == null) return ServiceResult.failure(StatusCode.BAD_REQUEST, "book must not be null");
        try { LibraryCopyRules.checkBatch(book.getTotalCopies()); }
        catch (IllegalArgumentException invalid) { return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage()); }
        return repository.addBook(book) ? ServiceResult.ok(book)
                : ServiceResult.<Book>failure(StatusCode.CONFLICT, "book id already exists");
    }

    /** 原子增加已有馆藏的总量和可借量，保持借出数量不变。 */
    @Override
    public ServiceResult<Book> restock(String bookId, int copies) {
        if (blank(bookId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId must not be blank");
        if (copies <= 0) return ServiceResult.failure(StatusCode.BAD_REQUEST, "copies must be positive");
        try { LibraryCopyRules.checkBatch(copies); }
        catch (IllegalArgumentException invalid) { return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage()); }
        return repository.restock(bookId.trim(), copies);
    }

    /** 以当前会话身份申请借阅一本或一批图书。 */
    @Override
    public ServiceResult<List<BorrowRecord>> borrow(String userId, String bookId) {
        return borrowBatch(userId, Collections.singletonList(bookId));
    }

    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
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

    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    @Override
    public ServiceResult<BorrowRecord> returnBook(String userId, String recordId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        if (blank(recordId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "recordId must not be blank");
        return repository.returnBook(userId.trim(), recordId.trim(), LocalDate.now(clock));
    }

    /** 查询指定真实用户的借阅历史。 */
    @Override
    public ServiceResult<List<BorrowRecord>> borrowHistory(String userId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId must not be blank");
        return ServiceResult.ok(immutable(repository.findBorrowHistory(userId.trim())));
    }

    /** 查询全校借阅历史，外层入口负责管理权限。 */
    @Override
    public ServiceResult<List<BorrowRecord>> allBorrowHistory() {
        return ServiceResult.ok(immutable(repository.findAllBorrowHistory()));
    }

    /** 复制结果列表并禁止调用方修改返回集合。 */
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    /** 将可选文本规范化为空串或去除首尾空白的值。 */
    private static String optionalText(String value) { return value == null ? "" : value.trim(); }
    /** 判断文本是否为空或仅含空白。 */
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    /** 仅通过显式 V4 实现读取实体册。 */
    @Override public ServiceResult<List<LibraryCopy>> copies(String bookId) {
        return repository instanceof LibraryCatalogV4 ? ((LibraryCatalogV4) repository).copies(bookId)
                : LibraryV4Delegation.unavailable();
    }
    /** 读取借阅时名称，不改旧历史接口的数据类型。 */
    @Override public ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId) {
        return repository instanceof LibraryCatalogV4 ? ((LibraryCatalogV4) repository).loanSnapshots(userId)
                : LibraryV4Delegation.unavailable();
    }
    /** 校验与冲突处理由同一共享仓库的 V4 实现承担。 */
    @Override public ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement) {
        return repository instanceof LibraryCatalogV4 ? ((LibraryCatalogV4) repository).updateBook(operatorId, expected, replacement)
                : LibraryV4Delegation.unavailable();
    }
}

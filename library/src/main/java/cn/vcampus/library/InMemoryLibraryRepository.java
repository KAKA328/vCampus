package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongPredicate;

/** Thread-safe repository used by tests and server demo mode. */
public final class InMemoryLibraryRepository implements LibraryRepository, LibraryCatalogV4 {
    /** 仓库创建时读取的每人同时在借上限。 */
    private final int borrowLimit = LibraryBorrowPolicy.configuredLimit();
    /** 按图书编号索引的馆藏集合。 */
    private final Map<String, Book> books = new LinkedHashMap<String, Book>();
    /** 共享的借阅记录集合。 */
    private final List<BorrowRecord> records = new ArrayList<BorrowRecord>();
    /** 与库存和借阅共用一把锁的实体册、名称快照。 */
    private final InMemoryLibraryCopies copyLedger = new InMemoryLibraryCopies();
    /** V4 资料和查询操作，仍在仓库锁内执行。 */
    private final InMemoryLibraryCatalogV4 catalogV4 = new InMemoryLibraryCatalogV4(books, records, copyLedger);
    /** 共享仓库锁内的赔偿状态管理器。 */
    private final InMemoryLibraryCompensations compensationRecords =
            new InMemoryLibraryCompensations(books, records, copyLedger);

    /** 创建空馆藏仓库，借阅和赔偿集合均独立于其他实例。 */
    public InMemoryLibraryRepository() { }

    /** 装载初始馆藏；null 参数或重复图书编号将被拒绝。 */
    public InMemoryLibraryRepository(List<Book> initialBooks) {
        if (initialBooks == null) throw new IllegalArgumentException("initialBooks must not be null");
        for (Book book : initialBooks) {
            if (!addBook(book)) throw new IllegalArgumentException("duplicate book id: " + book.getBookId());
        }
    }

    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    @Override
    public synchronized List<Book> search(String keyword, String category) {
        return LibraryMemorySearch.search(books, keyword, category);
    }

    /** 根据图书编号读取馆藏快照。 */
    @Override
    public synchronized Book findBook(String bookId) {
        return bookId == null ? null : books.get(bookId.trim());
    }

    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    @Override
    public synchronized boolean addBook(Book book) {
        if (book == null || books.containsKey(book.getBookId())) return false;
        copyLedger.addBook(book);
        books.put(book.getBookId(), book);
        return true;
    }

    /** 原子增加已有馆藏的总量和可借量，保持借出数量不变。 */
    @Override
    public synchronized ServiceResult<Book> restock(String bookId, int copies) {
        if (bookId == null || bookId.trim().isEmpty() || copies <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId and positive copies are required");
        }
        Book current = books.get(bookId.trim());
        if (current == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        final Book updated;
        try {
            updated = current.withAdditionalCopies(copies);
            LibraryCopyRules.checkBatch(copies);
        } catch (IllegalArgumentException invalidStock) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidStock.getMessage());
        }
        copyLedger.restock(current.getBookId(), copies);
        books.put(current.getBookId(), updated);
        return ServiceResult.ok(updated);
    }

    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
    @Override
    public synchronized ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        long active = records.stream().filter(record -> record.getUserId().equals(userId)
                && record.getStatus() == BorrowStatus.BORROWED).count();
        if (!LibraryBorrowPolicy.allows(borrowLimit, active, bookIds.size())) {
            return ServiceResult.failure(StatusCode.CONFLICT, LibraryBorrowPolicy.exceededMessage(borrowLimit));
        }
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bookId);
            if (book.getAvailableCopies() <= 0 || !copyLedger.hasAvailable(bookId)) {
                return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bookId);
            }
            if (findActive(userId, bookId) != null) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed: " + bookId);
            }
        }
        String orderId = id("BO");
        List<BorrowRecord> created = new ArrayList<BorrowRecord>();
        for (String bookId : bookIds) {
            Book book = books.get(bookId);
            books.put(bookId, book.withAvailableCopies(book.getAvailableCopies() - 1));
            BorrowRecord record = new BorrowRecord(orderId, id("BR"), userId, bookId,
                    borrowDate, dueDate, null, BorrowStatus.BORROWED);
            records.add(record);
            copyLedger.borrow(record, book.getTitle());
            created.add(record);
        }
        return ServiceResult.ok(created);
    }

    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    @Override
    public synchronized ServiceResult<BorrowRecord> returnBook(
            String userId, String recordId, LocalDate returnDate) {
        for (int index = 0; index < records.size(); index++) {
            BorrowRecord record = records.get(index);
            if (!record.getRecordId().equals(recordId) || !record.getUserId().equals(userId)
                    || record.getStatus() != BorrowStatus.BORROWED) continue;
            Book book = books.get(record.getBookId());
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            if (!copyLedger.canFinish(recordId)) return ServiceResult.failure(StatusCode.CONFLICT, "copy state changed");
            BorrowRecord returned = record.returned(returnDate);
            copyLedger.finish(recordId, LibraryCopyStatus.AVAILABLE);
            records.set(index, returned);
            books.put(book.getBookId(), book.withAvailableCopies(book.getAvailableCopies() + 1));
            return ServiceResult.ok(returned);
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "active borrowing record not found");
    }

    /** 读取指定用户的借阅历史。 */
    @Override
    public synchronized List<BorrowRecord> findBorrowHistory(String userId) {
        List<BorrowRecord> history = new ArrayList<BorrowRecord>();
        for (BorrowRecord record : records) {
            if (record.getUserId().equals(userId)) history.add(record);
        }
        return history;
    }

    /** 读取全校借阅历史。 */
    @Override
    public synchronized List<BorrowRecord> findAllBorrowHistory() {
        return new ArrayList<BorrowRecord>(records);
    }

    /** 查找同一用户对同一图书仍在借的记录。 */
    private BorrowRecord findActive(String userId, String bookId) {
        for (BorrowRecord record : records) {
            if (record.getStatus() == BorrowStatus.BORROWED
                    && record.getUserId().equals(userId) && record.getBookId().equals(bookId)) return record;
        }
        return null;
    }

    /** 为新业务记录生成带前缀的唯一编号。 */
    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString(); }
    /** 创建独立的 50 种馆藏演示仓库。 */
    public static InMemoryLibraryRepository withDemoCatalog() {
        return LibraryDemoCatalog.withDemoCatalog();
    }

    /** 在独立演示馆藏中加入临期、逾期和已归还记录。 */
    public static InMemoryLibraryRepository withDemoData() {
        return LibraryDemoCatalog.withDemoData();
    }

    /** 在图书仓库共享锁下登记遗失，重复登记不创建重复账单。 */
    public synchronized ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        return compensationRecords.declareLoss(operatorId, recordId);
    }

    /** 调用方先持有本仓库锁，再持有钱包锁；扣款失败不得改变赔偿记录。 */
    public synchronized ServiceResult<LibraryCompensation> payCompensation(String userId, String compensationId,
            LongPredicate debit) {
        return compensationRecords.payCompensation(userId, compensationId, debit);
    }

    /** 查询本人或全部赔偿记录；权限由原有服务端会话边界校验。 */
    public synchronized List<LibraryCompensation> findCompensations(String userId) {
        return compensationRecords.findCompensations(userId);
    }
    /** 共享锁内读取实体册。 */
    @Override public synchronized ServiceResult<List<LibraryCopy>> copies(String bookId) { return catalogV4.copies(bookId); }
    /** 共享锁内读取借阅名称快照。 */
    @Override public synchronized ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId) {
        return catalogV4.loanSnapshots(userId);
    }
    /** 共享锁内编辑资料，保留并发借还后的库存。 */
    @Override public synchronized ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement) {
        return catalogV4.updateBook(operatorId, expected, replacement);
    }
}

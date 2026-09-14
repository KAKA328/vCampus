package cn.vcampus.library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 内存实体册及借阅名称快照；仅由共享仓库锁下的调用方使用。 */
final class InMemoryLibraryCopies {
    /** 按独立实体册编号保存当前状态。 */
    private final Map<String, LibraryCopy> copies = new LinkedHashMap<String, LibraryCopy>();
    /** 每次借阅独立保存名称和册关系，归还不会删除。 */
    private final Map<String, LibraryLoanSnapshot> loans = new LinkedHashMap<String, LibraryLoanSnapshot>();

    /** 为新增条目创建实体册，不可借但无记录的原始库存明确标记待核对。 */
    void addBook(Book book) {
        LibraryCopyRules.checkBatch(book.getTotalCopies());
        for (int index = 0; index < book.getTotalCopies(); index++) {
            add(book.getBookId(), index < book.getAvailableCopies() ? LibraryCopyStatus.AVAILABLE : LibraryCopyStatus.UNAVAILABLE);
        }
    }
    /** 补库只新增可借实体册，不复用旧编号。 */
    void restock(String bookId, int count) {
        LibraryCopyRules.checkBatch(count);
        for (int index = 0; index < count; index++) add(bookId, LibraryCopyStatus.AVAILABLE);
    }
    /** 生成可用于条码标签的唯一实体册编号。 */
    private void add(String bookId, LibraryCopyStatus status) {
        String id = "CP-" + UUID.randomUUID();
        copies.put(id, new LibraryCopy(id, bookId, status));
    }
    /** 读取某馆藏的实体册，包括遗失册。 */
    List<LibraryCopy> list(String bookId) {
        List<LibraryCopy> result = new ArrayList<LibraryCopy>();
        for (LibraryCopy copy : copies.values()) if (copy.getBookId().equals(bookId)) result.add(copy);
        return result;
    }
    /** 在改动库存前确认确有可借实体册。 */
    boolean hasAvailable(String bookId) { return available(bookId) != null; }
    /** 查找可分配的实体册，不根据客户端指定状态分配。 */
    private LibraryCopy available(String bookId) {
        for (LibraryCopy copy : copies.values()) {
            if (copy.getBookId().equals(bookId) && copy.getStatus() == LibraryCopyStatus.AVAILABLE) return copy;
        }
        return null;
    }
    /** 在同一共享锁内分配一册，并保存真正的借阅时书名。 */
    void borrow(BorrowRecord record, String title) {
        LibraryCopy copy = available(record.getBookId());
        if (copy == null) throw new IllegalStateException("no available physical copy");
        LibraryLoanSnapshot snapshot = new LibraryLoanSnapshot(record, title, copy.getCopyId(), false);
        copies.put(copy.getCopyId(), copy.withStatus(LibraryCopyStatus.BORROWED));
        loans.put(record.getRecordId(), snapshot);
    }
    /** 校验借阅与实体册仍然一一对应。 */
    boolean canFinish(String recordId) {
        LibraryLoanSnapshot loan = loans.get(recordId);
        LibraryCopy copy = loan == null ? null : copies.get(loan.getCopyId());
        return copy != null && copy.getStatus() == LibraryCopyStatus.BORROWED;
    }
    /** 只改变对应实体册状态，名称快照和编号永久保留。 */
    void finish(String recordId, LibraryCopyStatus status) {
        if (!canFinish(recordId)) throw new IllegalStateException("copy and borrowing record are inconsistent");
        LibraryLoanSnapshot loan = loans.get(recordId);
        LibraryCopy copy = copies.get(loan.getCopyId());
        copies.put(copy.getCopyId(), copy.withStatus(status));
    }
    /** 以最新生命周期拼装历史；后续改名不会覆盖已保存的名称。 */
    List<LibraryLoanSnapshot> history(List<BorrowRecord> records, String userId) {
        List<LibraryLoanSnapshot> result = new ArrayList<LibraryLoanSnapshot>();
        for (BorrowRecord record : records) {
            if (userId != null && !record.getUserId().equals(userId)) continue;
            LibraryLoanSnapshot snapshot = loans.get(record.getRecordId());
            if (snapshot == null) throw new IllegalStateException("borrowing snapshot missing");
            result.add(snapshot.withRecord(record));
        }
        return result;
    }
}

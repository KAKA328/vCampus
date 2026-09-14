package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 共享仓库锁内的 V4 查询与元数据编辑，不独立维护库存。 */
final class InMemoryLibraryCatalogV4 implements LibraryCatalogV4 {
    /** 仓库的馆藏集合。 */
    private final Map<String, Book> books;
    /** 仓库的借阅集合。 */
    private final List<BorrowRecord> records;
    /** 实体册与历史快照存储。 */
    private final InMemoryLibraryCopies copies;
    /** 演示进程内的元数据编辑审计，不代替 Access 持久化审计。 */
    private final List<String> edits = new ArrayList<String>();
    /** 使用同一份状态，所有方法须由仓库同步入口调用。 */
    InMemoryLibraryCatalogV4(Map<String, Book> books, List<BorrowRecord> records, InMemoryLibraryCopies copies) {
        this.books = books; this.records = records; this.copies = copies;
    }
    /** 返回某馆藏的实体册快照。 */
    @Override public ServiceResult<List<LibraryCopy>> copies(String bookId) {
        if (bookId == null || bookId.trim().isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId required");
        if (!books.containsKey(bookId.trim())) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        return ServiceResult.ok(copies.list(bookId.trim()));
    }
    /** 返回授权范围的历史；权限在消息入口检查。 */
    @Override public ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId) {
        if (userId != null && userId.trim().isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId required");
        return ServiceResult.ok(copies.history(records, userId == null ? null : userId.trim()));
    }
    /** 在共享锁中校验原资料并更新新资料，保留最新库存并追加审计。 */
    @Override public ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement) {
        try { LibraryBookMetadata.validateEdit(operatorId, expected, replacement); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        Book current = books.get(expected.getBookId());
        if (current == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
        if (!LibraryBookMetadata.same(current, expected)) {
            return ServiceResult.failure(StatusCode.CONFLICT, "图书资料已被其他管理员修改，请重新打开编辑");
        }
        Book updated = LibraryBookMetadata.merge(replacement, current);
        if (!LibraryBookMetadata.same(current, updated)) {
            edits.add(LocalDateTime.now() + " operator=" + operatorId + " book=" + current.getBookId()
                    + "\nBEFORE\n" + LibraryBookMetadata.auditText(current) + "\nAFTER\n" + LibraryBookMetadata.auditText(updated));
            books.put(current.getBookId(), updated);
        }
        return ServiceResult.ok(updated);
    }
}

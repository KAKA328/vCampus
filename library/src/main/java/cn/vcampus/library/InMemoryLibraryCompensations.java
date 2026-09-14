package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongPredicate;

/** 共享仓库锁内的遗失账单及赔偿状态转换，不独立持有钱包余额。 */
final class InMemoryLibraryCompensations {

    /** 按图书编号索引的馆藏集合。 */
    private final Map<String, Book> books;
    /** 共享的借阅记录集合。 */
    private final List<BorrowRecord> records;
    /** 与借还业务共用的实体册账本。 */
    private final InMemoryLibraryCopies copies;
    /** 赔偿记录或赔偿服务。 */
    private final Map<String, LibraryCompensation> compensations =
            new LinkedHashMap<String, LibraryCompensation>();

    /** 共享馆藏与借阅集合；所有入口必须由仓库同步锁保护。 */
    InMemoryLibraryCompensations(Map<String, Book> books, List<BorrowRecord> records, InMemoryLibraryCopies copies) {
        this.books = books;
        this.records = records;
        this.copies = copies;
    }

    /** 登记遗失并按原价生成唯一赔偿账单，不直接扣读者余额。 */
    ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        if (blank(operatorId) || blank(recordId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "operatorId and recordId are required");
        }
        for (LibraryCompensation bill : compensations.values()) {
            if (bill.getRecordId().equals(recordId.trim())) return ServiceResult.ok(bill);
        }
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            if (!record.getRecordId().equals(recordId.trim())) continue;
            if (record.getStatus() != BorrowStatus.BORROWED) {
                return ServiceResult.failure(StatusCode.CONFLICT, "only an active loan can be declared lost");
            }
            Book book = books.get(record.getBookId());
            if (book == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            if (book.getTotalCopies() <= book.getAvailableCopies()) {
                return ServiceResult.failure(StatusCode.CONFLICT, "book inventory is inconsistent");
            }
            final long cents;
            try {
                cents = LibraryCompensation.originalPriceCents(book.getPrice());
            } catch (IllegalArgumentException invalidPrice) {
                return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidPrice.getMessage());
            }
            LibraryCompensation bill = new LibraryCompensation(UUID.randomUUID().toString(), record.getRecordId(),
                    record.getUserId(), book.getBookId(), book.getTitle(), cents, CompensationStatus.PENDING,
                    operatorId, LocalDateTime.now(), null);
            Book remaining = new Book(book.getBookId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                    book.getCategory(), book.getPublisher(), book.getPrice(), book.getTotalCopies() - 1,
                    book.getAvailableCopies(), book.getLocation());
            BorrowRecord lost = record.lost();
            if (!copies.canFinish(recordId.trim())) return ServiceResult.failure(StatusCode.CONFLICT, "copy state changed");
            copies.finish(recordId.trim(), LibraryCopyStatus.LOST);
            books.put(book.getBookId(), remaining);
            records.set(i, lost);
            compensations.put(bill.getCompensationId(), bill);
            return ServiceResult.ok(bill);
        }
        return ServiceResult.failure(StatusCode.NOT_FOUND, "borrowing record not found");
    }

    /** 结清本人的赔偿账单；重复付款不再次扣款。 */
    ServiceResult<LibraryCompensation> payCompensation(String userId, String compensationId,
            LongPredicate debit) {
        if (blank(userId) || blank(compensationId) || debit == null) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId and compensationId are required");
        }
        LibraryCompensation bill = compensations.get(compensationId.trim());
        if (bill == null || !bill.getUserId().equals(userId.trim())) {
            return ServiceResult.failure(StatusCode.NOT_FOUND, "compensation not found");
        }
        if (bill.getStatus() == CompensationStatus.PAID) return ServiceResult.ok(bill);
        for (int i = 0; i < records.size(); i++) {
            BorrowRecord record = records.get(i);
            if (!record.getRecordId().equals(bill.getRecordId())) continue;
            if (record.getStatus() != BorrowStatus.LOST || !record.getUserId().equals(userId.trim())) {
                return ServiceResult.failure(StatusCode.CONFLICT, "lost record state changed");
            }
            // Validate every new immutable value before the irreversible in-memory wallet mutation.
            BorrowRecord settled = record.compensated();
            LibraryCompensation paid = bill.paid(LocalDateTime.now());
            if (bill.getAmountCents() > 0 && !debit.test(bill.getAmountCents())) {
                return ServiceResult.failure(StatusCode.PAYMENT_REQUIRED, "insufficient campus wallet balance");
            }
            records.set(i, settled);
            compensations.put(bill.getCompensationId(), paid);
            return ServiceResult.ok(paid);
        }
        return ServiceResult.failure(StatusCode.CONFLICT, "lost borrowing record missing");
    }

    /** 读取本人或全部赔偿账单快照。 */
    List<LibraryCompensation> findCompensations(String userId) {
        List<LibraryCompensation> result = new ArrayList<LibraryCompensation>();
        for (LibraryCompensation bill : compensations.values()) {
            if (userId == null || bill.getUserId().equals(userId.trim())) result.add(bill);
        }
        return result;
    }

    /** 判断文本是否为空或仅含空白。 */
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

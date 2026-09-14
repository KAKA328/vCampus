package cn.vcampus.library;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable record for one borrowed copy. */
public final class BorrowRecord implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;

    /** 批次借阅编号。 */
    private final String orderId;
    /** 单笔借阅记录编号。 */
    private final String recordId;
    /** 由服务端会话确认的用户编号。 */
    private final String userId;
    /** 馆藏图书编号。 */
    private final String bookId;
    /** 借阅日期。 */
    private final LocalDate borrowDate;
    /** 应还日期。 */
    private final LocalDate dueDate;
    /** 实际归还日期；未归还时为空。 */
    private final LocalDate returnDate;
    /** 业务状态或操作提示。 */
    private final BorrowStatus status;

    /** 保存一册图书的借阅生命周期快照；借阅日期、归还日期和状态按传入值记录。 */
    public BorrowRecord(String orderId, String recordId, String userId, String bookId,
            LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, BorrowStatus status) {
        this.orderId = requireText(orderId, "orderId");
        this.recordId = requireText(recordId, "recordId");
        this.userId = requireText(userId, "userId");
        this.bookId = requireText(bookId, "bookId");
        this.borrowDate = Objects.requireNonNull(borrowDate, "borrowDate");
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.returnDate = returnDate;
        this.status = Objects.requireNonNull(status, "status");
        if (dueDate.isBefore(borrowDate)) throw new IllegalArgumentException("dueDate must not precede borrowDate");
        if (status == BorrowStatus.RETURNED && returnDate == null) {
            throw new IllegalArgumentException("returned record must have returnDate");
        }
        if (status != BorrowStatus.RETURNED && returnDate != null) {
            throw new IllegalArgumentException("active record must not have returnDate");
        }
    }

    /** 返回批次借阅编号。 */
    public String getOrderId() { return orderId; }
    /** 返回单笔借阅记录编号。 */
    public String getRecordId() { return recordId; }
    /** 返回由服务端会话确认的用户编号。 */
    public String getUserId() { return userId; }
    /** 返回馆藏图书编号。 */
    public String getBookId() { return bookId; }
    /** 返回借阅日期。 */
    public LocalDate getBorrowDate() { return borrowDate; }
    /** 返回应还日期。 */
    public LocalDate getDueDate() { return dueDate; }
    /** 返回实际归还日期；未归还时为空。 */
    public LocalDate getReturnDate() { return returnDate; }
    /** 返回业务状态或操作提示。 */
    public BorrowStatus getStatus() { return status; }
    /** 判断借阅记录是否已正常归还。 */
    public boolean isReturned() { return status == BorrowStatus.RETURNED; }

    /** 生成已归还的不可变记录，要求实际归还日期。 */
    public BorrowRecord returned(LocalDate date) {
        return new BorrowRecord(orderId, recordId, userId, bookId, borrowDate, dueDate,
                Objects.requireNonNull(date, "returnDate"), BorrowStatus.RETURNED);
    }

    /** 仅从在借状态生成遗失记录。 */
    public BorrowRecord lost() {
        if (status != BorrowStatus.BORROWED) throw new IllegalStateException("only borrowed records can be lost");
        return new BorrowRecord(orderId, recordId, userId, bookId, borrowDate, dueDate, null, BorrowStatus.LOST);
    }

    /** 仅从遗失状态生成已赔偿记录。 */
    public BorrowRecord compensated() {
        if (status != BorrowStatus.LOST) throw new IllegalStateException("only lost records can be compensated");
        return new BorrowRecord(orderId, recordId, userId, bookId, borrowDate, dueDate, null, BorrowStatus.COMPENSATED);
    }

    /** 比较业务快照的全部值字段。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BorrowRecord)) return false;
        BorrowRecord that = (BorrowRecord) other;
        return orderId.equals(that.orderId) && recordId.equals(that.recordId)
                && userId.equals(that.userId) && bookId.equals(that.bookId)
                && borrowDate.equals(that.borrowDate) && dueDate.equals(that.dueDate)
                && Objects.equals(returnDate, that.returnDate) && status == that.status;
    }

    /** 根据与相等性一致的字段计算哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(orderId, recordId, userId, bookId, borrowDate, dueDate, returnDate, status);
    }

    /** 校验并规范化必填文本。 */
    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

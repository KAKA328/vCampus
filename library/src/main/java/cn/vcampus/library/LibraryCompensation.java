package cn.vcampus.library;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable original-price snapshot. Introduced only in the explicit V3 protocol. */
public final class LibraryCompensation implements Serializable {
    /** 序列化兼容版本；本轮保持原值不变。 */
    private static final long serialVersionUID = 1L;
    /** 赔偿账单编号。 */
    private final String compensationId;
    /** 单笔借阅记录编号。 */
    private final String recordId;
    /** 由服务端会话确认的用户编号。 */
    private final String userId;
    /** 馆藏图书编号。 */
    private final String bookId;
    /** 登记遗失时固定保存的图书名称。 */
    private final String bookTitle;
    /** 锁定的原价赔偿金额，单位为整数分。 */
    private final long amountCents;
    /** 业务状态或操作提示。 */
    private final CompensationStatus status;
    /** 确认遗失的管理员编号。 */
    private final String createdBy;
    /** 赔偿账单创建时间。 */
    private final LocalDateTime createdAt;
    /** 赔偿结清时间；未结清时为空。 */
    private final LocalDateTime paidAt;

    /** 保存赔偿编号、原价分值、书名快照和付款生命周期字段。 */
    public LibraryCompensation(String compensationId, String recordId, String userId, String bookId,
            String bookTitle, long amountCents, CompensationStatus status, String createdBy,
            LocalDateTime createdAt, LocalDateTime paidAt) {
        this.compensationId = text(compensationId);
        this.recordId = text(recordId);
        this.userId = text(userId);
        this.bookId = text(bookId);
        this.bookTitle = text(bookTitle);
        if (amountCents < 0) throw new IllegalArgumentException("amountCents must not be negative");
        this.amountCents = amountCents;
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = text(createdBy);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if ((status == CompensationStatus.PAID) != (paidAt != null)) {
            throw new IllegalArgumentException("paidAt must match compensation status");
        }
        this.paidAt = paidAt;
    }

    /** Freeze the authoritative catalog price in integer cents, rejecting overflow. */
    public static long originalPriceCents(double price) {
        if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("invalid book price");
        try {
            return BigDecimal.valueOf(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("book price exceeds supported compensation amount", overflow);
        }
    }

    /** 生成已结清的赔偿快照，不改变原账单对象。 */
    public LibraryCompensation paid(LocalDateTime time) {
        if (status != CompensationStatus.PENDING) throw new IllegalStateException("bill already paid");
        return new LibraryCompensation(compensationId, recordId, userId, bookId, bookTitle,
                amountCents, CompensationStatus.PAID, createdBy, createdAt, Objects.requireNonNull(time, "paidAt"));
    }

    /** 返回赔偿账单编号。 */
    public String getCompensationId() { return compensationId; }
    /** 返回单笔借阅记录编号。 */
    public String getRecordId() { return recordId; }
    /** 返回由服务端会话确认的用户编号。 */
    public String getUserId() { return userId; }
    /** 返回馆藏图书编号。 */
    public String getBookId() { return bookId; }
    /** 返回登记遗失时固定保存的图书名称。 */
    public String getBookTitle() { return bookTitle; }
    /** 返回锁定的原价赔偿金额，单位为整数分。 */
    public long getAmountCents() { return amountCents; }
    /** 返回业务状态或操作提示。 */
    public CompensationStatus getStatus() { return status; }
    /** 返回确认遗失的管理员编号。 */
    public String getCreatedBy() { return createdBy; }
    /** 返回赔偿账单创建时间。 */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** 返回赔偿结清时间；未结清时为空。 */
    public LocalDateTime getPaidAt() { return paidAt; }
    /** 校验必填文本或规范化数据库可空文本。 */
    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("required text is blank");
        return value.trim();
    }
}

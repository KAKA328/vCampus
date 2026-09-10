package cn.vcampus.library;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Immutable original-price snapshot. Introduced only in the explicit V3 protocol. */
public final class LibraryCompensation implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String compensationId;
    private final String recordId;
    private final String userId;
    private final String bookId;
    private final String bookTitle;
    private final long amountCents;
    private final CompensationStatus status;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime paidAt;

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

    public LibraryCompensation paid(LocalDateTime time) {
        if (status != CompensationStatus.PENDING) throw new IllegalStateException("bill already paid");
        return new LibraryCompensation(compensationId, recordId, userId, bookId, bookTitle,
                amountCents, CompensationStatus.PAID, createdBy, createdAt, Objects.requireNonNull(time, "paidAt"));
    }

    public String getCompensationId() { return compensationId; }
    public String getRecordId() { return recordId; }
    public String getUserId() { return userId; }
    public String getBookId() { return bookId; }
    public String getBookTitle() { return bookTitle; }
    public long getAmountCents() { return amountCents; }
    public CompensationStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    private static String text(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("required text is blank");
        return value.trim();
    }
}

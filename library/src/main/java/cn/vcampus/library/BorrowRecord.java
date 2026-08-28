package cn.vcampus.library;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** One borrowing record of a book by a user. */
public final class BorrowRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String orderId;
    private final String recordId;
    private final String userId;
    private final String bookId;
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private final LocalDate returnDate;
    private final BorrowStatus status;

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
    }

    public String getOrderId() { return orderId; }
    public String getRecordId() { return recordId; }
    public String getUserId() { return userId; }
    public String getBookId() { return bookId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public BorrowStatus getStatus() { return status; }

    public boolean isReturned() { return status == BorrowStatus.RETURNED; }

    /** Returns a copy of this record marked as returned on the given date. */
    public BorrowRecord returned(LocalDate date) {
        return new BorrowRecord(orderId, recordId, userId, bookId, borrowDate, dueDate, date, BorrowStatus.RETURNED);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BorrowRecord)) return false;
        BorrowRecord that = (BorrowRecord) other;
        return orderId.equals(that.orderId) && recordId.equals(that.recordId) && userId.equals(that.userId)
                && bookId.equals(that.bookId) && borrowDate.equals(that.borrowDate) && dueDate.equals(that.dueDate)
                && Objects.equals(returnDate, that.returnDate) && status == that.status;
    }

    @Override public int hashCode() {
        return Objects.hash(orderId, recordId, userId, bookId, borrowDate, dueDate, returnDate, status);
    }
}

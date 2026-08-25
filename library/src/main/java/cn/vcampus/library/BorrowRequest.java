package cn.vcampus.library;

import java.io.Serializable;

/** Payload for borrow and return messages. */
public final class BorrowRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String studentId;
    private final String bookId;

    public BorrowRequest(String studentId, String bookId) {
        this.studentId = requireText(studentId, "studentId");
        this.bookId = requireText(bookId, "bookId");
    }

    public String getStudentId() { return studentId; }
    public String getBookId() { return bookId; }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

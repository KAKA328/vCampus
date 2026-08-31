package cn.vcampus.user;

import java.io.Serializable;

/** Per-row failure returned from a batch user import. */
public final class UserImportFailure implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int rowNumber;
    private final String userId;
    private final String message;

    public UserImportFailure(int rowNumber, String userId, String message) {
        if (rowNumber < 1) {
            throw new IllegalArgumentException("rowNumber must start at 1");
        }
        this.rowNumber = rowNumber;
        this.userId = userId == null ? "" : userId.trim();
        this.message = message == null || message.trim().isEmpty() ? "import failed" : message.trim();
    }

    public int getRowNumber() { return rowNumber; }
    public String getUserId() { return userId; }
    public String getMessage() { return message; }
    public String getReason() { return message; }
}

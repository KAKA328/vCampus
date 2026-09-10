package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryCompensationService;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.WalletMutation;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Loss inventory, borrowing status, bill, balance and ledger use one Access transaction. */
public final class AccessLibraryCompensationService implements LibraryCompensationService {
    private static final String COLUMNS = "compensation_id,record_id,user_id,book_id,book_title,amount_cents,"
            + "status,created_by,created_at,paid_at";
    private final Path databasePath;
    private final AccessLibraryRepository library;
    private final AccessWalletRepository wallet;
    private final ConnectionFactory connections;

    interface ConnectionFactory { Connection open() throws SQLException; }

    public AccessLibraryCompensationService(Path databasePath, AccessLibraryRepository library,
            AccessWalletRepository wallet) {
        this(databasePath, library, wallet, null);
    }

    AccessLibraryCompensationService(Path databasePath, AccessLibraryRepository library,
            AccessWalletRepository wallet, ConnectionFactory connections) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.library = Objects.requireNonNull(library, "library");
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        this.connections = connections;
    }

    @Override
    public ServiceResult<LibraryCompensation> declareLoss(String operatorId, String recordId) {
        if (blank(operatorId) || blank(recordId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "operatorId and recordId are required");
        }
        synchronized (library) {
            Connection connection = null;
            try {
                connection = open();
                connection.setAutoCommit(false);
                LibraryCompensation existing = findBill(connection, "record_id", recordId.trim());
                if (existing != null) { rollback(connection); return ServiceResult.ok(existing); }
                String userId;
                String bookId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT user_id,book_id,status FROM tblBorrowRecord WHERE record_id=?")) {
                    statement.setString(1, recordId.trim());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return fail(connection, StatusCode.NOT_FOUND, "borrowing record not found");
                        if (!"BORROWED".equals(result.getString("status"))) {
                            return fail(connection, StatusCode.CONFLICT, "only an active loan can be declared lost");
                        }
                        userId = result.getString("user_id");
                        bookId = result.getString("book_id");
                    }
                }
                String title;
                long cents;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT title,price FROM tblBook WHERE book_id=?")) {
                    statement.setString(1, bookId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) return fail(connection, StatusCode.NOT_FOUND, "book not found");
                        title = result.getString("title");
                        try {
                            cents = LibraryCompensation.originalPriceCents(result.getDouble("price"));
                        } catch (IllegalArgumentException invalidPrice) {
                            return fail(connection, StatusCode.BAD_REQUEST, invalidPrice.getMessage());
                        }
                    }
                }
                LibraryCompensation bill = new LibraryCompensation(UUID.randomUUID().toString(), recordId.trim(),
                        userId, bookId, title, cents, CompensationStatus.PENDING, operatorId.trim(), LocalDateTime.now(), null);
                if (updateBorrowStatus(connection, recordId.trim(), userId, "BORROWED", "LOST") != 1) {
                    return fail(connection, StatusCode.CONFLICT, "borrowing state changed");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE tblBook SET total_copies=total_copies-1 WHERE book_id=? AND total_copies>available_copies")) {
                    statement.setString(1, bookId);
                    if (statement.executeUpdate() != 1) {
                        return fail(connection, StatusCode.CONFLICT, "book inventory is inconsistent");
                    }
                }
                insertBill(connection, bill);
                connection.commit();
                return ServiceResult.ok(bill);
            } catch (SQLException | RuntimeException storageFailure) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to declare library loss");
            } finally { close(connection); }
        }
    }

    @Override
    public ServiceResult<LibraryCompensation> pay(String userId, String compensationId) {
        if (blank(userId) || blank(compensationId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId and compensationId are required");
        }
        synchronized (library) {
            synchronized (wallet) {
                Connection connection = null;
                try {
                    connection = open();
                    connection.setAutoCommit(false);
                    LibraryCompensation bill = findBill(connection, "compensation_id", compensationId.trim());
                    // Ownership is enforced here as well as at the token-authenticated endpoint.
                    if (bill == null || !bill.getUserId().equals(userId.trim())) {
                        return fail(connection, StatusCode.NOT_FOUND, "compensation not found");
                    }
                    if (bill.getStatus() == CompensationStatus.PAID) { rollback(connection); return ServiceResult.ok(bill); }
                    LibraryCompensation paid = bill.paid(LocalDateTime.now());
                    if (updateBorrowStatus(connection, bill.getRecordId(), userId.trim(), "LOST", "COMPENSATED") != 1) {
                        return fail(connection, StatusCode.CONFLICT, "lost borrowing record state changed");
                    }
                    if (bill.getAmountCents() > 0) {
                        WalletMutation mutation = wallet.debitInTransaction(connection, userId.trim(), bill.getAmountCents(),
                                WalletTransactionType.LIBRARY_LOSS, userId.trim(),
                                "library compensation " + bill.getCompensationId(), bill.getCompensationId());
                        if (!mutation.isApplied()) {
                            return fail(connection, StatusCode.PAYMENT_REQUIRED, "insufficient campus wallet balance");
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE tblLibraryCompensation SET status=?,paid_at=? WHERE compensation_id=? AND status=?")) {
                        statement.setString(1, "PAID");
                        statement.setTimestamp(2, Timestamp.valueOf(paid.getPaidAt()));
                        statement.setString(3, bill.getCompensationId());
                        statement.setString(4, "PENDING");
                        if (statement.executeUpdate() != 1) {
                            return fail(connection, StatusCode.CONFLICT, "compensation state changed");
                        }
                    }
                    connection.commit();
                    return ServiceResult.ok(paid);
                } catch (SQLException | RuntimeException storageFailure) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to settle library compensation");
                } finally { close(connection); }
            }
        }
    }

    @Override
    public ServiceResult<List<LibraryCompensation>> history(String userId) {
        if (userId != null && blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId is blank");
        synchronized (library) {
            String sql = "SELECT " + COLUMNS + " FROM tblLibraryCompensation"
                    + (userId == null ? "" : " WHERE user_id=?") + " ORDER BY created_at,compensation_id";
            try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
                if (userId != null) statement.setString(1, userId.trim());
                try (ResultSet result = statement.executeQuery()) {
                    List<LibraryCompensation> bills = new ArrayList<LibraryCompensation>();
                    while (result.next()) bills.add(readBill(result));
                    return ServiceResult.ok(bills);
                }
            } catch (SQLException | RuntimeException storageFailure) {
                return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read library compensations");
            }
        }
    }

    @Override
    public ServiceResult<Long> balance(String userId) {
        if (blank(userId)) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId is required");
        try {
            synchronized (wallet) {
                BankAccount account = wallet.findByUserId(userId.trim());
                return ServiceResult.ok(account == null ? 0L : account.getBalanceCents());
            }
        } catch (IllegalStateException storageFailure) {
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to read campus wallet");
        }
    }

    private static int updateBorrowStatus(Connection connection, String recordId, String userId,
            String oldStatus, String newStatus) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblBorrowRecord SET status=? WHERE record_id=? AND user_id=? AND status=?")) {
            statement.setString(1, newStatus);
            statement.setString(2, recordId);
            statement.setString(3, userId);
            statement.setString(4, oldStatus);
            return statement.executeUpdate();
        }
    }

    private static LibraryCompensation findBill(Connection connection, String key, String value) throws SQLException {
        // key is an internal constant, never a request parameter.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM tblLibraryCompensation WHERE " + key + "=?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? readBill(result) : null; }
        }
    }

    private static LibraryCompensation readBill(ResultSet result) throws SQLException {
        Timestamp paid = result.getTimestamp("paid_at");
        return new LibraryCompensation(result.getString("compensation_id"), result.getString("record_id"),
                result.getString("user_id"), result.getString("book_id"), result.getString("book_title"),
                result.getLong("amount_cents"), CompensationStatus.valueOf(result.getString("status")),
                result.getString("created_by"), result.getTimestamp("created_at").toLocalDateTime(),
                paid == null ? null : paid.toLocalDateTime());
    }

    private static void insertBill(Connection connection, LibraryCompensation bill) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblLibraryCompensation(" + COLUMNS + ") VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, bill.getCompensationId());
            statement.setString(2, bill.getRecordId());
            statement.setString(3, bill.getUserId());
            statement.setString(4, bill.getBookId());
            statement.setString(5, bill.getBookTitle());
            statement.setLong(6, bill.getAmountCents());
            statement.setString(7, bill.getStatus().name());
            statement.setString(8, bill.getCreatedBy());
            statement.setTimestamp(9, Timestamp.valueOf(bill.getCreatedAt()));
            statement.setNull(10, Types.TIMESTAMP);
            statement.executeUpdate();
        }
    }

    private Connection open() throws SQLException {
        if (connections != null) return connections.open();
        try { Class.forName("net.ucanaccess.jdbc.UcanaccessDriver"); }
        catch (ClassNotFoundException missing) { throw new SQLException("UCanAccess driver missing", missing); }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath + ";immediatelyReleaseResources=true");
    }

    private static <T> ServiceResult<T> fail(Connection connection, StatusCode status, String message) {
        rollback(connection);
        return ServiceResult.failure(status, message);
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static void rollback(Connection connection) {
        if (connection != null) try { connection.rollback(); } catch (SQLException ignored) { }
    }
    private static void close(Connection connection) {
        if (connection != null) try { connection.close(); } catch (SQLException ignored) { }
    }
}

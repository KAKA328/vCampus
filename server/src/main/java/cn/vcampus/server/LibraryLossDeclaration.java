package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryCopyStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import static cn.vcampus.server.LibraryCompensationSql.*;

/** 登记遗失的完整 Access 事务；调用方必须持有共享图书仓库锁。 */
final class LibraryLossDeclaration {
    /** 在同一事务内登记遗失、减少馆藏总量并创建原价账单；失败全部回滚。 */
    static ServiceResult<LibraryCompensation> execute(
            AccessLibraryCompensationService.ConnectionFactory connections, String operatorId, String recordId) {
        if (blank(operatorId) || blank(recordId)) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "operatorId and recordId are required");
        }
        Connection connection = null;
        try {
            connection = connections.open();
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
            boolean trackCopies = LibraryCopySchema.available(connection);
            if (trackCopies) LibraryCopySchema.initializeBook(connection, bookId);
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
            if (trackCopies) LibraryCopySql.finish(connection, recordId.trim(), LibraryCopyStatus.LOST);
            connection.commit();
            return ServiceResult.ok(bill);
        } catch (SQLException | RuntimeException storageFailure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "failed to declare library loss");
        } finally { close(connection); }
    }
}

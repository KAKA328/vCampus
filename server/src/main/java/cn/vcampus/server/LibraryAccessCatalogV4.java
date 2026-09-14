package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryBookMetadata;
import cn.vcampus.library.LibraryCopy;
import cn.vcampus.library.LibraryLoanSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static cn.vcampus.server.LibraryAccessSql.*;

/** Access 的 V4 查询及资料编辑；调用方持有共享图书仓库锁。 */
final class LibraryAccessCatalogV4 {
    /** 完成回填后读取实体册；未迁移的数据库明确提示，不创建空的假结果。 */
    static ServiceResult<List<LibraryCopy>> copies(AccessLibraryRepository repository, String bookId) {
        if (bookId == null || bookId.trim().isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId required");
        return transaction(repository, connection -> {
            LibraryCopySchema.require(connection);
            if (findBook(connection, bookId.trim()) == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            LibraryCopySchema.initializeBook(connection, bookId.trim());
            return ServiceResult.ok(LibraryCopySql.list(connection, bookId.trim()));
        });
    }
    /** 回填旧记录后读取历史，新增借阅的历史始终保留借阅时名称。 */
    static ServiceResult<List<LibraryLoanSnapshot>> history(AccessLibraryRepository repository, String userId) {
        if (userId != null && userId.trim().isEmpty()) return ServiceResult.failure(StatusCode.BAD_REQUEST, "userId required");
        return transaction(repository, connection -> {
            LibraryCopySchema.initializeAll(connection);
            return ServiceResult.ok(LibraryCopySql.history(connection, userId == null ? null : userId.trim()));
        });
    }
    /** 校验旧值、修改元数据并追加审计；三者在同一连接事务内。 */
    static ServiceResult<Book> update(AccessLibraryRepository repository, String operatorId, Book expected, Book replacement) {
        try { LibraryBookMetadata.validateEdit(operatorId, expected, replacement); }
        catch (IllegalArgumentException | NullPointerException invalid) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, invalid.getMessage());
        }
        return transaction(repository, connection -> {
            LibraryCopySchema.require(connection);
            Book current = findBook(connection, expected.getBookId());
            if (current == null) return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            if (!LibraryBookMetadata.same(current, expected)) {
                return ServiceResult.failure(StatusCode.CONFLICT, "图书资料已被其他管理员修改，请重新打开编辑");
            }
            // 必须先保存旧记录名称，再允许改名；不能用修改后的名字回填旧借阅。
            LibraryCopySchema.initializeBook(connection, current.getBookId());
            Book updated = LibraryBookMetadata.merge(replacement, current);
            if (!LibraryBookMetadata.same(current, updated)) {
                writeMetadata(connection, updated);
                writeAudit(connection, operatorId, current, updated);
            }
            return ServiceResult.ok(updated);
        });
    }
    /** 只修改资料列，不在 UPDATE 中包含库存或图书号。 */
    private static void writeMetadata(Connection connection, Book book) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE tblBook SET title=?,author=?,isbn=?,category=?,publisher=?,price=?,location=? WHERE book_id=?")) {
            statement.setString(1, book.getTitle()); statement.setString(2, book.getAuthor());
            statement.setString(3, book.getIsbn()); statement.setString(4, book.getCategory());
            statement.setString(5, book.getPublisher()); statement.setDouble(6, book.getPrice());
            statement.setString(7, book.getLocation()); statement.setString(8, book.getBookId());
            if (statement.executeUpdate() != 1) throw new SQLException("book changed");
        }
    }
    /** 保存操作者及完整可编辑资料的前后值，审计失败导致资料更新一并回滚。 */
    private static void writeAudit(Connection connection, String operator, Book before, Book after) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblLibraryBookEdit(edit_id,book_id,operator_id,edited_at,before_details,after_details) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1, UUID.randomUUID().toString()); statement.setString(2, before.getBookId());
            statement.setString(3, operator.trim()); statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(5, LibraryBookMetadata.auditText(before)); statement.setString(6, LibraryBookMetadata.auditText(after));
            statement.executeUpdate();
        }
    }
    /** 同步锁由仓库入口持有；仅成功业务结果提交，其他路径全部回滚。 */
    private static <T> ServiceResult<T> transaction(AccessLibraryRepository repository, Work<T> action) {
        Connection connection = null;
        try {
            connection = repository.open(); connection.setAutoCommit(false);
            ServiceResult<T> result = action.run(connection);
            if (result.getStatus() == StatusCode.OK) connection.commit(); else rollback(connection);
            return result;
        } catch (SQLException | RuntimeException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "图书馆 V4 操作失败；请核对数据库迁移及库存一致性");
        } finally { close(connection); }
    }
    /** 在调用方连接中执行的事务内容。 */
    private interface Work<T> {
        /** 返回业务结果或抛出错误，由统一事务边界处理。 */
        ServiceResult<T> run(Connection connection) throws SQLException;
    }
}

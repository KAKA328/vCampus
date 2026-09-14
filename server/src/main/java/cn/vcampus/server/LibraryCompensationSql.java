package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;

import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

/** 赔偿账单 SQL 和行映射；使用调用者的连接，绝不独立提交或扣款。 */
final class LibraryCompensationSql {
    /** 回滚当前事务并保留明确业务失败码。 */
    static <T> ServiceResult<T> fail(Connection connection, StatusCode status, String message) {
        rollback(connection);
        return ServiceResult.failure(status, message);
    }

    /** 检查业务编号是否为空。 */
    static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    /** 失败路径尽力回滚，不能覆盖原始业务错误。 */
    static void rollback(Connection connection) {
        if (connection != null) try { connection.rollback(); } catch (SQLException ignored) { }
    }

    /** 关闭调用方的短生命周期连接。 */
    static void close(Connection connection) {
        if (connection != null) try { connection.close(); } catch (SQLException ignored) { }
    }

    /** 固定 SQL 查询列；不接受客户端提供的列名。 */
    static final String COLUMNS = "compensation_id,record_id,user_id,book_id,book_title,amount_cents,"
            + "status,created_by,created_at,paid_at";

    /** 按原状态及记录归属条件更新借阅状态。 */
    static int updateBorrowStatus(Connection connection, String recordId, String userId,
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

    /** 按内部固定列名与绑定参数读取赔偿单。 */
    static LibraryCompensation findBill(Connection connection, String key, String value) throws SQLException {
        // key is an internal constant, never a request parameter.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM tblLibraryCompensation WHERE " + key + "=?")) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? readBill(result) : null; }
        }
    }

    /** 将数据库行映射为带原价与书名快照的赔偿单。 */
    static LibraryCompensation readBill(ResultSet result) throws SQLException {
        Timestamp paid = result.getTimestamp("paid_at");
        return new LibraryCompensation(result.getString("compensation_id"), result.getString("record_id"),
                result.getString("user_id"), result.getString("book_id"), result.getString("book_title"),
                result.getLong("amount_cents"), CompensationStatus.valueOf(result.getString("status")),
                result.getString("created_by"), result.getTimestamp("created_at").toLocalDateTime(),
                paid == null ? null : paid.toLocalDateTime());
    }

    /** 在当前事务中写入唯一赔偿账单，不独立提交。 */
    static void insertBill(Connection connection, LibraryCompensation bill) throws SQLException {
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
}

package cn.vcampus.server;

import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** 图书馆 Access 行映射和单条 SQL 操作；事务及共享锁由仓库统一持有。 */
final class LibraryAccessSql {
    /** 在借阅事务及共享仓库锁内统计当前用户的全部在借记录。 */
    static long activeBorrowCount(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblBorrowRecord WHERE user_id=? AND status=?")) {
            statement.setString(1, userId);
            statement.setString(2, BorrowStatus.BORROWED.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("active borrow count unavailable");
                return result.getLong(1);
            }
        }
    }

    /** 根据图书编号读取馆藏快照。 */
    static Book findBook(Connection connection, String bookId) throws SQLException {
        String sql = "SELECT book_id,title,author,isbn,category,publisher,price,total_copies,"
                + "available_copies,location FROM tblBook WHERE book_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readBook(results) : null;
            }
        }
    }

    /** 判断同一用户是否已借阅该图书且尚未归还。 */
    static boolean hasActiveBorrow(Connection connection, String userId, String bookId) throws SQLException {
        String sql = "SELECT record_id FROM tblBorrowRecord WHERE user_id=? AND book_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, bookId);
            statement.setString(3, BorrowStatus.BORROWED.name());
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    /** 按用户和记录编号读取仍在借的记录。 */
    static BorrowRecord findActiveRecord(Connection connection, String userId, String recordId)
            throws SQLException {
        String sql = "SELECT order_id,record_id,user_id,book_id,borrow_date,due_date,return_date,status "
                + "FROM tblBorrowRecord WHERE user_id=? AND record_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, recordId);
            statement.setString(3, BorrowStatus.BORROWED.name());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readBorrowRecord(results) : null;
            }
        }
    }

    /** 条件扣减可借库存，库存不足时不更新。 */
    static boolean decrementAvailable(Connection connection, String bookId) throws SQLException {
        String sql = "UPDATE tblBook SET available_copies=available_copies-1 "
                + "WHERE book_id=? AND available_copies>0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            return statement.executeUpdate() == 1;
        }
    }

    /** 条件恢复可借库存，不允许超过总册数。 */
    static boolean incrementAvailable(Connection connection, String bookId) throws SQLException {
        String sql = "UPDATE tblBook SET available_copies=available_copies+1 "
                + "WHERE book_id=? AND available_copies<total_copies";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            return statement.executeUpdate() == 1;
        }
    }

    /** 在调用方事务内插入一条借阅记录。 */
    static void insertBorrowRecord(Connection connection, BorrowRecord record) throws SQLException {
        String sql = "INSERT INTO tblBorrowRecord(order_id,record_id,user_id,book_id,borrow_date,due_date,"
                + "return_date,status) VALUES(?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.getOrderId());
            statement.setString(2, record.getRecordId());
            statement.setString(3, record.getUserId());
            statement.setString(4, record.getBookId());
            statement.setDate(5, Date.valueOf(record.getBorrowDate()));
            statement.setDate(6, Date.valueOf(record.getDueDate()));
            statement.setDate(7, null);
            statement.setString(8, record.getStatus().name());
            statement.executeUpdate();
        }
    }

    /** 仅将仍在借的记录更新为已归还，避免重复处理。 */
    static boolean markReturned(Connection connection, String recordId, LocalDate returnDate) throws SQLException {
        String sql = "UPDATE tblBorrowRecord SET return_date=?,status=? WHERE record_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(returnDate));
            statement.setString(2, BorrowStatus.RETURNED.name());
            statement.setString(3, recordId);
            statement.setString(4, BorrowStatus.BORROWED.name());
            return statement.executeUpdate() == 1;
        }
    }

    /** 将数据库行映射为经过校验的馆藏快照。 */
    static Book readBook(ResultSet results) throws SQLException {
        return new Book(results.getString("book_id"), results.getString("title"),
                results.getString("author"), text(results, "isbn"), text(results, "category"),
                text(results, "publisher"), results.getDouble("price"), results.getInt("total_copies"),
                results.getInt("available_copies"), text(results, "location"));
    }

    /** 将数据库行映射为不可变借阅记录。 */
    static BorrowRecord readBorrowRecord(ResultSet results) throws SQLException {
        Date returned = results.getDate("return_date");
        return new BorrowRecord(results.getString("order_id"), results.getString("record_id"),
                results.getString("user_id"), results.getString("book_id"),
                results.getDate("borrow_date").toLocalDate(), results.getDate("due_date").toLocalDate(),
                returned == null ? null : returned.toLocalDate(),
                BorrowStatus.valueOf(results.getString("status")));
    }

    /** 绑定馆藏插入参数，不拼接客户端文本到 SQL。 */
    static void writeBook(PreparedStatement statement, Book book) throws SQLException {
        statement.setString(1, book.getBookId());
        statement.setString(2, book.getTitle());
        statement.setString(3, book.getAuthor());
        statement.setString(4, book.getIsbn());
        statement.setString(5, book.getCategory());
        statement.setString(6, book.getPublisher());
        statement.setDouble(7, book.getPrice());
        statement.setInt(8, book.getTotalCopies());
        statement.setInt(9, book.getAvailableCopies());
        statement.setString(10, book.getLocation());
    }

    /** 失败路径尽力回滚，不覆盖原始业务错误。 */
    static void rollback(Connection connection) {
        if (connection == null) return;
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    /** 关闭短生命周期连接，不停止服务器。 */
    static void close(Connection connection) {
        if (connection == null) return;
        try { connection.close(); } catch (SQLException ignored) { }
    }

    /** 校验必填文本或规范化数据库可空文本。 */
    static String text(ResultSet results, String column) throws SQLException {
        String value = results.getString(column);
        return value == null ? "" : value;
    }

    /** 为新业务记录生成带前缀的唯一编号。 */
    static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString(); }

    /** 去除首尾空白并使用稳定区域规则转为小写。 */
    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 按规范化后的文本匹配检索关键词。 */
    static boolean contains(String value, String key) { return normalize(value).contains(key); }
}

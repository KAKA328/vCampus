package cn.vcampus.server;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.LibraryCopy;
import cn.vcampus.library.LibraryCopyRules;
import cn.vcampus.library.LibraryCopyStatus;
import cn.vcampus.library.LibraryLoanSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 实体册和借阅快照的单连接 SQL；不自行提交或关闭调用者的事务。 */
final class LibraryCopySql {
    /** 批量创建有界数量的实体册，所有编号独立且不复用。 */
    static void addCopies(Connection connection, String bookId, int count, LibraryCopyStatus status) throws SQLException {
        LibraryCopyRules.checkBatch(count);
        for (int index = 0; index < count; index++) addCopy(connection, bookId, status);
    }
    /** 创建一册并返回唯一编号。 */
    static String addCopy(Connection connection, String bookId, LibraryCopyStatus status) throws SQLException {
        String id = "CP-" + UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblBookCopy(copy_id,book_id,status) VALUES(?,?,?)")) {
            statement.setString(1, id); statement.setString(2, bookId); statement.setString(3, status.name());
            statement.executeUpdate();
        }
        return id;
    }
    /** 原子预占可借实体册，失败由外层回滚整批借阅。 */
    static String reserve(Connection connection, String bookId) throws SQLException {
        String id = null;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT copy_id FROM tblBookCopy WHERE book_id=? AND status=? ORDER BY copy_id")) {
            query.setString(1, bookId); query.setString(2, LibraryCopyStatus.AVAILABLE.name());
            try (ResultSet rows = query.executeQuery()) { if (rows.next()) id = rows.getString(1); }
        }
        if (id == null || !change(connection, id, LibraryCopyStatus.AVAILABLE, LibraryCopyStatus.BORROWED)) {
            throw new SQLException("no available physical copy");
        }
        return id;
    }
    /** 保存不可变名称与实体册关系，不以编辑后的馆藏名称覆盖。 */
    static void insertSnapshot(Connection connection, String recordId, String title, String copyId, boolean backfill)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblLibraryLoanSnapshot(record_id,book_title,copy_id,historical_backfill) VALUES(?,?,?,?)")) {
            statement.setString(1, recordId); statement.setString(2, title); statement.setString(3, copyId);
            statement.setBoolean(4, backfill); statement.executeUpdate();
        }
    }
    /** 归还或遗失时只转换该次借阅绑定的实体册。 */
    static void finish(Connection connection, String recordId, LibraryCopyStatus status) throws SQLException {
        String id = null;
        try (PreparedStatement query = connection.prepareStatement("SELECT copy_id FROM tblLibraryLoanSnapshot WHERE record_id=?")) {
            query.setString(1, recordId);
            try (ResultSet rows = query.executeQuery()) { if (rows.next()) id = rows.getString(1); }
        }
        if (id == null || !change(connection, id, LibraryCopyStatus.BORROWED, status)) {
            throw new SQLException("copy and borrowing state differ");
        }
    }
    /** 用旧状态作为更新条件，不能重复归还或复活遗失册。 */
    private static boolean change(Connection connection, String id, LibraryCopyStatus before, LibraryCopyStatus after)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE tblBookCopy SET status=? WHERE copy_id=? AND status=?")) {
            statement.setString(1, after.name()); statement.setString(2, id); statement.setString(3, before.name());
            return statement.executeUpdate() == 1;
        }
    }
    /** 查询馆藏所有实体册，保留已遗失编号用于追溯。 */
    static List<LibraryCopy> list(Connection connection, String bookId) throws SQLException {
        List<LibraryCopy> result = new ArrayList<LibraryCopy>();
        try (PreparedStatement query = connection.prepareStatement("SELECT copy_id,book_id,status FROM tblBookCopy WHERE book_id=? ORDER BY copy_id")) {
            query.setString(1, bookId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(new LibraryCopy(rows.getString(1), rows.getString(2), LibraryCopyStatus.valueOf(rows.getString(3))));
            }
        }
        return result;
    }
    /** 在同一连接读取授权范围的生命周期与名称快照。 */
    static List<LibraryLoanSnapshot> history(Connection connection, String userId) throws SQLException {
        List<LibraryLoanSnapshot> result = new ArrayList<LibraryLoanSnapshot>();
        String sql = "SELECT r.*,s.book_title,s.copy_id,s.historical_backfill FROM tblBorrowRecord r"
                + " INNER JOIN tblLibraryLoanSnapshot s ON r.record_id=s.record_id"
                + (userId == null ? "" : " WHERE r.user_id=?") + " ORDER BY r.borrow_date DESC,r.record_id";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            if (userId != null) query.setString(1, userId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    BorrowRecord record = LibraryAccessSql.readBorrowRecord(rows);
                    result.add(new LibraryLoanSnapshot(record, rows.getString("book_title"),
                            rows.getString("copy_id"), rows.getBoolean("historical_backfill")));
                }
            }
        }
        return result;
    }
}

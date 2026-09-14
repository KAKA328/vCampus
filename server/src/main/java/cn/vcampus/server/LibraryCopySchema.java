package cn.vcampus.server;

import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryCopyRules;
import cn.vcampus.library.LibraryCopyStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** V4 表检测及旧库存回填；DDL 仅由显式离线迁移创建，业务入口不偷偷改表。 */
final class LibraryCopySchema {
    /** 必须成组存在的扩展表名。 */
    private static final Set<String> TABLES = new HashSet<String>(Arrays.asList(
            "tblbookcopy", "tbllibraryloansnapshot", "tbllibrarycopycatalog", "tbllibrarybookedit"));
    /** 检查完整扩展；旧库可继续原 V2/V3 业务，部分迁移的库拒绝写入。 */
    static boolean available(Connection connection) throws SQLException {
        int found = 0;
        try (ResultSet tables = connection.getMetaData().getTables(null, null, null, new String[] {"TABLE"})) {
            while (tables.next()) if (TABLES.contains(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT))) found++;
        }
        if (found != 0 && found != TABLES.size()) throw new SQLException("library V4 schema incomplete");
        return found == TABLES.size();
    }
    /** V4 入口不能把未迁移旧库伪装成已支持实体册。 */
    static void require(Connection connection) throws SQLException {
        if (!available(connection)) throw new SQLException("请先离线执行图书馆 V4 数据库迁移");
    }
    /** 为全部馆藏回填一次；调用者在共享锁和一个事务内提交。 */
    static void initializeAll(Connection connection) throws SQLException {
        require(connection);
        List<String> ids = new ArrayList<String>();
        try (PreparedStatement query = connection.prepareStatement("SELECT book_id FROM tblBook");
                ResultSet rows = query.executeQuery()) {
            while (rows.next()) ids.add(rows.getString(1));
        }
        for (String id : ids) initializeBook(connection, id);
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM tblBorrowRecord r LEFT JOIN tblLibraryLoanSnapshot s ON r.record_id=s.record_id"
                + " WHERE s.record_id IS NULL"); ResultSet rows = query.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0) throw new SQLException("存在孤立借阅记录，停止回填并请管理员核对");
        }
    }
    /** 按既有库存及借阅初始化实体册，不改变旧库存、原始借阅或赔偿单。 */
    static void initializeBook(Connection connection, String bookId) throws SQLException {
        if (initialized(connection, bookId)) return;
        Book book = LibraryAccessSql.findBook(connection, bookId);
        if (book == null) throw new SQLException("book not found during copy migration");
        LibraryCopyRules.checkBatch(book.getTotalCopies());
        List<BorrowRecord> records = new ArrayList<BorrowRecord>();
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM tblBorrowRecord WHERE book_id=?")) {
            query.setString(1, bookId);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) records.add(LibraryAccessSql.readBorrowRecord(rows)); }
        }
        long active = records.stream().filter(record -> record.getStatus() == BorrowStatus.BORROWED).count();
        long unexplained = (long) book.getTotalCopies() - book.getAvailableCopies() - active;
        if (unexplained < 0) throw new SQLException("在借记录超过不可借库存，迁移未修改原始数量: " + bookId);
        LibraryCopySql.addCopies(connection, bookId, book.getAvailableCopies(), LibraryCopyStatus.AVAILABLE);
        LibraryCopySql.addCopies(connection, bookId, (int) unexplained, LibraryCopyStatus.UNAVAILABLE);
        for (BorrowRecord record : records) {
            String copyId = null;
            if (record.getStatus() != BorrowStatus.RETURNED) {
                copyId = LibraryCopySql.addCopy(connection, bookId,
                        record.getStatus() == BorrowStatus.BORROWED ? LibraryCopyStatus.BORROWED : LibraryCopyStatus.LOST);
            }
            LibraryCopySql.insertSnapshot(connection, record.getRecordId(), book.getTitle(), copyId, true);
        }
        try (PreparedStatement mark = connection.prepareStatement("INSERT INTO tblLibraryCopyCatalog(book_id) VALUES(?)")) {
            mark.setString(1, bookId); mark.executeUpdate();
        }
    }
    /** 空库存也用显式标记记录已完成回填，避免重复初始化。 */
    private static boolean initialized(Connection connection, String bookId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT book_id FROM tblLibraryCopyCatalog WHERE book_id=?")) {
            query.setString(1, bookId);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }
}

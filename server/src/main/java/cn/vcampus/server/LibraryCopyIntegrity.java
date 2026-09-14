package cn.vcampus.server;

import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryCopy;
import cn.vcampus.library.LibraryCopyStatus;
import cn.vcampus.library.LibraryLoanSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 迁移及验收时核对副本、汇总库存和仍在借记录，不自动修正不一致数据。 */
final class LibraryCopyIntegrity {
    /** 不一致时抛错，让迁移事务回滚，保留原数据库供核对。 */
    static void verify(Connection connection) throws SQLException {
        noOrphans(connection, "tblBookCopy c LEFT JOIN tblBook b ON c.book_id=b.book_id", "b.book_id IS NULL");
        noOrphans(connection, "tblBorrowRecord r LEFT JOIN tblLibraryLoanSnapshot s ON r.record_id=s.record_id", "s.record_id IS NULL");
        noOrphans(connection, "tblLibraryLoanSnapshot s LEFT JOIN tblBorrowRecord r ON s.record_id=r.record_id", "r.record_id IS NULL");
        noOrphans(connection, "tblLibraryCopyCatalog c LEFT JOIN tblBook b ON c.book_id=b.book_id", "b.book_id IS NULL");
        noOrphans(connection, "tblLibraryBookEdit e LEFT JOIN tblBook b ON e.book_id=b.book_id", "b.book_id IS NULL");
        Map<String, LibraryCopy> copies = new HashMap<String, LibraryCopy>();
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM tblBook"); ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                Book book = LibraryAccessSql.readBook(rows);
                int total = 0, available = 0;
                for (LibraryCopy copy : LibraryCopySql.list(connection, book.getBookId())) {
                    copies.put(copy.getCopyId(), copy);
                    if (copy.getStatus() != LibraryCopyStatus.LOST) total++;
                    if (copy.getStatus() == LibraryCopyStatus.AVAILABLE) available++;
                }
                if (total != book.getTotalCopies() || available != book.getAvailableCopies()) {
                    throw new SQLException("实体册与库存汇总不一致: " + book.getBookId());
                }
            }
        }
        Set<String> activeCopies = new HashSet<String>();
        for (LibraryLoanSnapshot loan : LibraryCopySql.history(connection, null)) {
            LibraryCopy copy = copies.get(loan.getCopyId());
            if (loan.getCopyId() != null && (copy == null || !copy.getBookId().equals(loan.getRecord().getBookId()))) {
                throw new SQLException("借阅绑定了错误馆藏的实体册");
            }
            if (loan.getRecord().getStatus() == BorrowStatus.BORROWED) {
                if (copy == null || copy.getStatus() != LibraryCopyStatus.BORROWED || !activeCopies.add(copy.getCopyId())) {
                    throw new SQLException("在借记录与实体册不是一一对应");
                }
            }
            if (loan.getRecord().getStatus() == BorrowStatus.LOST || loan.getRecord().getStatus() == BorrowStatus.COMPENSATED) {
                if (copy == null || copy.getStatus() != LibraryCopyStatus.LOST) throw new SQLException("遗失实体册状态异常");
            }
        }
        for (LibraryCopy copy : copies.values()) {
            if (copy.getStatus() == LibraryCopyStatus.BORROWED && !activeCopies.contains(copy.getCopyId())) {
                throw new SQLException("借出实体册缺少在借记录");
            }
        }
    }
    /** 仅由固定内部 SQL 调用，核对关联两端都存在，不删除孤立数据。 */
    private static void noOrphans(Connection connection, String tables, String missing) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM " + tables + " WHERE " + missing);
                ResultSet rows = query.executeQuery()) {
            if (!rows.next() || rows.getLong(1) != 0) throw new SQLException("图书馆扩展数据存在孤立关联，请停止升级并核对");
        }
    }
}

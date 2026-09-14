package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryCopyRules;
import cn.vcampus.library.LibraryCopyStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static cn.vcampus.server.LibraryAccessSql.*;

/** 馆藏查询、新增和补库存；写操作由调用仓库持有同一同步锁。 */
final class LibraryAccessCatalog {
    /** 按关键词和可选分类查询馆藏，空条件表示不限制该项。 */
    static List<Book> search(AccessLibraryRepository repository, String keyword, String category) {
        String key = normalize(keyword);
        String categoryKey = category == null ? null : normalize(category);
        List<Book> matches = new ArrayList<Book>();
        String sql = "SELECT book_id,title,author,isbn,category,publisher,price,total_copies,"
                + "available_copies,location FROM tblBook ORDER BY book_id";
        try (Connection connection = repository.open();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                Book book = readBook(results);
                if (categoryKey != null && !normalize(book.getCategory()).equals(categoryKey)) continue;
                if (key.isEmpty() || contains(book.getBookId(), key) || contains(book.getTitle(), key)
                        || contains(book.getAuthor(), key) || contains(book.getIsbn(), key)
                        || contains(book.getCategory(), key)) matches.add(book);
            }
            return matches;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to search library catalog", failure);
        }
    }

    /** 新增馆藏；价格允许为零，不覆盖已有相同编号的图书。 */
    static boolean addBook(AccessLibraryRepository repository, Book book) {
        if (book == null || repository.findBook(book.getBookId()) != null) return false;
        LibraryCopyRules.checkBatch(book.getTotalCopies());
        String sql = "INSERT INTO tblBook(book_id,title,author,isbn,category,publisher,price,total_copies,"
                + "available_copies,location) VALUES(?,?,?,?,?,?,?,?,?,?)";
        Connection connection = null;
        try {
            connection = repository.open();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                writeBook(statement, book);
                if (statement.executeUpdate() != 1) throw new SQLException("book insert failed");
            }
            if (LibraryCopySchema.available(connection)) LibraryCopySchema.initializeBook(connection, book.getBookId());
            connection.commit();
            return true;
        } catch (SQLException | RuntimeException failure) {
            rollback(connection);
            throw new IllegalStateException("failed to add book", failure);
        } finally { close(connection); }
    }

    /** 原子增加已有馆藏的总量和可借量，保持借出数量不变。 */
    static ServiceResult<Book> restock(AccessLibraryRepository repository, String bookId, int copies) {
        if (bookId == null || bookId.trim().isEmpty() || copies <= 0) {
            return ServiceResult.failure(StatusCode.BAD_REQUEST, "bookId and positive copies are required");
        }
        Connection connection = null;
        try {
            connection = repository.open();
            connection.setAutoCommit(false);
            Book current = findBook(connection, bookId.trim());
            if (current == null) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found");
            }
            final Book updated;
            try {
                updated = current.withAdditionalCopies(copies);
                LibraryCopyRules.checkBatch(copies);
            } catch (IllegalArgumentException invalidStock) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.BAD_REQUEST, invalidStock.getMessage());
            }
            boolean trackCopies = LibraryCopySchema.available(connection);
            if (trackCopies) LibraryCopySchema.initializeBook(connection, current.getBookId());
            // 两个库存字段在同一条语句中更新；旧值条件避免覆盖并发借还或补货。
            String sql = "UPDATE tblBook SET total_copies=?,available_copies=? "
                    + "WHERE book_id=? AND total_copies=? AND available_copies=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, updated.getTotalCopies());
                statement.setInt(2, updated.getAvailableCopies());
                statement.setString(3, current.getBookId());
                statement.setInt(4, current.getTotalCopies());
                statement.setInt(5, current.getAvailableCopies());
                if (statement.executeUpdate() != 1) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "book inventory changed; refresh and retry");
                }
            }
            if (trackCopies) LibraryCopySql.addCopies(connection, current.getBookId(), copies, LibraryCopyStatus.AVAILABLE);
            connection.commit();
            return ServiceResult.ok(updated);
        } catch (SQLException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "library restock transaction failed");
        } finally {
            close(connection);
        }
    }
}

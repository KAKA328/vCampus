package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.LibraryRepository;
import cn.vcampus.library.LibraryBorrowPolicy;
import cn.vcampus.library.LibraryCatalogV4;
import cn.vcampus.library.LibraryCopy;
import cn.vcampus.library.LibraryLoanSnapshot;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static cn.vcampus.server.LibraryAccessSql.*;

/** Access-backed library repository with atomic inventory and borrowing updates. */
public final class AccessLibraryRepository implements LibraryRepository, LibraryCatalogV4 {
    /** 仓库创建时读取的每人同时在借上限。 */
    private final int borrowLimit = LibraryBorrowPolicy.configuredLimit();
    /** Access 数据库文件路径。 */
    private final Path databasePath;
    /** 可替换的数据库连接来源。 */
    private final ConnectionFactory connectionFactory;

    /** 绑定 Access 文件路径；各操作自行打开和关闭短生命周期连接。 */
    public AccessLibraryRepository(Path databasePath) {
        this(databasePath, null);
    }

    /** 包内可替换连接来源，便于验证事务提交失败时的回滚边界。 */
    AccessLibraryRepository(Path databasePath, ConnectionFactory connectionFactory) {
        if (databasePath == null) throw new IllegalArgumentException("databasePath must not be null");
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.connectionFactory = connectionFactory;
    }

    /** 查询馆藏，结果仍使用既有 Book 数据契约。 */
    @Override
    public List<Book> search(String keyword, String category) {
        return LibraryAccessCatalog.search(this, keyword, category);
    }

    /** 在共享图书锁下新增馆藏。 */
    @Override
    public synchronized boolean addBook(Book book) {
        return LibraryAccessCatalog.addBook(this, book);
    }

    /** 在共享图书锁下原子补充总量和可借量。 */
    @Override
    public synchronized ServiceResult<Book> restock(String bookId, int copies) {
        return LibraryAccessCatalog.restock(this, bookId, copies);
    }

    /** 根据图书编号读取馆藏快照。 */
    @Override
    public Book findBook(String bookId) {
        try (Connection connection = open()) {
            return LibraryAccessSql.findBook(connection, bookId);
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find book", failure);
        }
    }

    /** 在同一原子操作内借阅整批图书，检查库存、重复借阅和同时在借上限。 */
    @Override
    public synchronized ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        return LibraryAccessCirculation.borrowBatch(this, borrowLimit, userId, bookIds, borrowDate, dueDate);
    }

    /** 归还指定借阅记录；重复归还不能重复增加库存。 */
    @Override
    public synchronized ServiceResult<BorrowRecord> returnBook(
            String userId, String recordId, LocalDate returnDate) {
        return LibraryAccessCirculation.returnBook(this, userId, recordId, returnDate);
    }

    /** 读取指定用户的借阅历史。 */
    @Override
    public List<BorrowRecord> findBorrowHistory(String userId) {
        return readHistory("SELECT order_id,record_id,user_id,book_id,borrow_date,due_date,return_date,status "
                + "FROM tblBorrowRecord WHERE user_id=? ORDER BY borrow_date DESC,record_id", userId);
    }

    /** 读取全校借阅历史。 */
    @Override
    public List<BorrowRecord> findAllBorrowHistory() {
        return readHistory("SELECT order_id,record_id,user_id,book_id,borrow_date,due_date,return_date,status "
                + "FROM tblBorrowRecord ORDER BY borrow_date DESC,record_id", null);
    }

    /** 使用固定 SQL 与绑定参数读取借阅记录。 */
    private List<BorrowRecord> readHistory(String sql, String userId) {
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId != null) statement.setString(1, userId);
            try (ResultSet results = statement.executeQuery()) {
                List<BorrowRecord> records = new ArrayList<BorrowRecord>();
                while (results.next()) records.add(readBorrowRecord(results));
                return records;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to read borrowing history", failure);
        }
    }

    /** 共享图书锁内查询实体册。 */
    @Override public synchronized ServiceResult<List<LibraryCopy>> copies(String bookId) {
        return LibraryAccessCatalogV4.copies(this, bookId);
    }
    /** 共享图书锁内查询历史快照。 */
    @Override public synchronized ServiceResult<List<LibraryLoanSnapshot>> loanSnapshots(String userId) {
        return LibraryAccessCatalogV4.history(this, userId);
    }
    /** 在共享锁和事务中更新元数据并写入审计。 */
    @Override public synchronized ServiceResult<Book> updateBook(String operatorId, Book expected, Book replacement) {
        return LibraryAccessCatalogV4.update(this, operatorId, expected, replacement);
    }
    /** 打开可替换的短生命周期 Access 连接。 */
    Connection open() throws SQLException {
        if (connectionFactory != null) return connectionFactory.open();
        try { Class.forName("net.ucanaccess.jdbc.UcanaccessDriver"); }
        catch (ClassNotFoundException missingDriver) {
            throw new IllegalStateException("UCanAccess driver is missing", missingDriver);
        }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    /** 临时数据库测试可替换的连接来源。 */
    interface ConnectionFactory {
        /** 打开可替换的短生命周期 Access 连接。 */
        Connection open() throws SQLException;
    }

}

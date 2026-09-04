package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Access-backed library repository with atomic inventory and borrowing updates. */
public final class AccessLibraryRepository implements LibraryRepository {
    private final Path databasePath;

    public AccessLibraryRepository(Path databasePath) {
        if (databasePath == null) throw new IllegalArgumentException("databasePath must not be null");
        this.databasePath = databasePath.toAbsolutePath().normalize();
    }

    @Override
    public List<Book> search(String keyword, String category) {
        String key = normalize(keyword);
        String categoryKey = category == null ? null : normalize(category);
        List<Book> matches = new ArrayList<Book>();
        String sql = "SELECT book_id,title,author,isbn,category,publisher,total_copies,"
                + "available_copies,location FROM tblBook ORDER BY book_id";
        try (Connection connection = open();
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

    @Override
    public Book findBook(String bookId) {
        try (Connection connection = open()) {
            return findBook(connection, bookId);
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to find book", failure);
        }
    }

    @Override
    public synchronized boolean addBook(Book book) {
        if (book == null || findBook(book.getBookId()) != null) return false;
        String sql = "INSERT INTO tblBook(book_id,title,author,isbn,category,publisher,total_copies,"
                + "available_copies,location) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection connection = open();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            writeBook(statement, book);
            return statement.executeUpdate() == 1;
        } catch (SQLException failure) {
            throw new IllegalStateException("failed to add book", failure);
        }
    }

    @Override
    public synchronized ServiceResult<List<BorrowRecord>> borrowBatch(
            String userId, List<String> bookIds, LocalDate borrowDate, LocalDate dueDate) {
        Connection connection = null;
        try {
            connection = open();
            connection.setAutoCommit(false);
            for (String bookId : bookIds) {
                Book book = findBook(connection, bookId);
                if (book == null) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.NOT_FOUND, "book not found: " + bookId);
                }
                if (book.getAvailableCopies() <= 0) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "no available copy: " + bookId);
                }
                if (hasActiveBorrow(connection, userId, bookId)) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "book already borrowed: " + bookId);
                }
            }

            String orderId = id("BO");
            List<BorrowRecord> created = new ArrayList<BorrowRecord>();
            for (String bookId : bookIds) {
                if (!decrementAvailable(connection, bookId)) {
                    rollback(connection);
                    return ServiceResult.failure(StatusCode.CONFLICT, "book inventory changed: " + bookId);
                }
                BorrowRecord record = new BorrowRecord(orderId, id("BR"), userId, bookId,
                        borrowDate, dueDate, null, BorrowStatus.BORROWED);
                insertBorrowRecord(connection, record);
                created.add(record);
            }
            connection.commit();
            return ServiceResult.ok(created);
        } catch (SQLException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "library borrow transaction failed");
        } finally {
            close(connection);
        }
    }

    @Override
    public synchronized ServiceResult<BorrowRecord> returnBook(
            String userId, String recordId, LocalDate returnDate) {
        Connection connection = null;
        try {
            connection = open();
            connection.setAutoCommit(false);
            BorrowRecord active = findActiveRecord(connection, userId, recordId);
            if (active == null) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.NOT_FOUND, "active borrowing record not found");
            }
            if (!markReturned(connection, recordId, returnDate) || !incrementAvailable(connection, active.getBookId())) {
                rollback(connection);
                return ServiceResult.failure(StatusCode.CONFLICT, "library return transaction failed");
            }
            connection.commit();
            return ServiceResult.ok(active.returned(returnDate));
        } catch (SQLException failure) {
            rollback(connection);
            return ServiceResult.failure(StatusCode.SERVER_ERROR, "library return transaction failed");
        } finally {
            close(connection);
        }
    }

    @Override
    public List<BorrowRecord> findBorrowHistory(String userId) {
        return readHistory("SELECT order_id,record_id,user_id,book_id,borrow_date,due_date,return_date,status "
                + "FROM tblBorrowRecord WHERE user_id=? ORDER BY borrow_date DESC,record_id", userId);
    }

    @Override
    public List<BorrowRecord> findAllBorrowHistory() {
        return readHistory("SELECT order_id,record_id,user_id,book_id,borrow_date,due_date,return_date,status "
                + "FROM tblBorrowRecord ORDER BY borrow_date DESC,record_id", null);
    }

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

    private Book findBook(Connection connection, String bookId) throws SQLException {
        String sql = "SELECT book_id,title,author,isbn,category,publisher,total_copies,"
                + "available_copies,location FROM tblBook WHERE book_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readBook(results) : null;
            }
        }
    }

    private boolean hasActiveBorrow(Connection connection, String userId, String bookId) throws SQLException {
        String sql = "SELECT record_id FROM tblBorrowRecord WHERE user_id=? AND book_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, bookId);
            statement.setString(3, BorrowStatus.BORROWED.name());
            try (ResultSet results = statement.executeQuery()) { return results.next(); }
        }
    }

    private BorrowRecord findActiveRecord(Connection connection, String userId, String recordId)
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

    private boolean decrementAvailable(Connection connection, String bookId) throws SQLException {
        String sql = "UPDATE tblBook SET available_copies=available_copies-1 "
                + "WHERE book_id=? AND available_copies>0";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            return statement.executeUpdate() == 1;
        }
    }

    private boolean incrementAvailable(Connection connection, String bookId) throws SQLException {
        String sql = "UPDATE tblBook SET available_copies=available_copies+1 "
                + "WHERE book_id=? AND available_copies<total_copies";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bookId);
            return statement.executeUpdate() == 1;
        }
    }

    private void insertBorrowRecord(Connection connection, BorrowRecord record) throws SQLException {
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

    private boolean markReturned(Connection connection, String recordId, LocalDate returnDate) throws SQLException {
        String sql = "UPDATE tblBorrowRecord SET return_date=?,status=? WHERE record_id=? AND status=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(returnDate));
            statement.setString(2, BorrowStatus.RETURNED.name());
            statement.setString(3, recordId);
            statement.setString(4, BorrowStatus.BORROWED.name());
            return statement.executeUpdate() == 1;
        }
    }

    private static Book readBook(ResultSet results) throws SQLException {
        return new Book(results.getString("book_id"), results.getString("title"),
                results.getString("author"), text(results, "isbn"), text(results, "category"),
                text(results, "publisher"), results.getInt("total_copies"),
                results.getInt("available_copies"), text(results, "location"));
    }

    private static BorrowRecord readBorrowRecord(ResultSet results) throws SQLException {
        Date returned = results.getDate("return_date");
        return new BorrowRecord(results.getString("order_id"), results.getString("record_id"),
                results.getString("user_id"), results.getString("book_id"),
                results.getDate("borrow_date").toLocalDate(), results.getDate("due_date").toLocalDate(),
                returned == null ? null : returned.toLocalDate(),
                BorrowStatus.valueOf(results.getString("status")));
    }

    private static void writeBook(PreparedStatement statement, Book book) throws SQLException {
        statement.setString(1, book.getBookId());
        statement.setString(2, book.getTitle());
        statement.setString(3, book.getAuthor());
        statement.setString(4, book.getIsbn());
        statement.setString(5, book.getCategory());
        statement.setString(6, book.getPublisher());
        statement.setInt(7, book.getTotalCopies());
        statement.setInt(8, book.getAvailableCopies());
        statement.setString(9, book.getLocation());
    }

    private Connection open() throws SQLException {
        try { Class.forName("net.ucanaccess.jdbc.UcanaccessDriver"); }
        catch (ClassNotFoundException missingDriver) {
            throw new IllegalStateException("UCanAccess driver is missing", missingDriver);
        }
        return DriverManager.getConnection("jdbc:ucanaccess://" + databasePath
                + ";immediatelyReleaseResources=true");
    }

    private static void rollback(Connection connection) {
        if (connection == null) return;
        try { connection.rollback(); } catch (SQLException ignored) { }
    }

    private static void close(Connection connection) {
        if (connection == null) return;
        try { connection.close(); } catch (SQLException ignored) { }
    }

    private static String text(ResultSet results, String column) throws SQLException {
        String value = results.getString(column);
        return value == null ? "" : value;
    }

    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString(); }
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
    private static boolean contains(String value, String key) { return normalize(value).contains(key); }
}

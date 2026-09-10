package cn.vcampus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.DefaultLibraryService;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessLibraryRepositoryTest {
    @TempDir Path temporaryDirectory;
    private DefaultLibraryService library;
    private Path database;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("library-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblBook ("
                    + "book_id VARCHAR(32) NOT NULL,title VARCHAR(120) NOT NULL,"
                    + "author VARCHAR(100) NOT NULL,isbn VARCHAR(32),category VARCHAR(64),"
                    + "publisher VARCHAR(100),price DOUBLE NOT NULL,total_copies INTEGER NOT NULL,"
                    + "available_copies INTEGER NOT NULL,location VARCHAR(64),PRIMARY KEY (book_id))");
            statement.execute("CREATE TABLE tblBorrowRecord ("
                    + "record_id VARCHAR(40) NOT NULL,order_id VARCHAR(40) NOT NULL,"
                    + "user_id VARCHAR(32) NOT NULL,book_id VARCHAR(32) NOT NULL,"
                    + "borrow_date DATETIME NOT NULL,due_date DATETIME NOT NULL,"
                    + "return_date DATETIME,status VARCHAR(16) NOT NULL,PRIMARY KEY (record_id))");
            insertBook(connection, "B001", "Java核心技术", 2);
            insertBook(connection, "B002", "算法导论", 1);
        }
        library = new DefaultLibraryService(new AccessLibraryRepository(database));
    }

    @Test
    void catalogSearchAndAddArePersisted() {
        assertEquals(1, library.search("Java").getData().size());
        Book book = new Book("B003", "三体", "刘慈欣", "9787536692930",
                "科幻", "重庆出版社", 39.00d, 3, 3, "B-02");
        assertEquals(StatusCode.OK, library.addBook(book).getStatus());
        assertNotNull(library.getBook("B003").getData());
        assertEquals(39.00d, library.getBook("B003").getData().getPrice(), 0.001d);
        assertEquals(StatusCode.CONFLICT, library.addBook(book).getStatus());
    }

    @Test
    void borrowAndReturnUpdateInventoryAndLedgerAtomically() {
        BorrowRecord borrowed = library.borrow("student001", "B001").getData().get(0);
        assertEquals(1, library.getBook("B001").getData().getAvailableCopies());
        assertEquals(1, library.borrowHistory("student001").getData().size());
        assertFalse(borrowed.isReturned());

        BorrowRecord returned = library.returnBook("student001", borrowed.getRecordId()).getData();
        assertTrue(returned.isReturned());
        assertEquals(2, library.getBook("B001").getData().getAvailableCopies());
        assertTrue(library.borrowHistory("student001").getData().get(0).isReturned());
    }

    @Test
    void batchBorrowRollsBackWhenAnyBookIsMissing() {
        assertEquals(StatusCode.NOT_FOUND,
                library.borrowBatch("student001", Arrays.asList("B001", "NOPE")).getStatus());
        assertEquals(2, library.getBook("B001").getData().getAvailableCopies());
        assertTrue(library.borrowHistory("student001").getData().isEmpty());
    }

    @Test
    void duplicateActiveBorrowIsRejectedWithoutExtraStockChange() {
        assertEquals(StatusCode.OK, library.borrow("student001", "B001").getStatus());
        assertEquals(StatusCode.CONFLICT, library.borrow("student001", "B001").getStatus());
        assertEquals(1, library.getBook("B001").getData().getAvailableCopies());
    }

    @Test
    void anotherUserCannotReturnRecord() {
        BorrowRecord record = library.borrow("student001", "B001").getData().get(0);
        assertEquals(StatusCode.NOT_FOUND, library.returnBook("student002", record.getRecordId()).getStatus());
        assertEquals(1, library.getBook("B001").getData().getAvailableCopies());
    }

    @Test
    void restockPersistsBothCountsAndPreservesExistingLoanAcrossRepositoryInstances() {
        BorrowRecord loan = library.borrow("student001", "B001").getData().get(0);
        Book before = library.getBook("B001").getData();
        assertEquals(StatusCode.OK, library.restock(" B001 ", 5).getStatus());
        DefaultLibraryService reopened = new DefaultLibraryService(new AccessLibraryRepository(database));
        Book updated = reopened.getBook("B001").getData();
        assertEquals(before.withAdditionalCopies(5), updated);
        assertEquals(7, updated.getTotalCopies());
        assertEquals(6, updated.getAvailableCopies());
        assertEquals(loan, reopened.borrowHistory("student001").getData().get(0));
        assertEquals(StatusCode.OK, reopened.returnBook("student001", loan.getRecordId()).getStatus());
        assertEquals(7, reopened.getBook("B001").getData().getAvailableCopies());
    }

    @Test
    void invalidRestockAndOverflowLeavePersistentInventoryUntouched() {
        AccessLibraryRepository repository = new AccessLibraryRepository(database);
        Book before = repository.findBook("B001");
        for (int copies : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(StatusCode.BAD_REQUEST, library.restock("B001", copies).getStatus());
            assertEquals(StatusCode.BAD_REQUEST, repository.restock("B001", copies).getStatus());
        }
        for (String id : new String[] {null, "", " "}) {
            assertEquals(StatusCode.BAD_REQUEST, repository.restock(id, 1).getStatus());
        }
        assertEquals(StatusCode.NOT_FOUND, repository.restock("MISSING", 1).getStatus());
        assertEquals(before, new AccessLibraryRepository(database).findBook("B001"));
    }

    @Test
    void failedRestockCommitRollsBackBothCountsWithoutChangingBorrowHistory() {
        BorrowRecord loan = library.borrow("student001", "B001").getData().get(0);
        Book before = library.getBook("B001").getData();
        AtomicBoolean reachedCommit = new AtomicBoolean();
        AtomicBoolean rolledBack = new AtomicBoolean();
        AccessLibraryRepository failing = new AccessLibraryRepository(database, () -> {
            Connection delegate = DriverManager.getConnection("jdbc:ucanaccess://" + database
                    + ";immediatelyReleaseResources=true");
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("commit".equals(method.getName())) {
                            reachedCommit.set(true);
                            throw new SQLException("simulated commit failure after inventory update");
                        }
                        if ("rollback".equals(method.getName())) rolledBack.set(true);
                        try { return method.invoke(delegate, args); }
                        catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                    });
        });
        assertEquals(StatusCode.SERVER_ERROR, failing.restock("B001", 5).getStatus());
        assertTrue(reachedCommit.get(), "验证异常发生在库存更新之后");
        assertTrue(rolledBack.get(), "提交失败必须显式回滚");
        DefaultLibraryService reopened = new DefaultLibraryService(new AccessLibraryRepository(database));
        assertEquals(before, reopened.getBook("B001").getData());
        assertEquals(loan, reopened.borrowHistory("student001").getData().get(0));
    }

    private static void insertBook(Connection connection, String id, String title, int copies) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblBook(book_id,title,author,isbn,category,publisher,total_copies,"
                        + "available_copies,location,price) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, id);
            statement.setString(2, title);
            statement.setString(3, "测试作者");
            statement.setString(4, "");
            statement.setString(5, "计算机");
            statement.setString(6, "测试出版社");
            statement.setInt(7, copies);
            statement.setInt(8, copies);
            statement.setString(9, "A-01");
            statement.setDouble(10, 88.00d);
            statement.executeUpdate();
        }
    }
}

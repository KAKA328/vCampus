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
import java.sql.Statement;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessLibraryRepositoryTest {
    @TempDir Path temporaryDirectory;
    private DefaultLibraryService library;

    @BeforeEach
    void setUp() throws Exception {
        Path database = temporaryDirectory.resolve("library-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblBook ("
                    + "book_id VARCHAR(32) NOT NULL,title VARCHAR(120) NOT NULL,"
                    + "author VARCHAR(100) NOT NULL,isbn VARCHAR(32),category VARCHAR(64),"
                    + "publisher VARCHAR(100),total_copies INTEGER NOT NULL,"
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
                "科幻", "重庆出版社", 3, 3, "B-02");
        assertEquals(StatusCode.OK, library.addBook(book).getStatus());
        assertNotNull(library.getBook("B003").getData());
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

    private static void insertBook(Connection connection, String id, String title, int copies) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblBook(book_id,title,author,isbn,category,publisher,total_copies,"
                        + "available_copies,location) VALUES(?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, id);
            statement.setString(2, title);
            statement.setString(3, "测试作者");
            statement.setString(4, "");
            statement.setString(5, "计算机");
            statement.setString(6, "测试出版社");
            statement.setInt(7, copies);
            statement.setInt(8, copies);
            statement.setString(9, "A-01");
            statement.executeUpdate();
        }
    }
}

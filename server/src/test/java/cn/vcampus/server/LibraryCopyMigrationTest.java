package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryCopyMigrationTest {
    @TempDir Path directory;
    @Test void migrationKeepsSourceAndLegacyHistoryAndIsIdempotentOnANewCopy() throws Exception {
        Path source = directory.resolve("old.accdb"), migrated = directory.resolve("new.accdb");
        LibraryLimitAccessFixture.create(source);
        DefaultLibraryService old = new DefaultLibraryService(new AccessLibraryRepository(source));
        old.addBook(new Book("BOOK", "旧书名", "作者", "", "文学", "", 10, 3, 3, "A"));
        BorrowRecord returned = old.borrow("returned", "BOOK").getData().get(0);
        old.returnBook("returned", returned.getRecordId());
        BorrowRecord active = old.borrow("active", "BOOK").getData().get(0);
        byte[] before = Files.readAllBytes(source);
        LibraryCopyMigration.migrateCopy(source, migrated, LibraryV4TestFixture.migration());
        assertArrayEquals(before, Files.readAllBytes(source));
        DefaultLibraryService service = new DefaultLibraryService(new AccessLibraryRepository(migrated));
        List<LibraryLoanSnapshot> snapshots = service.loanSnapshots(null).getData();
        assertEquals(2, snapshots.size());
        snapshots.forEach(snapshot -> { assertTrue(snapshot.isHistoricalBackfill()); assertEquals("旧书名", snapshot.getBookTitle()); });
        LibraryLoanSnapshot returnedView = snapshots.stream().filter(item -> item.getRecord().getRecordId().equals(returned.getRecordId())).findFirst().get();
        assertNull(returnedView.getCopyId());
        String activeCopy = service.loanSnapshots("active").getData().get(0).getCopyId();
        assertNotNull(activeCopy);
        assertEquals(3, service.copies("BOOK").getData().size());
        assertEquals(2, service.getBook("BOOK").getData().getAvailableCopies());
        Path another = directory.resolve("again.accdb");
        LibraryCopyMigration.migrateCopy(migrated, another, LibraryV4TestFixture.migration());
        assertEquals(activeCopy, new DefaultLibraryService(new AccessLibraryRepository(another)).loanSnapshots("active").getData().get(0).getCopyId());
        assertEquals(StatusCode.OK, service.returnBook("active", active.getRecordId()).getStatus());
        LibraryV4TestFixture.verify(migrated);
    }
    @Test void inconsistentLegacyInventoryFailsWithoutChangingOriginalDatabase() throws Exception {
        Path source = directory.resolve("bad.accdb"), output = directory.resolve("failed-copy.accdb");
        LibraryLimitAccessFixture.create(source);
        DefaultLibraryService old = new DefaultLibraryService(new AccessLibraryRepository(source));
        old.addBook(new Book("BOOK", "旧书名", "作者"));
        old.borrow("reader", "BOOK");
        try (Connection connection = LibraryV4TestFixture.open(source); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE tblBook SET available_copies=1 WHERE book_id='BOOK'");
        }
        byte[] before = Files.readAllBytes(source);
        assertThrows(Exception.class, () -> LibraryCopyMigration.migrateCopy(source, output, LibraryV4TestFixture.migration()));
        assertArrayEquals(before, Files.readAllBytes(source));
        assertEquals(0, LibraryV4TestFixture.count(output, "tblLibraryLoanSnapshot"));
        assertEquals(0, LibraryV4TestFixture.count(output, "tblBookCopy"));
    }
    @Test void unknownLegacyUnavailableStockStaysUnavailableAndOutputCannotBeOverwritten() throws Exception {
        Path source = directory.resolve("reserved.accdb"), output = directory.resolve("upgraded.accdb");
        LibraryLimitAccessFixture.create(source);
        new DefaultLibraryService(new AccessLibraryRepository(source)).addBook(
                new Book("BOOK", "保留库存", "作者", "", "文学", "", 0, 3, 1, "A"));
        LibraryCopyMigration.migrateCopy(source, output, LibraryV4TestFixture.migration());
        DefaultLibraryService library = new DefaultLibraryService(new AccessLibraryRepository(output));
        assertEquals(2, library.copies("BOOK").getData().stream().filter(copy -> copy.getStatus() == LibraryCopyStatus.UNAVAILABLE).count());
        assertThrows(IllegalArgumentException.class, () -> LibraryCopyMigration.migrateCopy(source, source, LibraryV4TestFixture.migration()));
        byte[] before = Files.readAllBytes(output);
        assertThrows(IllegalArgumentException.class, () -> LibraryCopyMigration.migrateCopy(source, output, LibraryV4TestFixture.migration()));
        assertArrayEquals(before, Files.readAllBytes(output));
        LibraryV4TestFixture.verify(output);
    }
}

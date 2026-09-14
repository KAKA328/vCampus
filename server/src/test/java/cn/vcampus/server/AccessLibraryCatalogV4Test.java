package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccessLibraryCatalogV4Test {
    @TempDir Path directory;
    private Path database;
    private AccessLibraryRepository repository;
    private DefaultLibraryService library;
    @BeforeEach void create() throws Exception {
        database = directory.resolve("v4.accdb");
        LibraryV4TestFixture.create(database);
        repository = new AccessLibraryRepository(database);
        library = new DefaultLibraryService(repository);
        assertEquals(StatusCode.OK, library.addBook(book("原名", 25.5, 3, 3)).getStatus());
    }
    private Book book(String title, double price, int total, int available) {
        return new Book("BOOK", title, "作者", "ISBN", "文学", "出版社", price, total, available, "A");
    }
    private DefaultLibraryService reopened() { return new DefaultLibraryService(new AccessLibraryRepository(database)); }
    @Test void addAndRestockPersistIndividualCopiesWithoutReusingIds() throws Exception {
        List<LibraryCopy> before = library.copies("BOOK").getData();
        assertEquals(3, before.size());
        assertEquals(StatusCode.OK, library.restock("BOOK", 2).getStatus());
        List<LibraryCopy> after = reopened().copies("BOOK").getData();
        HashSet<String> ids = new HashSet<String>();
        after.forEach(copy -> assertTrue(ids.add(copy.getCopyId())));
        assertEquals(5, ids.size());
        before.forEach(copy -> assertTrue(ids.contains(copy.getCopyId())));
        LibraryV4TestFixture.verify(database);
    }
    @Test void editPreservesLoanTitleAndNewInventoryAcrossRestart() throws Exception {
        Book opened = library.getBook("BOOK").getData();
        BorrowRecord loan = library.borrow("reader", "BOOK").getData().get(0);
        String copyId = library.loanSnapshots("reader").getData().get(0).getCopyId();
        assertEquals(StatusCode.OK, library.updateBook("librarian", opened, book("新名", 0, 3, 3)).getStatus());
        Book current = reopened().getBook("BOOK").getData();
        assertEquals("新名", current.getTitle()); assertEquals(0, current.getPrice()); assertEquals(2, current.getAvailableCopies());
        LibraryLoanSnapshot snapshot = reopened().loanSnapshots("reader").getData().get(0);
        assertEquals("原名", snapshot.getBookTitle()); assertFalse(snapshot.isHistoricalBackfill()); assertEquals(copyId, snapshot.getCopyId());
        assertEquals(loan, reopened().borrowHistory("reader").getData().get(0));
        assertEquals(1, LibraryV4TestFixture.count(database, "tblLibraryBookEdit"));
        assertEquals(StatusCode.OK, library.returnBook("reader", loan.getRecordId()).getStatus());
        assertEquals(3, reopened().getBook("BOOK").getData().getAvailableCopies());
        assertEquals(LibraryCopyStatus.AVAILABLE, reopened().copies("BOOK").getData().stream()
                .filter(copy -> copyId.equals(copy.getCopyId())).findFirst().get().getStatus());
        assertEquals("原名", reopened().loanSnapshots("reader").getData().get(0).getBookTitle());
        LibraryV4TestFixture.verify(database);
    }
    @Test void staleOrInventoryChangingEditsAreRejectedWithoutExtraAudit() throws Exception {
        Book opened = library.getBook("BOOK").getData();
        library.restock("BOOK", 1);
        assertEquals(4, library.updateBook("one", opened, book("新名", 1, 3, 3)).getData().getTotalCopies());
        assertEquals(StatusCode.CONFLICT, library.updateBook("two", opened, book("覆盖", 2, 3, 3)).getStatus());
        Book current = library.getBook("BOOK").getData();
        assertEquals(StatusCode.BAD_REQUEST, library.updateBook("one", current, current.withAdditionalCopies(1)).getStatus());
        assertEquals(1, LibraryV4TestFixture.count(database, "tblLibraryBookEdit"));
        LibraryV4TestFixture.verify(database);
    }
    @Test void failedCommitRollsBackMetadataAndAuditTogether() throws Exception {
        Book before = library.getBook("BOOK").getData();
        AccessLibraryRepository faulty = new AccessLibraryRepository(database, () -> {
            final Connection actual;
            try { actual = LibraryV4TestFixture.open(database); } catch (Exception e) { throw new SQLException(e); }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) throw new SQLException("injected commit failure");
                        try { return method.invoke(actual, args); } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        });
        assertEquals(StatusCode.SERVER_ERROR, faulty.updateBook("one", before, book("不可提交", 0, 3, 3)).getStatus());
        assertEquals(before, reopened().getBook("BOOK").getData());
        assertEquals(0, LibraryV4TestFixture.count(database, "tblLibraryBookEdit"));
        LibraryV4TestFixture.verify(database);
    }
    @Test void failedBorrowAndReturnCommitsNeverLeaveHalfUpdatedPhysicalCopies() throws Exception {
        AccessLibraryRepository faulty = new AccessLibraryRepository(database, () -> {
            final Connection actual;
            try { actual = LibraryV4TestFixture.open(database); } catch (Exception e) { throw new SQLException(e); }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) throw new SQLException("injected commit failure");
                        try { return method.invoke(actual, args); } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        });
        DefaultLibraryService failing = new DefaultLibraryService(faulty);
        assertEquals(StatusCode.SERVER_ERROR, failing.borrow("reader", "BOOK").getStatus());
        assertEquals(3, library.getBook("BOOK").getData().getAvailableCopies());
        assertEquals(0, LibraryV4TestFixture.count(database, "tblLibraryLoanSnapshot"));
        assertEquals(0, library.borrowHistory("reader").getData().size());
        LibraryV4TestFixture.verify(database);
        BorrowRecord loan = library.borrow("reader", "BOOK").getData().get(0);
        assertEquals(StatusCode.SERVER_ERROR, failing.returnBook("reader", loan.getRecordId()).getStatus());
        assertEquals(2, library.getBook("BOOK").getData().getAvailableCopies());
        assertEquals(BorrowStatus.BORROWED, library.loanSnapshots("reader").getData().get(0).getRecord().getStatus());
        LibraryV4TestFixture.verify(database);
    }
    @Test void concurrentReadersNeverShareTheSameActiveCopy() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<StatusCode>> results = new ArrayList<Future<StatusCode>>();
        try {
            for (int i = 0; i < 6; i++) {
                final String user = "reader" + i;
                results.add(workers.submit(() -> { start.await(); return library.borrow(user, "BOOK").getStatus(); }));
            }
            start.countDown();
            int successes = 0;
            for (Future<StatusCode> result : results) {
                StatusCode status = result.get(60, TimeUnit.SECONDS);
                if (status == StatusCode.OK) successes++; else assertEquals(StatusCode.CONFLICT, status);
            }
            assertEquals(3, successes);
            HashSet<String> ids = new HashSet<String>();
            reopened().loanSnapshots(null).getData().forEach(loan -> assertTrue(ids.add(loan.getCopyId())));
            assertEquals(3, ids.size());
            LibraryV4TestFixture.verify(database);
        } finally { workers.shutdownNow(); }
    }
}

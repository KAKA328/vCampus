package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.*;
import cn.vcampus.store.BankAccount;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccessLibraryCopyLossTest {
    @TempDir Path directory;
    private Path database;
    private AccessLibraryRepository repository;
    private DefaultLibraryService library;
    private AccessWalletRepository wallet;
    private AccessLibraryCompensationService compensation;
    private BorrowRecord loan;
    @BeforeEach void create() throws Exception {
        database = directory.resolve("loss-v4.accdb");
        LibraryV4TestFixture.create(database);
        LibraryV4TestFixture.paymentTables(database);
        repository = new AccessLibraryRepository(database);
        library = new DefaultLibraryService(repository);
        wallet = new AccessWalletRepository(database);
        compensation = new AccessLibraryCompensationService(database, repository, wallet);
        library.addBook(new Book("BOOK", "原名", "作者", "", "文学", "", 25.5, 3, 3, "A"));
        wallet.save(new BankAccount("reader", 10000));
        loan = library.borrow("reader", "BOOK").getData().get(0);
    }
    @Test void lossTracksExactCopyAndPriceEditsDoNotChangeExistingBillsOrPastTitles() throws Exception {
        String copyId = library.loanSnapshots("reader").getData().get(0).getCopyId();
        LibraryCompensation bill = compensation.declareLoss("librarian", loan.getRecordId()).getData();
        assertEquals(2550, bill.getAmountCents());
        Book opened = library.getBook("BOOK").getData();
        Book changed = new Book("BOOK", "之后改名", "作者", "", "科幻", "", 900, opened.getTotalCopies(), opened.getAvailableCopies(), "B");
        assertEquals(StatusCode.OK, library.updateBook("librarian", opened, changed).getStatus());
        assertEquals(StatusCode.OK, compensation.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(StatusCode.OK, compensation.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(7450, wallet.findByUserId("reader").getBalanceCents());
        assertEquals(1, LibraryV4TestFixture.count(database, "tblWalletTransaction"));
        assertEquals(2, library.getBook("BOOK").getData().getTotalCopies());
        assertEquals(2, library.getBook("BOOK").getData().getAvailableCopies());
        assertEquals(LibraryCopyStatus.LOST, library.copies("BOOK").getData().stream()
                .filter(copy -> copyId.equals(copy.getCopyId())).findFirst().get().getStatus());
        assertEquals("原名", library.loanSnapshots("reader").getData().get(0).getBookTitle());
        assertEquals(BorrowStatus.COMPENSATED, library.loanSnapshots("reader").getData().get(0).getRecord().getStatus());
        LibraryV4TestFixture.verify(database);
    }
    @Test void failedLossCommitRollsBackCopyInventoryRecordAndBillTogether() throws Exception {
        AccessLibraryCompensationService faulty = new AccessLibraryCompensationService(database, repository, wallet, () -> {
            final Connection actual;
            try { actual = LibraryV4TestFixture.open(database); } catch (Exception e) { throw new SQLException(e); }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("commit")) throw new SQLException("injected failure");
                        try { return method.invoke(actual, args); } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
        });
        assertEquals(StatusCode.SERVER_ERROR, faulty.declareLoss("librarian", loan.getRecordId()).getStatus());
        assertEquals(0, LibraryV4TestFixture.count(database, "tblLibraryCompensation"));
        assertEquals(3, library.getBook("BOOK").getData().getTotalCopies());
        assertEquals(2, library.getBook("BOOK").getData().getAvailableCopies());
        assertEquals(BorrowStatus.BORROWED, library.loanSnapshots("reader").getData().get(0).getRecord().getStatus());
        LibraryV4TestFixture.verify(database);
    }
}

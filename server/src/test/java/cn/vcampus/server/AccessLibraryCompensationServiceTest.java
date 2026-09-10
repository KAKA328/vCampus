package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccessLibraryCompensationServiceTest {
    @TempDir Path temporaryDirectory;
    private Path database;
    private AccessLibraryRepository repository;
    private DefaultLibraryService library;
    private AccessWalletRepository wallet;
    private AccessLibraryCompensationService compensations;
    private BorrowRecord loan;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("compensation.accdb");
        createDatabase(database, true);
        repository = new AccessLibraryRepository(database);
        library = new DefaultLibraryService(repository);
        wallet = new AccessWalletRepository(database);
        compensations = new AccessLibraryCompensationService(database, repository, wallet);
        repository.addBook(book("B001", 59.705d));
        wallet.save(new BankAccount("reader", 10000));
        loan = library.borrow("reader", "B001").getData().get(0);
    }

    @Test
    void priceSnapshotPaymentAndIdempotentRetryPersistAcrossReopening() throws Exception {
        LibraryCompensation bill = declare();
        assertEquals(5971, bill.getAmountCents());
        assertStock(1, 1);
        assertEquals(BorrowStatus.LOST, currentLoan().getStatus());
        assertEquals(StatusCode.NOT_FOUND, library.returnBook("reader", loan.getRecordId()).getStatus());
        execute("UPDATE tblBook SET price=999 WHERE book_id='B001'");
        AccessLibraryCompensationService reopened = new AccessLibraryCompensationService(database,
                new AccessLibraryRepository(database), new AccessWalletRepository(database));
        LibraryCompensation paid = reopened.pay("reader", bill.getCompensationId()).getData();
        assertEquals(CompensationStatus.PAID, paid.getStatus());
        assertEquals(5971, paid.getAmountCents());
        assertEquals(BorrowStatus.COMPENSATED, currentLoan().getStatus());
        assertNull(currentLoan().getReturnDate());
        assertEquals(4029, new AccessWalletRepository(database).findByUserId("reader").getBalanceCents());
        List<WalletTransaction> ledger = wallet.findTransactionsByUserId("reader");
        assertEquals(1, ledger.size());
        assertEquals(bill.getCompensationId(), ledger.get(0).getTransactionId());
        assertEquals(WalletTransactionType.LIBRARY_LOSS, ledger.get(0).getType());
        assertEquals(-5971, ledger.get(0).getAmountCents());
        assertEquals(4029, ledger.get(0).getBalanceAfterCents());
        assertEquals("reader", ledger.get(0).getOperatorId());
        assertEquals(StatusCode.OK, reopened.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(bill.getCompensationId(), reopened.declareLoss("admin", loan.getRecordId()).getData().getCompensationId());
        assertEquals(1, reopened.history(null).getData().size());
        assertTrue(reopened.history("another").getData().isEmpty());
        assertEquals(1, wallet.findTransactionsByUserId("reader").size());
        assertStock(1, 1);
    }

    @Test
    void insufficientBalanceAndWrongOwnerLeaveEveryPersistentStateUnchanged() {
        LibraryCompensation bill = declare();
        wallet.save(new BankAccount("reader", 100));
        assertEquals(StatusCode.NOT_FOUND, compensations.pay("another", bill.getCompensationId()).getStatus());
        assertEquals(StatusCode.PAYMENT_REQUIRED, compensations.pay("reader", bill.getCompensationId()).getStatus());
        assertPending(100);
        assertEquals(StatusCode.BAD_REQUEST, compensations.pay(" ", bill.getCompensationId()).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, compensations.declareLoss("admin", " ").getStatus());
    }

    @Test
    void zeroPriceSettlesWithoutAccountOrLedgerAndOverflowNeverDeclaresLoss() {
        repository.addBook(book("FREE", 0));
        BorrowRecord free = library.borrow("no-wallet", "FREE").getData().get(0);
        LibraryCompensation bill = compensations.declareLoss("admin", free.getRecordId()).getData();
        assertEquals(StatusCode.OK, compensations.pay("no-wallet", bill.getCompensationId()).getStatus());
        assertEquals(BorrowStatus.COMPENSATED, repository.findBorrowHistory("no-wallet").get(0).getStatus());
        assertNull(wallet.findByUserId("no-wallet"));
        assertTrue(wallet.findTransactionsByUserId("no-wallet").isEmpty());
        repository.addBook(book("HUGE", Double.MAX_VALUE));
        BorrowRecord huge = library.borrow("reader", "HUGE").getData().get(0);
        assertEquals(StatusCode.BAD_REQUEST, compensations.declareLoss("admin", huge.getRecordId()).getStatus());
        assertEquals(2, repository.findBook("HUGE").getTotalCopies());
        assertEquals(BorrowStatus.BORROWED, repository.findBorrowHistory("reader").stream()
                .filter(record -> record.getRecordId().equals(huge.getRecordId())).findFirst().get().getStatus());
    }

    @Test
    void failedDeclarationCommitRollsBackRealAccessInventoryBorrowingAndBill() {
        AtomicBoolean reached = new AtomicBoolean();
        AtomicBoolean rolledBack = new AtomicBoolean();
        AccessLibraryCompensationService failing = failingService("commit", reached, rolledBack);
        assertEquals(StatusCode.SERVER_ERROR, failing.declareLoss("admin", loan.getRecordId()).getStatus());
        assertTrue(reached.get());
        assertTrue(rolledBack.get());
        assertEquals(BorrowStatus.BORROWED, currentLoan().getStatus());
        assertStock(2, 1);
        assertTrue(compensations.history(null).getData().isEmpty());
    }

    @Test
    void failedPaymentCommitRollsBackRealAccessBalanceLedgerBillAndBorrowing() {
        LibraryCompensation bill = declare();
        AtomicBoolean reached = new AtomicBoolean();
        AtomicBoolean rolledBack = new AtomicBoolean();
        AccessLibraryCompensationService failing = failingService("commit", reached, rolledBack);
        assertEquals(StatusCode.SERVER_ERROR, failing.pay("reader", bill.getCompensationId()).getStatus());
        assertTrue(reached.get());
        assertTrue(rolledBack.get());
        assertPending(10000);
        assertEquals(StatusCode.OK, compensations.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(1, wallet.findTransactionsByUserId("reader").size());
    }

    @Test
    void ledgerInsertFailureAfterDebitRollsBackRealAccessBalanceAndLostRecord() {
        LibraryCompensation bill = declare();
        AtomicBoolean reached = new AtomicBoolean();
        AtomicBoolean rolledBack = new AtomicBoolean();
        AccessLibraryCompensationService failing = failingService("ledger", reached, rolledBack);
        assertEquals(StatusCode.SERVER_ERROR, failing.pay("reader", bill.getCompensationId()).getStatus());
        assertTrue(reached.get());
        assertTrue(rolledBack.get());
        assertPending(10000);
    }

    @Test
    void parallelPaymentAcrossServicesSharingRuntimeOnlyDebitsOnce() throws Exception {
        LibraryCompensation bill = declare();
        AccessLibraryCompensationService other = new AccessLibraryCompensationService(database, repository, wallet);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ServiceResult<LibraryCompensation>>> results = new ArrayList<Future<ServiceResult<LibraryCompensation>>>();
        try {
            for (int i = 0; i < 8; i++) {
                final AccessLibraryCompensationService service = i % 2 == 0 ? other : compensations;
                results.add(pool.submit(() -> { start.await(); return service.pay("reader", bill.getCompensationId()); }));
            }
            start.countDown();
            for (Future<ServiceResult<LibraryCompensation>> future : results) {
                assertEquals(CompensationStatus.PAID, future.get(30, TimeUnit.SECONDS).getData().getStatus());
            }
        } finally { pool.shutdownNow(); }
        assertEquals(4029, wallet.findByUserId("reader").getBalanceCents());
        assertEquals(1, wallet.findTransactionsByUserId("reader").size());
    }

    @Test
    void migrationMatchesBaselineAndPreservesOldLoansWalletBalancesAndLedger() throws Exception {
        Path oldDatabase = temporaryDirectory.resolve("migration.accdb");
        createDatabase(oldDatabase, false);
        AccessLibraryRepository oldLibrary = new AccessLibraryRepository(oldDatabase);
        oldLibrary.addBook(book("OLD", 25));
        BorrowRecord oldLoan = new DefaultLibraryService(oldLibrary).borrow("existing", "OLD").getData().get(0);
        AccessWalletRepository oldWallet = new AccessWalletRepository(oldDatabase);
        oldWallet.credit("existing", 10000, WalletTransactionType.RECHARGE, "existing", "old ledger");
        String migration = AccessDatabaseSchemaTest.readScript("database/migrations/015_library_compensation.up.sql");
        String schema = AccessDatabaseSchemaTest.readScript("database/schema.sql");
        assertEquals(table(schema, "tblLibraryCompensation"), table(migration, "tblLibraryCompensation"));
        AccessDatabaseSchemaTest.executeScript(oldDatabase, migration);
        assertEquals(oldLoan, oldLibrary.findBorrowHistory("existing").get(0));
        assertEquals(10000, oldWallet.findByUserId("existing").getBalanceCents());
        assertEquals(1, oldWallet.findTransactionsByUserId("existing").size());
        AccessLibraryCompensationService migrated = new AccessLibraryCompensationService(oldDatabase, oldLibrary, oldWallet);
        LibraryCompensation bill = migrated.declareLoss("admin", oldLoan.getRecordId()).getData();
        assertEquals(StatusCode.OK, migrated.pay("existing", bill.getCompensationId()).getStatus());
        assertEquals(7500, oldWallet.findByUserId("existing").getBalanceCents());
        assertEquals(2, oldWallet.findTransactionsByUserId("existing").size());
        assertEquals(BorrowStatus.COMPENSATED, oldLibrary.findBorrowHistory("existing").get(0).getStatus());
        try (Connection connection = open(oldDatabase); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblLibraryCompensation SELECT 'different-id',record_id,user_id,book_id,book_title,amount_cents,"
                        + "status,created_by,created_at,paid_at FROM tblLibraryCompensation WHERE compensation_id=?")) {
            statement.setString(1, bill.getCompensationId());
            assertThrows(SQLException.class, statement::executeUpdate, "record_id must be database-unique");
        }
    }

    private AccessLibraryCompensationService failingService(String failAt, AtomicBoolean reached, AtomicBoolean rollback) {
        return new AccessLibraryCompensationService(database, repository, wallet, () -> {
            Connection actual = open(database);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, arguments) -> {
                        if ("rollback".equals(method.getName())) rollback.set(true);
                        if ("commit".equals(failAt) && "commit".equals(method.getName())) {
                            reached.set(true);
                            throw new SQLException("injected failure before actual commit");
                        }
                        if ("ledger".equals(failAt) && "prepareStatement".equals(method.getName())
                                && ((String) arguments[0]).startsWith("INSERT INTO tblWalletTransaction")) {
                            reached.set(true);
                            // Verify debit has actually executed in this same real transaction before failing.
                            try (Statement check = actual.createStatement(); ResultSet balance = check.executeQuery(
                                    "SELECT balance_cents FROM tblBankAccount WHERE user_id='reader'")) {
                                assertTrue(balance.next());
                                assertEquals(4029, balance.getLong(1));
                            }
                            throw new SQLException("injected ledger storage failure");
                        }
                        try { return method.invoke(actual, arguments); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                    });
        });
    }

    private void assertPending(long balance) {
        assertEquals(balance, new AccessWalletRepository(database).findByUserId("reader").getBalanceCents());
        assertTrue(wallet.findTransactionsByUserId("reader").isEmpty());
        assertEquals(BorrowStatus.LOST, currentLoan().getStatus());
        LibraryCompensation bill = compensations.history("reader").getData().get(0);
        assertEquals(CompensationStatus.PENDING, bill.getStatus());
        assertNull(bill.getPaidAt());
        assertStock(1, 1);
    }
    private void assertStock(int total, int available) {
        Book book = new AccessLibraryRepository(database).findBook("B001");
        assertEquals(total, book.getTotalCopies());
        assertEquals(available, book.getAvailableCopies());
    }
    private BorrowRecord currentLoan() { return new AccessLibraryRepository(database).findBorrowHistory("reader").get(0); }
    private LibraryCompensation declare() {
        ServiceResult<LibraryCompensation> result = compensations.declareLoss("admin", loan.getRecordId());
        assertEquals(StatusCode.OK, result.getStatus());
        return result.getData();
    }
    private void execute(String sql) throws SQLException {
        try (Connection connection = open(database); Statement statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static Connection open(Path path) throws SQLException {
        return DriverManager.getConnection("jdbc:ucanaccess://" + path + ";immediatelyReleaseResources=true");
    }
    private static void createDatabase(Path path, boolean withCompensations) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        String schema = AccessDatabaseSchemaTest.readScript("database/schema.sql");
        StringBuilder subset = new StringBuilder();
        for (String name : new String[] {"tblBook", "tblBorrowRecord", "tblBankAccount", "tblWalletTransaction"}) {
            subset.append(table(schema, name)).append('\n');
        }
        if (withCompensations) subset.append(table(schema, "tblLibraryCompensation"));
        AccessDatabaseSchemaTest.executeScript(path, subset.toString());
    }
    private static String table(String script, String name) {
        Matcher matcher = Pattern.compile("(?s)CREATE TABLE " + name + " \\(.*?\\);").matcher(script);
        assertTrue(matcher.find(), "missing table " + name);
        return matcher.group();
    }
    private static Book book(String id, double price) {
        return new Book(id, "Book " + id, "Author", "", "Literature", "Demo", price, 2, 2, "A1");
    }
}

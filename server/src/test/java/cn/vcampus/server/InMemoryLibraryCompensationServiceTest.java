package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.DefaultLibraryService;
import cn.vcampus.library.InMemoryLibraryRepository;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.InMemoryWalletRepository;
import cn.vcampus.store.WalletRepository;
import cn.vcampus.store.WalletTransactionType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryLibraryCompensationServiceTest {
    private InMemoryLibraryRepository repository;
    private DefaultLibraryService library;
    private InMemoryWalletRepository wallet;
    private InMemoryLibraryCompensationService compensations;
    private BorrowRecord loan;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLibraryRepository(Arrays.asList(book("B001", 59.705d)));
        library = new DefaultLibraryService(repository);
        wallet = new InMemoryWalletRepository();
        wallet.save(new BankAccount("reader", 10000));
        compensations = new InMemoryLibraryCompensationService(repository, wallet);
        loan = library.borrow("reader", "B001").getData().get(0);
    }

    @Test
    void originalPriceSettlementSharesWalletAndNeverRestoresLostStock() {
        LibraryCompensation bill = declare();
        assertEquals(5971, bill.getAmountCents());
        assertEquals("Book B001", bill.getBookTitle());
        assertEquals("librarian", bill.getCreatedBy());
        assertEquals(1, repository.findBook("B001").getTotalCopies());
        assertEquals(1, repository.findBook("B001").getAvailableCopies());
        assertEquals(BorrowStatus.LOST, currentLoan().getStatus());
        assertEquals(StatusCode.NOT_FOUND, library.returnBook("reader", loan.getRecordId()).getStatus());
        LibraryCompensation paid = compensations.pay("reader", bill.getCompensationId()).getData();
        assertEquals(CompensationStatus.PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());
        assertEquals(4029L, compensations.balance("reader").getData().longValue());
        assertEquals(BorrowStatus.COMPENSATED, currentLoan().getStatus());
        assertNull(currentLoan().getReturnDate());
        assertEquals(1, repository.findBook("B001").getAvailableCopies());
        assertEquals(1, wallet.findTransactionsByUserId("reader").size());
        assertEquals(WalletTransactionType.LIBRARY_LOSS, wallet.findTransactionsByUserId("reader").get(0).getType());
        assertEquals(-5971, wallet.findTransactionsByUserId("reader").get(0).getAmountCents());
    }

    @Test
    void insufficientBalanceAndWrongOwnerCannotSettleOrWriteLedger() {
        LibraryCompensation bill = declare();
        wallet.save(new BankAccount("reader", 100));
        assertEquals(StatusCode.NOT_FOUND, compensations.pay("another", bill.getCompensationId()).getStatus());
        assertEquals(StatusCode.PAYMENT_REQUIRED, compensations.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(100L, compensations.balance("reader").getData().longValue());
        assertEquals(CompensationStatus.PENDING, compensations.history("reader").getData().get(0).getStatus());
        assertEquals(BorrowStatus.LOST, currentLoan().getStatus());
        assertTrue(wallet.findTransactionsByUserId("reader").isEmpty());
    }

    @Test
    void repeatedDeclarationAndParallelPaymentAcrossServiceInstancesAreIdempotent() throws Exception {
        LibraryCompensation bill = declare();
        InMemoryLibraryCompensationService other = new InMemoryLibraryCompensationService(repository, wallet);
        assertEquals(bill.getCompensationId(), other.declareLoss("admin", loan.getRecordId()).getData().getCompensationId());
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ServiceResult<LibraryCompensation>>> results = new ArrayList<Future<ServiceResult<LibraryCompensation>>>();
        try {
            for (int i = 0; i < 16; i++) {
                final InMemoryLibraryCompensationService service = i % 2 == 0 ? other : compensations;
                results.add(pool.submit(() -> { start.await(); return service.pay("reader", bill.getCompensationId()); }));
            }
            start.countDown();
            for (Future<ServiceResult<LibraryCompensation>> future : results) {
                assertEquals(CompensationStatus.PAID, future.get(10, TimeUnit.SECONDS).getData().getStatus());
            }
        } finally { pool.shutdownNow(); }
        assertEquals(4029L, wallet.findByUserId("reader").getBalanceCents());
        assertEquals(1, wallet.findTransactionsByUserId("reader").size());
        assertEquals(1, other.history(null).getData().size());
        assertTrue(other.history("another").getData().isEmpty());
        assertEquals(1, repository.findBook("B001").getTotalCopies());
    }

    @Test
    void zeroPriceCanSettleWithoutAnAccountOrZeroValueWalletMutation() {
        repository.addBook(book("FREE", 0));
        BorrowRecord free = library.borrow("no-wallet", "FREE").getData().get(0);
        LibraryCompensation bill = compensations.declareLoss("admin", free.getRecordId()).getData();
        assertEquals(0, bill.getAmountCents());
        assertEquals(StatusCode.OK, compensations.pay("no-wallet", bill.getCompensationId()).getStatus());
        assertNull(wallet.findByUserId("no-wallet"));
        assertTrue(wallet.findTransactionsByUserId("no-wallet").isEmpty());
    }

    @Test
    void invalidOrOverflowPricesNeverChangeInventoryOrLoan() {
        repository.addBook(book("HUGE", Double.MAX_VALUE));
        BorrowRecord huge = library.borrow("reader", "HUGE").getData().get(0);
        assertEquals(StatusCode.BAD_REQUEST, compensations.declareLoss("admin", huge.getRecordId()).getStatus());
        assertEquals(2, repository.findBook("HUGE").getTotalCopies());
        assertEquals(1, repository.findBook("HUGE").getAvailableCopies());
        assertEquals(BorrowStatus.BORROWED, repository.findBorrowHistory("reader").get(1).getStatus());
        assertEquals(5970, LibraryCompensation.originalPriceCents(59.70d));
        assertEquals(1, LibraryCompensation.originalPriceCents(0.005d));
        assertThrows(IllegalArgumentException.class, () -> LibraryCompensation.originalPriceCents(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> LibraryCompensation.originalPriceCents(-1));
    }

    @Test
    void walletFailureLeavesBillAndBorrowingPending() {
        LibraryCompensation bill = declare();
        WalletRepository failing = (WalletRepository) Proxy.newProxyInstance(WalletRepository.class.getClassLoader(),
                new Class<?>[] {WalletRepository.class}, (proxy, method, arguments) -> {
                    if ("debit".equals(method.getName())) throw new IllegalStateException("injected storage failure");
                    try { return method.invoke(wallet, arguments); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
        InMemoryLibraryCompensationService service = new InMemoryLibraryCompensationService(repository, failing);
        assertEquals(StatusCode.SERVER_ERROR, service.pay("reader", bill.getCompensationId()).getStatus());
        assertEquals(10000, wallet.findByUserId("reader").getBalanceCents());
        assertEquals(BorrowStatus.LOST, currentLoan().getStatus());
        assertEquals(CompensationStatus.PENDING, service.history("reader").getData().get(0).getStatus());
        assertTrue(wallet.findTransactionsByUserId("reader").isEmpty());
    }

    @Test
    void invalidInputsAndReturnedLoansAreRejected() {
        for (String invalid : new String[] {null, "", " "}) {
            assertEquals(StatusCode.BAD_REQUEST, compensations.declareLoss(invalid, loan.getRecordId()).getStatus());
            assertEquals(StatusCode.BAD_REQUEST, compensations.declareLoss("admin", invalid).getStatus());
            assertEquals(StatusCode.BAD_REQUEST, compensations.pay(invalid, "id").getStatus());
            assertEquals(StatusCode.BAD_REQUEST, compensations.balance(invalid).getStatus());
        }
        assertEquals(StatusCode.NOT_FOUND, compensations.declareLoss("admin", "missing").getStatus());
        assertEquals(StatusCode.NOT_FOUND, compensations.pay("reader", "missing").getStatus());
        library.returnBook("reader", loan.getRecordId());
        assertEquals(StatusCode.CONFLICT, compensations.declareLoss("admin", loan.getRecordId()).getStatus());
        assertTrue(compensations.history(null).getData().isEmpty());
    }

    private LibraryCompensation declare() {
        ServiceResult<LibraryCompensation> result = compensations.declareLoss("librarian", loan.getRecordId());
        assertEquals(StatusCode.OK, result.getStatus());
        return result.getData();
    }
    private BorrowRecord currentLoan() { return repository.findBorrowHistory("reader").get(0); }
    private static Book book(String id, double price) {
        return new Book(id, "Book " + id, "Author", "", "Literature", "Demo", price, 2, 2, "A1");
    }
}

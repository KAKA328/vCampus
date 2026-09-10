package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

/** Concurrent requests share one repository, matching one running demo server. */
class LibraryBorrowConcurrencyTest {
    private static final int WORKERS = 24;
    private final DefaultLibraryService library =
            new DefaultLibraryService(new InMemoryLibraryRepository());

    private DefaultLibraryService persistedView() { return library; }

    @Test
    void simultaneousReadersCompetingForLastCopyProduceExactlyOneLoan() throws Exception {
        addBook("LAST", 1);
        List<ServiceResult<List<BorrowRecord>>> results = simultaneously(
                WORKERS, index -> library.borrow("reader-" + index, "LAST"));
        assertStatuses(results, 1, StatusCode.CONFLICT);
        DefaultLibraryService observed = persistedView();
        assertInventory(observed, "LAST", 1, 0);
        List<BorrowRecord> history = observed.allBorrowHistory().getData();
        assertEquals(1, history.size());
        assertEquals(BorrowStatus.BORROWED, history.get(0).getStatus());
        for (int index = 0; index < results.size(); index++) {
            List<BorrowRecord> ownHistory = observed.borrowHistory("reader-" + index).getData();
            if (results.get(index).getStatus() == StatusCode.OK) {
                assertEquals(1, results.get(index).getData().size());
                assertEquals(results.get(index).getData(), ownHistory);
            } else {
                assertTrue(ownHistory.isEmpty(), "unsuccessful readers must not receive a loan");
            }
        }
    }

    @Test
    void simultaneousReadersCannotBorrowMoreThanAvailableCopies() throws Exception {
        int copies = 3;
        addBook("MULTI", copies);
        List<ServiceResult<List<BorrowRecord>>> results = simultaneously(
                WORKERS, index -> library.borrow("reader-" + index, "MULTI"));
        assertStatuses(results, copies, StatusCode.CONFLICT);
        DefaultLibraryService observed = persistedView();
        assertInventory(observed, "MULTI", copies, 0);
        List<BorrowRecord> records = observed.allBorrowHistory().getData();
        assertEquals(copies, records.size());
        Set<String> users = new HashSet<String>();
        Set<String> recordIds = new HashSet<String>();
        for (BorrowRecord record : records) {
            assertEquals(BorrowStatus.BORROWED, record.getStatus());
            assertEquals("MULTI", record.getBookId());
            assertTrue(users.add(record.getUserId()));
            assertTrue(recordIds.add(record.getRecordId()));
        }
    }

    @Test
    void duplicateConcurrentBorrowRequestsFromOneReaderDeductOnlyOneCopy() throws Exception {
        addBook("DUPLICATE", WORKERS);
        List<ServiceResult<List<BorrowRecord>>> results = simultaneously(
                WORKERS, index -> library.borrow("same-reader", "DUPLICATE"));
        assertStatuses(results, 1, StatusCode.CONFLICT);
        DefaultLibraryService observed = persistedView();
        assertInventory(observed, "DUPLICATE", WORKERS, WORKERS - 1);
        List<BorrowRecord> records = observed.borrowHistory("same-reader").getData();
        assertEquals(1, records.size());
        assertEquals(BorrowStatus.BORROWED, records.get(0).getStatus());
        assertEquals(1, observed.allBorrowHistory().getData().size());
    }

    @Test
    void duplicateConcurrentReturnsRestoreOnlyOneCopy() throws Exception {
        addBook("RETURN", 3);
        ServiceResult<List<BorrowRecord>> borrowed = library.borrow("same-reader", "RETURN");
        assertEquals(StatusCode.OK, borrowed.getStatus());
        BorrowRecord record = borrowed.getData().get(0);
        assertInventory(library, "RETURN", 3, 2);
        List<ServiceResult<BorrowRecord>> results = simultaneously(
                WORKERS, index -> library.returnBook("same-reader", record.getRecordId()));
        assertStatuses(results, 1, StatusCode.NOT_FOUND);
        DefaultLibraryService observed = persistedView();
        assertInventory(observed, "RETURN", 3, 3);
        List<BorrowRecord> records = observed.allBorrowHistory().getData();
        assertEquals(1, records.size());
        assertEquals(record.getRecordId(), records.get(0).getRecordId());
        assertEquals(BorrowStatus.RETURNED, records.get(0).getStatus());
        assertNotNull(records.get(0).getReturnDate());
    }

    @Test
    void losingConcurrentBatchRequestsLeaveNeitherPartialLoansNorPartialStockChanges() throws Exception {
        addBook("PLENTIFUL", WORKERS);
        addBook("SCARCE", 1);
        List<ServiceResult<List<BorrowRecord>>> results = simultaneously(
                WORKERS, index -> library.borrowBatch("reader-" + index,
                        Arrays.asList("PLENTIFUL", "SCARCE")));
        assertStatuses(results, 1, StatusCode.CONFLICT);
        DefaultLibraryService observed = persistedView();
        assertInventory(observed, "PLENTIFUL", WORKERS, WORKERS - 1);
        assertInventory(observed, "SCARCE", 1, 0);
        List<BorrowRecord> records = observed.allBorrowHistory().getData();
        assertEquals(2, records.size());
        assertEquals(records.get(0).getUserId(), records.get(1).getUserId());
        assertEquals(records.get(0).getOrderId(), records.get(1).getOrderId());
        Set<String> borrowedBooks = new HashSet<String>();
        for (BorrowRecord record : records) {
            assertEquals(BorrowStatus.BORROWED, record.getStatus());
            borrowedBooks.add(record.getBookId());
        }
        assertEquals(new HashSet<String>(Arrays.asList("PLENTIFUL", "SCARCE")), borrowedBooks);
        for (int index = 0; index < results.size(); index++) {
            List<BorrowRecord> ownHistory = observed.borrowHistory("reader-" + index).getData();
            if (results.get(index).getStatus() == StatusCode.OK) {
                assertEquals(2, ownHistory.size());
                assertEquals(2, results.get(index).getData().size());
            } else {
                assertTrue(ownHistory.isEmpty(), "a failed batch must not keep its plentiful book");
            }
        }
    }

    private void addBook(String bookId, int copies) {
        Book book = new Book(bookId, "并发测试图书", "测试作者", "", "计算机",
                "测试出版社", 39.50d, copies, copies, "TEST");
        assertEquals(StatusCode.OK, library.addBook(book).getStatus());
    }

    private static void assertInventory(DefaultLibraryService observed,
            String bookId, int total, int available) {
        ServiceResult<Book> result = observed.getBook(bookId);
        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(total, result.getData().getTotalCopies());
        assertEquals(available, result.getData().getAvailableCopies());
        assertTrue(result.getData().getAvailableCopies() >= 0);
        assertTrue(result.getData().getAvailableCopies() <= result.getData().getTotalCopies());
    }

    private static void assertStatuses(List<? extends ServiceResult<?>> results,
            int expectedSuccesses, StatusCode expectedFailure) {
        int successes = 0;
        for (ServiceResult<?> result : results) {
            if (result.getStatus() == StatusCode.OK) successes++;
            else assertEquals(expectedFailure, result.getStatus(), result.getMessage());
        }
        assertEquals(expectedSuccesses, successes);
        assertEquals((long) WORKERS - expectedSuccesses,
                results.stream().filter(result -> result.getStatus() == expectedFailure).count());
    }

    private static <T> List<T> simultaneously(int count, IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<Future<T>>();
        try {
            for (int index = 0; index < count; index++) {
                final int requestIndex = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent start signal timed out");
                    }
                    return action.apply(requestIndex);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all requests must reach the start barrier");
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            List<T> results = new ArrayList<T>();
            for (Future<T> future : futures) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0, "concurrent requests exceeded the overall deadline");
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            start.countDown();
            for (Future<T> future : futures) future.cancel(true);
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "worker pool did not terminate");
        }
    }
}

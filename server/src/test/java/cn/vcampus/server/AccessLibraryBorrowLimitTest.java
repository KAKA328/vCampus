package cn.vcampus.server;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.DefaultLibraryService;
import java.nio.file.Path;
import java.time.LocalDate;
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
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Access 中的额度检查与库存、借阅写入处于同一事务及仓库锁。 */
class AccessLibraryBorrowLimitTest {
    @TempDir Path temporaryDirectory;
    private Path database;
    private AccessLibraryRepository repository;
    private DefaultLibraryService library;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("loan-limit.accdb");
        LibraryLimitAccessFixture.create(database);
        repository = new AccessLibraryRepository(database);
        library = new DefaultLibraryService(repository);
        for (int i = 1; i <= 8; i++) {
            library.addBook(new Book("L" + i, "图书" + i, "作者", "", "文学", "", 0, 2, 2, "A"));
        }
    }

    private DefaultLibraryService persisted() {
        return new DefaultLibraryService(new AccessLibraryRepository(database));
    }

    @Test
    void limitPersistsAcrossRepositoryRestartAndIncludesOverdueLoans() {
        LocalDate past = LocalDate.now().minusDays(60);
        assertEquals(StatusCode.OK, repository.borrowBatch("reader",
                Arrays.asList("L1", "L2", "L3", "L4", "L5"), past, past.plusDays(30)).getStatus());
        assertEquals(StatusCode.CONFLICT, persisted().borrow("reader", "L6").getStatus());
        assertEquals(5, persisted().borrowHistory("reader").getData().size());
        assertEquals(2, persisted().getBook("L6").getData().getAvailableCopies());
        assertEquals(StatusCode.OK, persisted().borrow("another", "L6").getStatus());
    }

    @Test
    void rejectedBatchDoesNotConsumeAnyStockAndReturningRestoresCapacity() {
        library.borrowBatch("reader", Arrays.asList("L1", "L2", "L3", "L4"));
        assertEquals(StatusCode.CONFLICT, library.borrowBatch("reader", Arrays.asList("L5", "L6")).getStatus());
        DefaultLibraryService observed = persisted();
        assertEquals(4, observed.borrowHistory("reader").getData().size());
        assertEquals(2, observed.getBook("L5").getData().getAvailableCopies());
        assertEquals(2, observed.getBook("L6").getData().getAvailableCopies());
        BorrowRecord first = observed.borrowHistory("reader").getData().get(0);
        assertEquals(StatusCode.OK, library.returnBook("reader", first.getRecordId()).getStatus());
        assertEquals(StatusCode.OK, library.borrowBatch("reader", Arrays.asList("L5", "L6")).getStatus());
        assertEquals(5, persisted().borrowHistory("reader").getData().stream()
                .filter(record -> !record.isReturned()).count());
    }

    @Test
    void concurrentRequestsForDifferentTitlesCannotBypassThePerUserLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<StatusCode>> results = new ArrayList<Future<StatusCode>>();
        try {
            for (int i = 1; i <= 8; i++) {
                final String book = "L" + i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return library.borrow("reader", book).getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<StatusCode> result : results) {
                StatusCode status = result.get(60, TimeUnit.SECONDS);
                if (status == StatusCode.OK) successes++;
                else assertEquals(StatusCode.CONFLICT, status);
            }
            assertEquals(5, successes);
            DefaultLibraryService observed = persisted();
            assertEquals(5, observed.borrowHistory("reader").getData().size());
            assertEquals(11, observed.search("").getData().stream().mapToInt(Book::getAvailableCopies).sum());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}

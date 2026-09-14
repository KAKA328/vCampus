package cn.vcampus.library;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 同一仓库内的总借阅上限、整批原子性和并发额度检查。 */
class LibraryBorrowLimitTest {
    private final InMemoryLibraryRepository repository = new InMemoryLibraryRepository();
    private final DefaultLibraryService library = new DefaultLibraryService(repository);

    private void catalog() {
        for (int i = 1; i <= 12; i++) {
            library.addBook(new Book("L" + i, "图书" + i, "作者", "", "文学", "", 0, 2, 2, "A"));
        }
    }

    @Test
    void configurationIsPositiveAndOverflowSafe() {
        assertEquals(5, LibraryBorrowPolicy.parseLimit(null));
        assertEquals(10, LibraryBorrowPolicy.parseLimit(" 10 "));
        for (String invalid : Arrays.asList("", "0", "-1", "abc", "2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> LibraryBorrowPolicy.parseLimit(invalid));
        }
        assertFalse(LibraryBorrowPolicy.allows(Integer.MAX_VALUE, Integer.MAX_VALUE, 1));
        assertFalse(LibraryBorrowPolicy.allows(5, 6, 1));
    }

    @Test
    void sixthBookIsRejectedWithoutChangingInventory() {
        catalog();
        assertEquals(StatusCode.OK, library.borrowBatch("reader", Arrays.asList("L1", "L2", "L3", "L4", "L5")).getStatus());
        ServiceResult<List<BorrowRecord>> rejected = library.borrow("reader", "L6");
        assertEquals(StatusCode.CONFLICT, rejected.getStatus());
        assertTrue(rejected.getMessage().contains("5"));
        assertEquals(5, library.borrowHistory("reader").getData().size());
        assertEquals(2, library.getBook("L6").getData().getAvailableCopies());
        assertEquals(StatusCode.OK, library.borrow("another-reader", "L6").getStatus());
    }

    @Test
    void overLimitBatchIsAllOrNothingAndReturnReleasesOneSlot() {
        catalog();
        library.borrowBatch("reader", Arrays.asList("L1", "L2", "L3", "L4"));
        assertEquals(StatusCode.CONFLICT, library.borrowBatch("reader", Arrays.asList("L5", "L6")).getStatus());
        assertEquals(4, library.borrowHistory("reader").getData().size());
        assertEquals(2, library.getBook("L5").getData().getAvailableCopies());
        assertEquals(2, library.getBook("L6").getData().getAvailableCopies());
        BorrowRecord first = library.borrowHistory("reader").getData().get(0);
        assertEquals(StatusCode.OK, library.returnBook("reader", first.getRecordId()).getStatus());
        assertEquals(StatusCode.OK, library.borrowBatch("reader", Arrays.asList("L5", "L6")).getStatus());
    }

    @Test
    void lostLoansDoNotIntroduceAnUnrequestedDebtBorrowingBan() {
        catalog();
        library.borrowBatch("reader", Arrays.asList("L1", "L2", "L3", "L4", "L5"));
        BorrowRecord first = library.borrowHistory("reader").getData().get(0);
        assertEquals(StatusCode.OK, repository.declareLoss("librarian", first.getRecordId()).getStatus());
        assertEquals(StatusCode.OK, library.borrow("reader", "L6").getStatus());
    }

    @Test
    void simultaneousDifferentBooksForOneReaderCannotExceedTotalLimit() throws Exception {
        catalog();
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<StatusCode>> results = new ArrayList<Future<StatusCode>>();
        try {
            for (int i = 1; i <= 12; i++) {
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
                StatusCode status = result.get(15, TimeUnit.SECONDS);
                if (status == StatusCode.OK) successes++;
                else assertEquals(StatusCode.CONFLICT, status);
            }
            assertEquals(5, successes);
            assertEquals(5, library.borrowHistory("reader").getData().size());
            assertEquals(19, library.search("").getData().stream().mapToInt(Book::getAvailableCopies).sum());
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}

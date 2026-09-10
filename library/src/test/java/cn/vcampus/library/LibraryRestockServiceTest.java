package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class LibraryRestockServiceTest {
    private final Book original = new Book("TEST", "测试图书", "测试作者", "isbn", "文学",
            "测试出版社", 38.50d, 3, 3, "A-01");
    private final InMemoryLibraryRepository repository = new InMemoryLibraryRepository(
            Collections.singletonList(original));
    private final DefaultLibraryService library = new DefaultLibraryService(repository);

    @Test
    void restockIncreasesBothCountsWithoutChangingExistingLoanOrMetadata() {
        BorrowRecord loan = library.borrow("student", "TEST").getData().get(0);
        ServiceResult<Book> result = library.restock(" TEST ", 4);
        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(original.withAvailableCopies(2).withAdditionalCopies(4), result.getData());
        assertEquals(7, result.getData().getTotalCopies());
        assertEquals(6, result.getData().getAvailableCopies());
        assertEquals(loan, library.borrowHistory("student").getData().get(0));
        assertFalse(loan.isReturned());
        assertEquals(StatusCode.OK, library.returnBook("student", loan.getRecordId()).getStatus());
        assertEquals(7, library.getBook("TEST").getData().getAvailableCopies());
    }

    @Test
    void serviceAndRepositoryRejectInvalidOrMissingTargetsWithoutChangingStock() {
        for (int copies : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertEquals(StatusCode.BAD_REQUEST, library.restock("TEST", copies).getStatus());
            assertEquals(StatusCode.BAD_REQUEST, repository.restock("TEST", copies).getStatus());
        }
        for (String id : new String[] {null, "", " "}) {
            assertEquals(StatusCode.BAD_REQUEST, library.restock(id, 1).getStatus());
            assertEquals(StatusCode.BAD_REQUEST, repository.restock(id, 1).getStatus());
        }
        assertEquals(StatusCode.NOT_FOUND, library.restock("MISSING", 1).getStatus());
        assertEquals(original, library.getBook("TEST").getData());
    }

    @Test
    void overflowIsRejectedAtomicallyAndMaximumRepresentableStockIsAllowed() {
        assertEquals(StatusCode.BAD_REQUEST, library.restock("TEST", Integer.MAX_VALUE).getStatus());
        assertEquals(original, library.getBook("TEST").getData());
        assertEquals(StatusCode.OK, library.restock("TEST", Integer.MAX_VALUE - 3).getStatus());
        assertEquals(Integer.MAX_VALUE, library.getBook("TEST").getData().getTotalCopies());
        assertEquals(StatusCode.BAD_REQUEST, library.restock("TEST", 1).getStatus());
        assertEquals(Integer.MAX_VALUE, library.getBook("TEST").getData().getAvailableCopies());
    }

    @Test
    void zeroStockCanBeReplenishedAndThenBorrowed() {
        library.addBook(new Book("EMPTY", "空库存图书", "作者", "", "", "", 0, 0, ""));
        assertEquals(StatusCode.OK, library.restock("EMPTY", 1).getStatus());
        assertEquals(StatusCode.OK, library.borrow("student", "EMPTY").getStatus());
    }

    @Test
    void concurrentRestocksDoNotLoseCopiesOrChangeBorrowedCount() throws Exception {
        library.borrow("student", "TEST");
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> results = new java.util.ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                results.add(workers.submit(() -> {
                    for (int attempt = 0; attempt < 25; attempt++) {
                        assertEquals(StatusCode.OK, library.restock("TEST", 1).getStatus());
                    }
                }));
            }
            for (java.util.concurrent.Future<?> result : results) {
                result.get(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally { workers.shutdownNow(); }
        assertEquals(103, library.getBook("TEST").getData().getTotalCopies());
        assertEquals(102, library.getBook("TEST").getData().getAvailableCopies());
        assertEquals(1, library.borrowHistory("student").getData().size());
    }

    @Test
    void restockCommandValidatesAndRoundTripsWithUnchangedDeltaMeaning() throws Exception {
        LibraryRestockV2Command command = new LibraryRestockV2Command(" token ", " TEST ", 5);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) { stream.writeObject(command); }
        LibraryRestockV2Command decoded;
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            decoded = (LibraryRestockV2Command) stream.readObject();
        }
        assertEquals("token", decoded.getToken());
        assertEquals("TEST", decoded.getBookId());
        assertEquals(5, decoded.getCopies());
        assertThrows(IllegalArgumentException.class, () -> new LibraryRestockV2Command(" ", "TEST", 1));
        assertThrows(IllegalArgumentException.class, () -> new LibraryRestockV2Command("token", " ", 1));
        assertThrows(IllegalArgumentException.class, () -> new LibraryRestockV2Command("token", "TEST", 0));
        assertThrows(IllegalArgumentException.class, () -> new LibraryRestockV2Command("token", "TEST", -1));
    }
}

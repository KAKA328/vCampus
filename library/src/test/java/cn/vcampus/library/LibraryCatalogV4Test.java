package cn.vcampus.library;

import cn.vcampus.common.StatusCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryCatalogV4Test {
    private final InMemoryLibraryRepository repository = new InMemoryLibraryRepository();
    private final DefaultLibraryService library = new DefaultLibraryService(repository);
    private Book add(int copies) {
        Book book = new Book("BOOK", "原始书名", "作者", "ISBN", "文学", "出版社", 25.5, copies, copies, "A");
        assertEquals(StatusCode.OK, library.addBook(book).getStatus());
        return book;
    }
    private Book changed(Book source, String title, double price) {
        return new Book(source.getBookId(), title, source.getAuthor(), source.getIsbn(), "科幻",
                source.getPublisher(), price, source.getTotalCopies(), source.getAvailableCopies(), "B");
    }
    @Test void everyCopyHasItsOwnIdAndRestockingDoesNotReuseAnExistingId() {
        add(3);
        List<LibraryCopy> before = library.copies("BOOK").getData();
        assertEquals(3, before.size());
        assertEquals(StatusCode.OK, library.restock("BOOK", 2).getStatus());
        List<LibraryCopy> after = library.copies("BOOK").getData();
        assertEquals(5, after.size());
        HashSet<String> ids = new HashSet<String>();
        after.forEach(copy -> { assertTrue(ids.add(copy.getCopyId())); assertEquals(LibraryCopyStatus.AVAILABLE, copy.getStatus()); });
        before.forEach(copy -> assertTrue(ids.contains(copy.getCopyId())));
    }
    @Test void renamingAndRepricingNeverRewritesBorrowTimeTitleOrConcurrentInventory() {
        Book opened = add(3);
        BorrowRecord record = library.borrow("reader", "BOOK").getData().get(0);
        Book saved = library.updateBook("librarian", opened, changed(opened, "新版名称", 0)).getData();
        assertEquals(2, saved.getAvailableCopies());
        assertEquals(0, saved.getPrice());
        LibraryLoanSnapshot snapshot = library.loanSnapshots("reader").getData().get(0);
        assertEquals("原始书名", snapshot.getBookTitle());
        assertFalse(snapshot.isHistoricalBackfill());
        assertEquals(record, snapshot.getRecord());
        assertNotNull(snapshot.getCopyId());
        assertEquals(StatusCode.OK, library.returnBook("reader", record.getRecordId()).getStatus());
        assertEquals("原始书名", library.loanSnapshots("reader").getData().get(0).getBookTitle());
        assertEquals(3, library.getBook("BOOK").getData().getAvailableCopies());
    }
    @Test void returnsTheExactCopyAndLossNeverRestoresItsInventoryAfterPayment() {
        add(1);
        BorrowRecord loan = library.borrow("reader", "BOOK").getData().get(0);
        String id = library.loanSnapshots("reader").getData().get(0).getCopyId();
        library.returnBook("reader", loan.getRecordId());
        assertEquals(id, library.copies("BOOK").getData().get(0).getCopyId());
        BorrowRecord second = library.borrow("another", "BOOK").getData().get(0);
        assertEquals(id, library.loanSnapshots("another").getData().get(0).getCopyId());
        LibraryCompensation bill = repository.declareLoss("librarian", second.getRecordId()).getData();
        Book before = library.getBook("BOOK").getData();
        assertEquals(StatusCode.OK, library.updateBook("librarian", before, changed(before, "后来名称", 900)).getStatus());
        assertEquals(2550, bill.getAmountCents());
        assertEquals(StatusCode.OK, repository.payCompensation("another", bill.getCompensationId(), amount -> amount == 2550).getStatus());
        assertEquals(LibraryCopyStatus.LOST, library.copies("BOOK").getData().get(0).getStatus());
        assertEquals(0, library.getBook("BOOK").getData().getTotalCopies());
        assertEquals("原始书名", library.loanSnapshots("another").getData().get(0).getBookTitle());
    }
    @Test void editingRejectsStaleMetadataAndTamperingButNotConcurrentRestocks() {
        Book opened = add(2);
        library.restock("BOOK", 1);
        assertEquals(3, library.updateBook("admin", opened, changed(opened, "新名", 1)).getData().getTotalCopies());
        assertEquals(StatusCode.CONFLICT, library.updateBook("other", opened, changed(opened, "覆盖", 2)).getStatus());
        Book current = library.getBook("BOOK").getData();
        assertEquals(StatusCode.BAD_REQUEST, library.updateBook("admin", current, current.withAdditionalCopies(1)).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, library.updateBook(" ", current, changed(current, "任意", 0)).getStatus());
        Book wrongId = new Book("OTHER", current.getTitle(), current.getAuthor());
        assertEquals(StatusCode.BAD_REQUEST, library.updateBook("admin", current, wrongId).getStatus());
        assertEquals(current, library.getBook("BOOK").getData());
    }
    @Test void concurrentReadersAreAssignedDifferentPhysicalCopies() throws Exception {
        add(3);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<StatusCode>> results = new ArrayList<Future<StatusCode>>();
        try {
            for (int i = 0; i < 8; i++) {
                final String user = "reader" + i;
                results.add(workers.submit(() -> { start.await(); return library.borrow(user, "BOOK").getStatus(); }));
            }
            start.countDown();
            int count = 0;
            for (Future<StatusCode> result : results) if (result.get(10, TimeUnit.SECONDS) == StatusCode.OK) count++;
            assertEquals(3, count);
            HashSet<String> ids = new HashSet<String>();
            library.loanSnapshots(null).getData().forEach(loan -> assertTrue(ids.add(loan.getCopyId())));
            assertEquals(3, ids.size());
            assertEquals(0, library.getBook("BOOK").getData().getAvailableCopies());
        } finally { workers.shutdownNow(); }
    }
    @Test void concurrentEditorsCannotSilentlyOverwriteEachOther() throws Exception {
        Book opened = add(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<StatusCode> first = workers.submit(() -> { start.await(); return library.updateBook("one", opened, changed(opened, "一", 1)).getStatus(); });
            Future<StatusCode> second = workers.submit(() -> { start.await(); return library.updateBook("two", opened, changed(opened, "二", 2)).getStatus(); });
            start.countDown();
            List<StatusCode> results = java.util.Arrays.asList(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, Collections.frequency(results, StatusCode.OK));
            assertEquals(1, Collections.frequency(results, StatusCode.CONFLICT));
        } finally { workers.shutdownNow(); }
    }
}

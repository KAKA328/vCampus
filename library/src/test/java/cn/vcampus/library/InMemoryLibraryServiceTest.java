package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryLibraryServiceTest {
    private final InMemoryLibraryService service = new InMemoryLibraryService();

    @Test
    void searchFindsBooksByTitleAuthorIsbnAndCategory() {
        assertEquals(1, service.search("三体").getData().size());
        assertEquals(1, service.search("刘慈欣").getData().size());
        assertEquals(1, service.search("9787020002207").getData().size());
        assertEquals(2, service.search("计算机").getData().size());
    }

    @Test
    void emptyKeywordReturnsWholeCatalog() {
        assertEquals(5, service.search("").getData().size());
    }

    @Test
    void getBookReturnsDetailOrNotFound() {
        Book book = service.getBook("B001").getData();
        assertNotNull(book);
        assertEquals("机械工业出版社", book.getPublisher());
        assertEquals(StatusCode.NOT_FOUND, service.getBook("NOPE").getStatus());
    }

    @Test
    void listByCategoryFiltersBooks() {
        List<Book> books = service.listByCategory("科幻").getData();
        assertEquals(1, books.size());
        assertEquals("三体", books.get(0).getTitle());
    }

    @Test
    void addBookSucceedsAndDuplicateIsRejected() {
        Book book = new Book("B006", "小王子", "圣埃克苏佩里",
                "9787020042494", "文学", "人民文学出版社", 1, 1, "B-03");
        assertEquals(StatusCode.OK, service.addBook(book).getStatus());
        assertEquals(StatusCode.CONFLICT, service.addBook(book).getStatus());
    }

    @Test
    void borrowReturnsReceiptAndDecreasesAvailability() {
        ServiceResult<List<BorrowRecord>> result = service.borrow("student001", "B001");
        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(1, result.getData().size());
        assertEquals(30, result.getData().get(0).getDueDate().toEpochDay()
                - result.getData().get(0).getBorrowDate().toEpochDay());
        assertEquals(2, service.getBook("B001").getData().getAvailableCopies());
        assertEquals(StatusCode.CONFLICT, service.borrow("student001", "B001").getStatus());
    }

    @Test
    void borrowRejectedWhenNoCopyLeft() {
        assertEquals(StatusCode.OK, service.borrow("student001", "B002").getStatus());
        assertEquals(StatusCode.OK, service.borrow("student002", "B002").getStatus());
        assertEquals(StatusCode.CONFLICT, service.borrow("student003", "B002").getStatus());
    }

    @Test
    void returnUsesRecordIdRestoresCopyAndAllowsBorrowAgain() {
        BorrowRecord borrowed = service.borrow("student001", "B001").getData().get(0);
        ServiceResult<BorrowRecord> returned = service.returnBook("student001", borrowed.getRecordId());
        assertEquals(StatusCode.OK, returned.getStatus());
        assertTrue(returned.getData().isReturned());
        assertEquals(3, service.getBook("B001").getData().getAvailableCopies());
        assertEquals(StatusCode.OK, service.borrow("student001", "B001").getStatus());
    }

    @Test
    void anotherUserCannotReturnBorrowingRecord() {
        BorrowRecord borrowed = service.borrow("student001", "B001").getData().get(0);
        assertEquals(StatusCode.NOT_FOUND, service.returnBook("student002", borrowed.getRecordId()).getStatus());
        assertEquals(2, service.getBook("B001").getData().getAvailableCopies());
    }

    @Test
    void historyKeepsReturnedAndActiveRecords() {
        BorrowRecord first = service.borrow("student001", "B001").getData().get(0);
        service.returnBook("student001", first.getRecordId());
        service.borrow("student001", "B003");
        List<BorrowRecord> history = service.borrowHistory("student001").getData();
        assertEquals(2, history.size());
        assertTrue(history.get(0).isReturned());
        assertFalse(history.get(1).isReturned());
    }

    @Test
    void batchBorrowCreatesOneOrderWithMultipleRecords() {
        List<BorrowRecord> records = service.borrowBatch(
                "student001", Arrays.asList("B001", "B003")).getData();
        assertEquals(2, records.size());
        assertEquals(records.get(0).getOrderId(), records.get(1).getOrderId());
    }

    @Test
    void batchBorrowRejectsDuplicateBookIds() {
        assertEquals(StatusCode.BAD_REQUEST,
                service.borrowBatch("student001", Arrays.asList("B001", "B001")).getStatus());
    }

    @Test
    void batchBorrowIsAtomicWhenAnyBookIsMissing() {
        assertEquals(StatusCode.NOT_FOUND,
                service.borrowBatch("student001", Arrays.asList("B001", "NOPE")).getStatus());
        assertEquals(3, service.getBook("B001").getData().getAvailableCopies());
        assertTrue(service.borrowHistory("student001").getData().isEmpty());
    }

    @Test
    void allHistoryReturnsRecordsAcrossUsers() {
        service.borrow("student001", "B001");
        service.borrow("teacher001", "B003");
        assertEquals(2, service.allBorrowHistory().getData().size());
    }
}

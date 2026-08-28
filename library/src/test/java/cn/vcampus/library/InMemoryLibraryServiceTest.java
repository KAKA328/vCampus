package cn.vcampus.library;

import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        List<Book> sciFi = service.listByCategory("科幻").getData();
        assertEquals(1, sciFi.size());
        assertEquals("三体", sciFi.get(0).getTitle());
    }

    @Test
    void addBookSucceedsAndDuplicateIsRejected() {
        Book newBook = new Book("B006", "小王子", "圣埃克苏佩里",
                "9787020042494", "文学", "人民文学出版社", 1, 1, "B-03");
        assertEquals(StatusCode.OK, service.addBook(newBook).getStatus());
        assertEquals(StatusCode.CONFLICT, service.addBook(newBook).getStatus());
    }

    @Test
    void borrowDecreasesAvailabilityAndBlocksDuplicateBorrow() {
        assertEquals(StatusCode.OK, service.borrow("S001", "B001").getStatus());
        assertEquals(2, service.getBook("B001").getData().getAvailableCopies());
        assertEquals(StatusCode.CONFLICT, service.borrow("S001", "B001").getStatus());
    }

    @Test
    void borrowRejectedWhenNoCopyLeft() {
        assertEquals(StatusCode.OK, service.borrow("S001", "B002").getStatus());
        assertEquals(StatusCode.OK, service.borrow("S002", "B002").getStatus());
        assertEquals(StatusCode.CONFLICT, service.borrow("S003", "B002").getStatus());
    }

    @Test
    void returnBookRestoresCopyAndAllowsBorrowAgain() {
        service.borrow("S001", "B001");
        assertEquals(StatusCode.OK, service.returnBook("S001", "B001").getStatus());
        assertEquals(3, service.getBook("B001").getData().getAvailableCopies());
        assertEquals(StatusCode.OK, service.borrow("S001", "B001").getStatus());
    }

    @Test
    void returnWithoutActiveBorrowIsNotFound() {
        assertEquals(StatusCode.NOT_FOUND, service.returnBook("S001", "B001").getStatus());
    }

    @Test
    void borrowHistoryKeepsAllRecords() {
        service.borrow("S001", "B001");
        service.returnBook("S001", "B001");
        service.borrow("S001", "B003");
        List<BorrowRecord> history = service.borrowHistory("S001").getData();
        assertEquals(2, history.size());
        assertTrue(history.get(0).isReturned());
        assertFalse(history.get(1).isReturned());
    }

    @Test
    void borrowBatchCreatesOneOrderWithMultipleRecords() {
        assertEquals(StatusCode.OK, service.borrowBatch("S001", Arrays.asList("B001", "B003")).getStatus());
        List<BorrowRecord> history = service.borrowHistory("S001").getData();
        assertEquals(2, history.size());
        assertEquals(history.get(0).getOrderId(), history.get(1).getOrderId());
        assertFalse(history.get(0).getOrderId().isEmpty());
    }

    @Test
    void borrowBatchRejectsDuplicateBookIds() {
        assertEquals(StatusCode.BAD_REQUEST,
                service.borrowBatch("S001", Arrays.asList("B001", "B001")).getStatus());
    }
}

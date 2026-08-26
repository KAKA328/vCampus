package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;

class InMemoryLibraryServiceTest {
    @Test
    void borrowAndReturnBookUpdatesAvailableCopies() {
        InMemoryLibraryService service = new InMemoryLibraryService();

        assertEquals(StatusCode.OK, service.borrow("20230001", "B001").getStatus());
        assertEquals(1, service.search("Java").getData().get(0).getAvailableCopies());

        assertEquals(StatusCode.OK, service.returnBook("20230001", "B001").getStatus());
        assertEquals(2, service.search("Java").getData().get(0).getAvailableCopies());
    }

    @Test
    void rejectsBorrowWhenBookIsAlreadyBorrowedBySamePatron() {
        InMemoryLibraryService service = new InMemoryLibraryService();
        service.borrow("20230001", "B001");

        assertEquals(StatusCode.CONFLICT, service.borrow("20230001", "B001").getStatus());
    }
}

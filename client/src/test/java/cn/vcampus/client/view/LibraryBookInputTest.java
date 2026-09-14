package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 表单价格与既有 Book/服务端约束保持一致。 */
class LibraryBookInputTest {
    @Test
    void freeBooksAreAcceptedByTheSameParserUsedByTheAddDialog() {
        assertEquals(0.0d, LibraryBookInput.price("0"));
        assertEquals(0.0d, LibraryBookInput.price(" 0.00 "));
        assertEquals(0.0d, LibraryBookInput.price("-0"));
        Book free = new Book("FREE", "捐赠图书", "作者", "", "文学", "",
                LibraryBookInput.price("0"), 2, 2, "A");
        assertEquals(0.0d, free.getPrice());
        assertEquals(2, free.getAvailableCopies());
    }

    @Test
    void finitePositivePricesArePreserved() {
        assertEquals(35.05d, LibraryBookInput.price("35.05"));
    }

    @Test
    void negativeNonFiniteAndMalformedValuesRemainRejected() {
        for (String value : new String[] {"-0.01", "NaN", "Infinity", "-Infinity", "1e309", "", "abc", null}) {
            assertThrows(IllegalArgumentException.class, () -> LibraryBookInput.price(value));
        }
    }
}

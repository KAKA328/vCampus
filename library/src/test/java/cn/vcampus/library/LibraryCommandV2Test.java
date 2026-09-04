package cn.vcampus.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class LibraryCommandV2Test {
    @Test
    void borrowCommandCopiesBookList() {
        java.util.List<String> ids = new java.util.ArrayList<String>(Arrays.asList("B001", "B002"));
        LibraryBorrowV2Command command = new LibraryBorrowV2Command("token", ids);
        ids.clear();
        assertEquals(2, command.getBookIds().size());
    }

    @Test
    void commandsRejectBlankToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new LibraryQueryV2Command(" ", "Java"));
    }

    @Test
    void returnCommandUsesRecordId() {
        LibraryReturnV2Command command = new LibraryReturnV2Command("token", "BR-1");
        assertEquals("BR-1", command.getRecordId());
    }
}

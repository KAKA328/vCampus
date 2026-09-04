package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryPanelTest {
    @Test
    void onlyAdministrativeLibraryRolesCanManageCatalog() {
        assertTrue(LibraryPanel.canManage(Role.ADMIN));
        assertTrue(LibraryPanel.canManage(Role.LIBRARIAN));
        assertFalse(LibraryPanel.canManage(Role.STUDENT));
        assertFalse(LibraryPanel.canManage(Role.TEACHER));
    }

    @Test
    void bookRowPreservesInventoryColumns() {
        Book book = new Book("B-10", "测试驱动开发", "Kent Beck", "978-1", "计算机",
                "测试出版社", 5, 3, "A-01");

        Object[] row = LibraryPanel.bookRow(book);

        assertEquals("B-10", row[0]);
        assertEquals("测试驱动开发", row[1]);
        assertEquals(Integer.valueOf(5), row[6]);
        assertEquals(Integer.valueOf(3), row[7]);
        assertEquals("A-01", row[8]);
    }

    @Test
    void historyRowUsesRecordIdForReturnAndShowsActiveStatus() {
        BorrowRecord record = new BorrowRecord("BO-1", "BR-1", "student_1", "B-10",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), null, BorrowStatus.BORROWED);

        Object[] row = LibraryPanel.historyRow(record);

        assertEquals("BR-1", row[0]);
        assertEquals("BO-1", row[1]);
        assertEquals("student_1", row[2]);
        assertEquals("", row[6]);
        assertEquals("BORROWED", row[7]);
    }
}

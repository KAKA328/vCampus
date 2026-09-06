package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
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

    @Test
    void readerAndManagerUseDistinctTaskFocusedWorkspaces() {
        LibraryPanel reader = panel(Role.STUDENT);
        JTabbedPane readerTabs = findTabs(reader);
        Set<String> readerActions = buttonTexts(reader);

        assertEquals("找书借阅", readerTabs.getTitleAt(0));
        assertEquals("我的借阅", readerTabs.getTitleAt(1));
        assertTrue(readerActions.contains("借阅选中图书"));
        assertTrue(readerActions.contains("我的借阅记录"));
        assertFalse(readerActions.contains("新增图书"));
        assertFalse(readerActions.contains("全部借阅记录"));

        LibraryPanel manager = panel(Role.LIBRARIAN);
        JTabbedPane managerTabs = findTabs(manager);
        Set<String> managerActions = buttonTexts(manager);

        assertEquals("馆藏管理", managerTabs.getTitleAt(0));
        assertEquals("流通管理", managerTabs.getTitleAt(1));
        assertTrue(managerActions.contains("新增图书"));
        assertTrue(managerActions.contains("全部借阅记录"));
        assertFalse(managerActions.contains("借阅选中图书"));
        assertFalse(managerActions.contains("我的借阅记录"));
    }

    @Test
    void technicalBorrowStatesHaveReadableChineseLabels() {
        assertEquals("借阅中", LibraryPanel.borrowStatusLabel(BorrowStatus.BORROWED));
        assertEquals("已归还", LibraryPanel.borrowStatusLabel(BorrowStatus.RETURNED.name()));
    }

    @Test
    void visibleLibraryActionsRemainKeyboardFocusable() {
        assertAllButtonsFocusable(panel(Role.STUDENT));
        assertAllButtonsFocusable(panel(Role.LIBRARIAN));
    }

    private static LibraryPanel panel(Role role) {
        return new LibraryPanel("127.0.0.1", 19090,
                new Session("token", new User("user-001", "测试用户", role)));
    }

    private static JTabbedPane findTabs(Component component) {
        if (component instanceof JTabbedPane) return (JTabbedPane) component;
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                try {
                    return findTabs(child);
                } catch (IllegalStateException notHere) {
                    // Continue through the component tree.
                }
            }
        }
        throw new IllegalStateException("No tabbed pane found");
    }

    private static Set<String> buttonTexts(Component component) {
        Set<String> texts = new HashSet<String>();
        collectButtonTexts(component, texts);
        return texts;
    }

    private static void collectButtonTexts(Component component, Set<String> texts) {
        if (component instanceof JButton) {
            texts.add(((JButton) component).getText());
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectButtonTexts(child, texts);
            }
        }
    }

    private static void assertAllButtonsFocusable(Component component) {
        if (component instanceof JButton) {
            assertTrue(component.isFocusable(), ((JButton) component).getText() + " should accept keyboard focus");
            assertTrue(((JButton) component).isFocusPainted(),
                    ((JButton) component).getText() + " should show keyboard focus");
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                assertAllButtonsFocusable(child);
            }
        }
    }
}

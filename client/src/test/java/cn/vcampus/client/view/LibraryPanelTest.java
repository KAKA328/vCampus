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
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
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
                "测试出版社", 88.50d, 5, 3, "A-01");

        Object[] row = LibraryPanel.bookRow(book);

        assertEquals("B-10", row[0]);
        assertEquals("测试驱动开发", row[1]);
        assertEquals("￥88.50", row[3]);
        assertEquals(Integer.valueOf(5), row[7]);
        assertEquals(Integer.valueOf(3), row[8]);
        assertEquals("A-01", row[9]);
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
    void dueReminderSeparatesOverdueDueSoonAndReturnedRecords() {
        LocalDate today = LocalDate.of(2026, 9, 7);
        LibraryDueReminder.Summary summary = LibraryDueReminder.summarize(Arrays.asList(
                record("BR-overdue", today.minusDays(1), BorrowStatus.BORROWED),
                record("BR-soon", today.plusDays(3), BorrowStatus.BORROWED),
                record("BR-later", today.plusDays(8), BorrowStatus.BORROWED),
                returnedRecord("BR-returned", today.minusDays(5))), today);

        assertEquals(3, summary.getActiveCount());
        assertEquals(1, summary.getOverdueCount());
        assertEquals(1, summary.getDueSoonCount());
        assertEquals(today.minusDays(1), summary.getNearestDueDate());
        assertTrue(LibraryDueReminder.message(summary, false).contains("已逾期"));
        assertTrue(LibraryDueReminder.message(summary, false).contains("3 天内到期"));
    }

    @Test
    void dueReminderHandlesReadersWithoutActiveLoans() {
        LibraryDueReminder.Summary summary = LibraryDueReminder.summarize(
                Collections.singletonList(returnedRecord("BR-returned", LocalDate.of(2026, 9, 1))),
                LocalDate.of(2026, 9, 7));

        assertEquals(0, summary.getActiveCount());
        assertEquals("当前没有待归还图书。", LibraryDueReminder.message(summary, false));
    }

    @Test
    void visibleLibraryActionsRemainKeyboardFocusable() {
        assertAllButtonsFocusable(panel(Role.STUDENT));
        assertAllButtonsFocusable(panel(Role.LIBRARIAN));
    }

    @Test
    void narrowSearchBarKeepsEveryControlAccessibleAndTablesReadable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Role role : Arrays.asList(Role.STUDENT, Role.TEACHER, Role.LIBRARIAN)) {
                LibraryPanel panel = panel(role);
                JTabbedPane tabs = findTabs(panel);
                for (int width : new int[] {420, 1000}) {
                    for (int selected = 0; selected < tabs.getTabCount(); selected++) {
                        tabs.setSelectedIndex(selected);
                        panel.setSize(UiMetrics.dimension(width, 720));
                        // Width-dependent page heights settle after nested wrapping layouts run.
                        for (int pass = 0; pass < 8; pass++) layoutTree(panel);
                        JPanel search = findSearchBar(panel);
                        for (Component action : search.getComponents()) {
                            assertTrue(action.getX() >= 0);
                            assertTrue(action.getX() + action.getWidth() <= search.getWidth(),
                                    "search control must fit at logical width " + width);
                            assertTrue(action.getY() + action.getHeight() <= search.getHeight(),
                                    "wrapped controls must remain in the toolbar");
                        }
                        assertReadableTables(tabs);
                    }
                }
            }
        });
    }

    @Test
    void reminderWrapsAtNarrowWidthsAndTreatsUserTextAsPlainText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String message = "归还提醒：全校有 2 本图书已逾期，另有 3 本将在 3 天内到期，请尽快处理。";
            JLabel label = new LibraryWrappingLabel(message);
            VCampusTheme.statusPill(label, VCampusTheme.DANGER);
            label.setSize(UiMetrics.dimension(1000, 100));
            int wideHeight = label.getPreferredSize().height;
            label.setSize(UiMetrics.dimension(240, 100));
            assertTrue(label.getPreferredSize().height > wideHeight,
                    "the whole reminder must wrap instead of being clipped");
            assertEquals(message, label.getAccessibleContext().getAccessibleDescription());
            label.setText("<script>书名 & 作者</script>");
            assertTrue(label.getText().contains("&lt;script&gt;书名 &amp; 作者&lt;/script&gt;"));
        });
    }

    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) layoutTree((Container) child);
        }
    }

    private static JPanel findSearchBar(Container parent) {
        if ("librarySearchActions".equals(parent.getName())) return (JPanel) parent;
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                JPanel found = findSearchBar((Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertReadableTables(Container parent) {
        if (parent instanceof JTable) {
            JTable table = (JTable) parent;
            assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
            int logicalMinimum = table.getColumnCount() == 10 ? 64 : 82;
            for (int column = 0; column < table.getColumnCount(); column++) {
                assertTrue(table.getColumnModel().getColumn(column).getWidth()
                        >= UiMetrics.px(logicalMinimum));
            }
            assertTrue(table.getParent().getParent() instanceof JScrollPane);
        }
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) assertReadableTables((Container) child);
        }
    }

    private static LibraryPanel panel(Role role) {
        return new LibraryPanel("127.0.0.1", 19090,
                new Session("token", new User("user-001", "测试用户", role)));
    }

    private static BorrowRecord record(String id, LocalDate dueDate, BorrowStatus status) {
        LocalDate borrowDate = dueDate.minusDays(30);
        return new BorrowRecord("BO-1", id, "student_1", "B-10", borrowDate, dueDate,
                status == BorrowStatus.RETURNED ? dueDate.minusDays(1) : null, status);
    }

    private static BorrowRecord returnedRecord(String id, LocalDate dueDate) {
        return record(id, dueDate, BorrowStatus.RETURNED);
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

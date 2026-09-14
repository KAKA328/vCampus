package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryCatalogV4PanelTest {
    private LibraryPanel panel(Role role) {
        return new LibraryPanel("127.0.0.1", 1, new Session("token", new User("reader", "读者", role)), LibraryReminderState.memoryOnly());
    }
    @Test void onlyManagerHasEditAndPhysicalCopyButtonsAndBusyStateDisablesThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Role role : new Role[] {Role.STUDENT, Role.TEACHER, Role.LIBRARIAN, Role.ADMIN}) {
                LibraryPanel panel = panel(role);
                boolean manager = LibraryPanel.canManage(role);
                assertEquals(manager, hasButton(panel, "编辑资料"));
                assertEquals(manager, hasButton(panel, "实体副本"));
                panel.state.requestInProgress = true;
                LibraryRequestRunner.updateButtonState(panel.state);
                assertFalse(panel.state.editBookButton.isEnabled());
                assertFalse(panel.state.copiesButton.isEnabled());
            }
        });
    }
    @Test void editorAcceptsZeroPriceAndCannotChangeOriginalIdOrInventory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Book original = new Book("BOOK", "旧名称", "作者", "ISBN", "历史", "出版社", 12.5, 5, 2, "A");
            LibraryBookEditForm form = new LibraryBookEditForm(original);
            assertEquals("历史", form.category.getSelectedItem());
            form.title.setText("新名称"); form.price.setText("0");
            Book changed = form.replacement();
            assertEquals(0, changed.getPrice()); assertEquals("新名称", changed.getTitle());
            assertEquals("BOOK", changed.getBookId()); assertEquals(5, changed.getTotalCopies()); assertEquals(2, changed.getAvailableCopies());
            form.price.setText("-1"); assertThrows(IllegalArgumentException.class, form::replacement);
        });
    }
    @Test void snapshotTitleWinsOverChangedCatalogAndLegacyBackfillIsClearlyLabelled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.STUDENT);
            panel.showBooks(ok(MessageType.LIBRARY_QUERY_V2, Collections.singletonList(new Book("BOOK", "当前新名", "作者"))));
            BorrowRecord first = new BorrowRecord("O", "R1", "reader", "BOOK", LocalDate.now(), LocalDate.now().plusDays(30), null, BorrowStatus.BORROWED);
            BorrowRecord second = new BorrowRecord("O", "R2", "reader", "BOOK", LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), null, BorrowStatus.BORROWED);
            panel.showHistory(ok(MessageType.LIBRARY_HISTORY_V4, Arrays.asList(
                    new LibraryLoanSnapshot(first, "借阅时旧名", "CP-one", false),
                    new LibraryLoanSnapshot(second, "迁移时名称", null, true))), "我的借阅");
            assertEquals("借阅时旧名", panel.state.historyModel.getValueAt(0, 4));
            assertEquals("CP-one", panel.state.historyModel.getValueAt(0, 9));
            assertEquals("借阅时快照", panel.state.historyModel.getValueAt(0, 10));
            assertEquals("历史未记录", panel.state.historyModel.getValueAt(1, 9));
            assertEquals("迁移回填", panel.state.historyModel.getValueAt(1, 10));
            assertEquals("R1", panel.state.historyModel.getValueAt(0, 0));
        });
    }
    @Test void physicalCopyTableHasReadableStatusesAndEnoughWidthForIds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Container panel = LibraryCatalogV4Actions.copiesPanel("BOOK", Arrays.asList(
                    new LibraryCopy("CP-one", "BOOK", LibraryCopyStatus.AVAILABLE),
                    new LibraryCopy("CP-two", "BOOK", LibraryCopyStatus.LOST),
                    new LibraryCopy("CP-three", "BOOK", LibraryCopyStatus.UNAVAILABLE)));
            JTable table = table(panel);
            assertNotNull(table); assertEquals(3, table.getRowCount());
            assertEquals("在馆可借", table.getValueAt(0, 2)); assertEquals("已遗失", table.getValueAt(1, 2));
            assertEquals("旧库存待核对", table.getValueAt(2, 2));
            assertTrue(table.getColumnModel().getColumn(0).getMinWidth() >= UiMetrics.px(360));
        });
    }
    private Message ok(MessageType type, Object payload) { return Message.response(Message.request("ui", type, null), StatusCode.OK, payload); }
    private boolean hasButton(Container parent, String label) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton && label.equals(((JButton) child).getText())) return true;
            if (child instanceof Container && hasButton((Container) child, label)) return true;
        }
        return false;
    }
    private JTable table(Container parent) {
        if (parent instanceof JTable) return (JTable) parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) {
            JTable found = table((Container) child); if (found != null) return found;
        }
        return null;
    }
}

package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.awt.Container;
import java.time.LocalDate;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 图书馆对话框、分类选择与提醒操作的交互回归检查。 */
class LibraryInteractionTest {
    @Test
    void categoriesAreSelectableForSearchAndCreation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<String> search = LibraryPanel.categoryChoices(true);
            JComboBox<String> create = LibraryPanel.categoryChoices(false);
            assertFalse(search.isEditable());
            assertFalse(create.isEditable());
            assertEquals("全部分类", search.getSelectedItem());
            assertEquals(10, search.getItemCount());
            assertEquals(9, create.getItemCount());
            search.setSelectedItem("科幻");
            assertEquals("科幻", search.getSelectedItem());
            create.setSelectedItem("文学");
            assertEquals("文学", create.getSelectedItem());
        });
    }

    @Test
    void oldCatalogCategoriesRemainSelectableAndFiltersCanBeReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.STUDENT);
            Book book = new Book("B-CUSTOM", "旧库分类示例", "演示作者", "DEMO-CUSTOM", "地方文献",
                    "演示出版社", 20.0d, 2, 2, "D-02");
            Message catalog = Message.response(Message.request("ui", MessageType.LIBRARY_QUERY_V2, null),
                    StatusCode.OK, Arrays.asList(book));
            panel.showBooks(catalog);
            JComboBox<?> choices = findType(panel, JComboBox.class);
            assertEquals(11, choices.getItemCount());
            choices.setSelectedItem("地方文献");
            panel.showBooks(catalog);
            assertEquals(11, choices.getItemCount());
            assertEquals("地方文献", choices.getSelectedItem());
            findButton(panel, "清空筛选").doClick();
            assertEquals("全部分类", choices.getSelectedItem());
        });
    }

    @Test
    void detailsKeepLongValuesInSeparateRowsAndScrollOnSmallWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Book longBook = new Book("B-LONG", repeat("超长书名与设计实现说明", 6), repeat("作者姓名", 4),
                    "DEMO-DETAIL", "自然科学", repeat("出版社信息与发行部门", 8), 66.50d, 8, 3,
                    repeat("三楼东侧自然科学区域", 8));
            for (int width : new int[] {360, 620}) {
                JPanel detail = LibraryPanel.bookDetailPanel(longBook);
                JScrollPane scroll = VCampusTheme.pageScroll(detail);
                scroll.setSize(UiMetrics.dimension(width, 260));
                for (int pass = 0; pass < 20; pass++) layoutTree(scroll);
                JPanel fields = (JPanel) findNamed(detail, "libraryDetailFields");
                int previousBottom = -1;
                for (int row = 0; row < fields.getComponentCount(); row += 2) {
                    Component key = fields.getComponent(row);
                    Component value = fields.getComponent(row + 1);
                    assertTrue(key.getY() > previousBottom, "detail rows must not overlap");
                    assertTrue(value.getX() >= key.getX() + key.getWidth());
                    assertTrue(value.getX() + value.getWidth() <= fields.getWidth());
                    assertTrue(value.getHeight() >= value.getPreferredSize().height,
                            "long detail value must retain its wrapped height");
                    previousBottom = Math.max(key.getY() + key.getHeight(), value.getY() + value.getHeight());
                }
                assertTrue(scroll.getVerticalScrollBar().isVisible());
                assertTrue(detail.getHeight() > scroll.getViewport().getHeight());
                assertWrappedLabelsFit(detail);
            }
        });
    }

    @Test
    void onlyManagersHaveRestockAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertNull(findButton(panel(Role.STUDENT), "增加库存"));
            assertNull(findButton(panel(Role.TEACHER), "增加库存"));
            assertNotNull(findButton(panel(Role.LIBRARIAN), "增加库存"));
            assertNotNull(findButton(panel(Role.ADMIN), "增加库存"));
        });
    }

    @Test
    void reminderActionFindsTheEarliestLoanWithoutReturningIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.STUDENT);
            BorrowRecord later = record("BR-soon", LocalDate.now().plusDays(2));
            BorrowRecord earlier = record("BR-overdue", LocalDate.now().minusDays(1));
            panel.showHistory(history(later, earlier), "我的借阅记录");
            findButton(panel, "去归还").doClick();
            JTabbedPane tabs = findType(panel, JTabbedPane.class);
            assertEquals(1, tabs.getSelectedIndex());
            JTable table = findType((Container) tabs.getComponentAt(1), JTable.class);
            int selected = table.convertRowIndexToModel(table.getSelectedRow());
            assertEquals("BR-overdue", table.getModel().getValueAt(selected, 0));
            assertEquals("BORROWED", table.getModel().getValueAt(selected, 8));
        });
    }

    @Test
    void returnedRecordsAutomaticallyRemoveTheReminderCard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.TEACHER);
            BorrowRecord active = record("BR-active", LocalDate.now().plusDays(2));
            panel.showHistory(history(active), "我的借阅记录");
            assertTrue(findNamed(panel, "libraryReminderCard").isVisible());
            panel.showHistory(history(active.returned(LocalDate.now())), "我的借阅记录");
            assertFalse(findNamed(panel, "libraryReminderCard").isVisible());
        });
    }

    @Test
    void acknowledgingHidesTheCardAcrossRefreshWithoutReturningBooks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryReminderState state = LibraryReminderState.memoryOnly();
            Session session = new Session("ui-test", new User("library-ui-test", "测试用户", Role.STUDENT));
            LibraryPanel panel = new LibraryPanel("127.0.0.1", 19997, session, state);
            BorrowRecord active = record("BR-ack", LocalDate.now().plusDays(2));
            panel.showHistory(history(active), "我的借阅记录");
            findButton(panel, "今日已读").doClick();
            assertFalse(findNamed(panel, "libraryReminderCard").isVisible());
            assertEquals(BorrowStatus.BORROWED, active.getStatus());
            panel.showHistory(history(active), "我的借阅记录");
            assertFalse(findNamed(panel, "libraryReminderCard").isVisible());
            LibraryPanel reopened = new LibraryPanel("127.0.0.1", 19997, session, state);
            reopened.showHistory(history(active), "我的借阅记录");
            assertFalse(findNamed(reopened, "libraryReminderCard").isVisible());
            reopened.refreshReminderDate(LocalDate.now().plusDays(1));
            assertTrue(findNamed(reopened, "libraryReminderCard").isVisible());
            reopened.showHistory(history(active, record("BR-new", LocalDate.now())), "我的借阅记录");
            assertTrue(findNamed(reopened, "libraryReminderCard").isVisible());
        });
    }

    private static LibraryPanel panel(Role role) {
        return new LibraryPanel("127.0.0.1", 19997,
                new Session("ui-test", new User("library-ui-test", "测试用户", role)), LibraryReminderState.memoryOnly());
    }

    private static BorrowRecord record(String id, LocalDate dueDate) {
        return new BorrowRecord("BO-ui", id, "library-ui-test", "B001", dueDate.minusDays(30),
                dueDate, null, BorrowStatus.BORROWED);
    }

    private static Message history(BorrowRecord... records) {
        return Message.response(Message.request("ui", MessageType.LIBRARY_HISTORY_V3, null),
                StatusCode.OK, Arrays.asList(records));
    }

    private static String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < count; index++) result.append(text);
        return result.toString();
    }

    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) layoutTree((Container) child);
        }
    }

    private static void assertWrappedLabelsFit(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof LibraryWrappingLabel) {
                assertTrue(child.getHeight() >= child.getPreferredSize().height,
                        ((JLabel) child).getText() + " must not be clipped");
            }
            if (child instanceof Container) assertWrappedLabelsFit((Container) child);
        }
    }

    private static Component findNamed(Container parent, String name) {
        if (name.equals(parent.getName())) return parent;
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                Component found = findNamed((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JButton findButton(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) {
                JButton found = findButton((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T findType(Container parent, Class<T> type) {
        if (type.isInstance(parent)) return type.cast(parent);
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                T found = findType((Container) child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}

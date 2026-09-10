package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryHistoryV3Command;
import cn.vcampus.library.LibraryQueryV2Command;
import cn.vcampus.library.LibraryReturnV2Command;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.awt.Container;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖真实客户端后台请求、分类隔离，以及新增书名列后的归还定位。 */
class LibraryHistoryTitleTest {
    private static final Book TITLE = new Book("B003", "红楼梦", "曹雪芹");
    private static final Book FILTERED = new Book("B004", "三体", "刘慈欣");
    private static final BorrowRecord LOAN = new BorrowRecord("BO-1", "BR-target", "reader", "B003",
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(20), null, BorrowStatus.BORROWED);

    @Test
    void everyLibraryRoleLoadsTitlesWithoutReplacingTheFilteredCatalog() throws Exception {
        for (Role role : Arrays.asList(Role.STUDENT, Role.TEACHER, Role.LIBRARIAN, Role.ADMIN)) {
            exercise(role, StatusCode.OK, Collections.singletonList(TITLE), false, "红楼梦", false);
        }
    }

    @Test
    void catalogErrorDoesNotDiscardHistoryOrDisableReturn() throws Exception {
        exercise(Role.STUDENT, StatusCode.SERVER_ERROR, "temporarily unavailable", false, "书名暂不可用", false);
    }

    @Test
    void invalidCatalogPayloadDoesNotDiscardHistory() throws Exception {
        exercise(Role.TEACHER, StatusCode.OK, Arrays.asList(TITLE, "invalid book"), false, "书名暂不可用", false);
    }

    @Test
    void disconnectedCatalogRequestDoesNotDiscardHistory() throws Exception {
        exercise(Role.STUDENT, StatusCode.OK, null, true, "书名暂不可用", false);
    }

    @Test
    void missingCatalogEntryHasAnExplicitFallback() throws Exception {
        exercise(Role.LIBRARIAN, StatusCode.OK, Collections.emptyList(), false, "书名暂不可用", false);
    }

    @Test
    void returnUsesRecordIdRatherThanTheLeadingTitleColumn() throws Exception {
        exercise(Role.STUDENT, StatusCode.OK, Collections.singletonList(TITLE), false, "红楼梦", true);
    }

    private static void exercise(Role role, StatusCode catalogStatus, Object catalogPayload,
            boolean disconnect, String expectedTitle, boolean returnAfterLoad) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try {
                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(5000);
                        try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                            Message history = (Message) input.readObject();
                            assertEquals(MessageType.LIBRARY_HISTORY_V3, history.getType());
                            LibraryHistoryV3Command historyCommand = (LibraryHistoryV3Command) history.getPayload();
                            assertEquals("history-test", historyCommand.getToken());
                            assertEquals(LibraryPanel.canManage(role), historyCommand.isAllUsers());
                            output.writeObject(Message.response(history, StatusCode.OK, Collections.singletonList(LOAN)));
                            output.flush();
                            Message query = (Message) input.readObject();
                            assertEquals(MessageType.LIBRARY_QUERY_V2, query.getType());
                            LibraryQueryV2Command command = (LibraryQueryV2Command) query.getPayload();
                            assertTrue(command.getKeyword() == null || command.getKeyword().isEmpty());
                            assertTrue(command.getCategory() == null || command.getCategory().isEmpty());
                            if (!disconnect) {
                                output.writeObject(Message.response(query, catalogStatus, catalogPayload));
                                output.flush();
                            }
                        }
                    }
                    if (returnAfterLoad) {
                        try (Socket socket = listener.accept();
                                ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                            socket.setSoTimeout(5000);
                            Message request = (Message) input.readObject();
                            assertEquals(MessageType.LIBRARY_RETURN_V2, request.getType());
                            assertEquals("BR-target", ((LibraryReturnV2Command) request.getPayload()).getRecordId());
                            // 不触发下一轮自动刷新；这里只验证客户端发送的归还对象。
                            output.writeObject(Message.response(request, StatusCode.CONFLICT, "test complete"));
                            output.flush();
                        }
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            LibraryPanel[] panel = new LibraryPanel[1];
            JTable[] historyTable = new JTable[1];
            CountDownLatch rowsLoaded = new CountDownLatch(1);
            CountDownLatch requestFinished = new CountDownLatch(1);
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = new LibraryPanel("127.0.0.1", listener.getLocalPort(),
                        new Session("history-test", new User("reader", "测试读者", role)), LibraryReminderState.memoryOnly());
                panel[0].showBooks(Message.response(Message.request("filtered", MessageType.LIBRARY_QUERY_V2, null),
                        StatusCode.OK, Collections.singletonList(FILTERED)));
                JComboBox<?> categories = find(panel[0], JComboBox.class);
                categories.setSelectedItem("科幻");
                JTabbedPane tabs = find(panel[0], JTabbedPane.class);
                tabs.setSelectedIndex(1);
                historyTable[0] = find((Container) tabs.getComponentAt(1), JTable.class);
                historyTable[0].getModel().addTableModelListener(event -> {
                    if (historyTable[0].getRowCount() > 0) rowsLoaded.countDown();
                });
                JButton button = button(panel[0], LibraryPanel.canManage(role) ? "全部借阅记录" : "我的借阅记录");
                button.addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue())) requestFinished.countDown();
                });
                button.doClick();
            });
            assertTrue(rowsLoaded.await(5, TimeUnit.SECONDS), "history should survive catalog failures");
            assertTrue(requestFinished.await(5, TimeUnit.SECONDS), "history load should finish");
            SwingUtilities.invokeAndWait(() -> {
                JTable history = historyTable[0];
                assertEquals("图书名称", history.getColumnName(0));
                assertEquals(expectedTitle, history.getValueAt(0, 0));
                assertEquals("B003", history.getModel().getValueAt(0, 3));
                assertEquals("BR-target", history.getModel().getValueAt(0, 0));
                assertEquals("BORROWED", history.getModel().getValueAt(0, 8));
                assertTrue(history.getColumnModel().getColumn(0).getMinWidth() >= UiMetrics.px(220));
                JTable catalog = find((Container) find(panel[0], JTabbedPane.class).getComponentAt(0), JTable.class);
                assertEquals(1, catalog.getRowCount());
                assertEquals("三体", catalog.getValueAt(0, 1));
                assertEquals("科幻", find(panel[0], JComboBox.class).getSelectedItem());
                assertTrue(button(panel[0], "归还选中记录").isEnabled());
                if (returnAfterLoad) {
                    history.getRowSorter().toggleSortOrder(4);
                    history.setRowSelectionInterval(0, 0);
                    button(panel[0], "归还选中记录").doClick();
                }
            });
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    private static JButton button(Container root, String text) {
        if (root instanceof JButton && text.equals(((JButton) root).getText())) return (JButton) root;
        for (Component child : root.getComponents()) {
            if (child instanceof Container) {
                JButton found = button((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static <T> T find(Container root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        for (Component child : root.getComponents()) {
            if (child instanceof Container) {
                T found = find((Container) child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}

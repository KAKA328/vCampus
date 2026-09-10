package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryCompensationListV3Command;
import cn.vcampus.library.LibraryCompensationPayV3Command;
import cn.vcampus.store.WalletTransactionType;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.awt.Container;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LibraryCompensationPanelTest {
    @Test
    void enteringCompensationWhileBusyQueuesAReadInsteadOfDroppingIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                LibraryPanel panel = panel(Role.STUDENT, 19997);
                java.lang.reflect.Field busy = LibraryPanel.class.getDeclaredField("requestInProgress");
                busy.setAccessible(true);
                busy.setBoolean(panel, true);
                java.lang.reflect.Method load = LibraryPanel.class.getDeclaredMethod("loadCompensations");
                load.setAccessible(true);
                load.invoke(panel);
                java.lang.reflect.Field queued = LibraryPanel.class.getDeclaredField("compensationRefreshQueued");
                queued.setAccessible(true);
                assertTrue(queued.getBoolean(panel));
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @Test
    void readersPayOnlyTheirOwnBillsAndManagersNeverReceiveADebitButton() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Role role : Arrays.asList(Role.STUDENT, Role.TEACHER, Role.LIBRARIAN, Role.ADMIN)) {
                LibraryPanel panel = panel(role, 19997);
                boolean manager = LibraryPanel.canManage(role);
                JTabbedPane tabs = find(panel, JTabbedPane.class);
                assertEquals("遗失赔偿", tabs.getTitleAt(2));
                assertEquals(manager, button(panel, "确认遗失") != null);
                assertEquals(!manager, button(panel, "确认支付") != null);
                assertEquals(!manager, button(panel, "如何充值") != null);
                assertTrue(panel.showCompensations(ok(Arrays.asList(
                        bill("LC-own", "reader", 3500, CompensationStatus.PENDING),
                        bill("LC-other", "other", 3500, CompensationStatus.PENDING)))));
                JTable table = compensationTable(panel);
                assertEquals(manager ? 2 : 1, table.getRowCount());
                assertEquals("图书名称", table.getColumnName(0));
                assertEquals("测试图书", table.getValueAt(0, 0));
                assertEquals("原价赔偿金额", table.getColumnName(1));
                assertEquals("￥35.00", table.getValueAt(0, 1));
                assertEquals("状态", table.getColumnName(2));
                if (!manager) {
                    assertFalse(button(panel, "确认支付").isEnabled());
                    table.setRowSelectionInterval(0, 0);
                    assertTrue(button(panel, "确认支付").isEnabled());
                }
            }
        });
    }

    @Test
    void exactCentAmountsAndZeroSettlementDoNotUseFloatingPoint() throws Exception {
        assertEquals("￥0.00", LibraryPanel.formatCents(0));
        assertEquals("￥0.01", LibraryPanel.formatCents(1));
        assertEquals("￥35.05", LibraryPanel.formatCents(3505));
        assertEquals("￥92233720368547758.07", LibraryPanel.formatCents(Long.MAX_VALUE));
        LibraryCompensation zero = bill("LC-zero", "reader", 0, CompensationStatus.PENDING);
        String text = LibraryPanel.paymentConfirmationText(zero, Long.valueOf(0));
        assertTrue(text.contains("不扣除钱包余额"));
        assertTrue(text.contains("￥0.00"));
        assertFalse(text.contains("余额不足不会扣款"));
        LibraryCompensation priced = bill("LC-priced", "reader", 3505, CompensationStatus.PENDING);
        assertTrue(LibraryPanel.paymentConfirmationText(priced, Long.valueOf(5010)).contains("￥35.05"));
        assertTrue(LibraryPanel.paymentConfirmationText(priced, Long.valueOf(5010)).contains("￥50.10"));
        assertTrue(LibraryPanel.paymentConfirmationText(priced, null).contains("与商店共用"));
        assertEquals("￥35.05", LibraryPanel.compensationRow(priced)[4]);
        assertEquals("图书赔偿", StoreRowMapper.transactionTypeName(WalletTransactionType.LIBRARY_LOSS));
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.STUDENT, 19997);
            panel.showCompensations(ok(Collections.singletonList(zero)));
            compensationTable(panel).setRowSelectionInterval(0, 0);
            assertTrue(button(panel, "确认结清").isEnabled());
        });
    }

    @Test
    void paidOrFailedToRefreshBillsCannotBePaidFromStaleRows() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.STUDENT, 19997);
            panel.showCompensations(ok(Collections.singletonList(bill("LC-1", "reader", 3500, CompensationStatus.PAID))));
            compensationTable(panel).setRowSelectionInterval(0, 0);
            assertFalse(button(panel, "确认支付").isEnabled());
            panel.showCompensations(ok(Collections.singletonList(bill("LC-1", "reader", 3500, CompensationStatus.PENDING))));
            compensationTable(panel).setRowSelectionInterval(0, 0);
            assertTrue(button(panel, "确认支付").isEnabled());
            assertFalse(panel.showCompensations(null));
            assertFalse(button(panel, "确认支付").isEnabled());
            assertEquals(1, compensationTable(panel).getRowCount(), "stale rows remain visible only for reference");
            assertFalse(panel.showCompensations(ok(Arrays.asList("invalid bill"))));
            assertFalse(button(panel, "确认支付").isEnabled());
        });
    }

    @Test
    void walletDisplayDistinguishesZeroFromAnUnavailableBalance() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LibraryPanel panel = panel(Role.TEACHER, 19997);
            JLabel balance = (JLabel) named(panel, "libraryWalletBalance");
            assertTrue(panel.showWalletBalance(ok(Long.valueOf(0))));
            assertTrue(balance.getText().contains("￥0.00"));
            assertTrue(panel.showWalletBalance(ok(Long.valueOf(12345))));
            assertTrue(balance.getText().contains("￥123.45"));
            assertFalse(panel.showWalletBalance(ok(Integer.valueOf(12345))));
            assertTrue(balance.getText().contains("暂不可用"));
            assertFalse(panel.showWalletBalance(null));
            assertFalse(panel.showWalletBalance(ok(Long.valueOf(-1))));
        });
    }

    @Test
    void lostAndCompensatedLoansAreReadableAndNeverRemainInReturnReminders() throws Exception {
        assertEquals("已遗失·待赔偿", LibraryPanel.borrowStatusLabel(BorrowStatus.LOST));
        assertEquals("已赔偿", LibraryPanel.borrowStatusLabel(BorrowStatus.COMPENSATED.name()));
        SwingUtilities.invokeAndWait(() -> {
            for (Role role : Arrays.asList(Role.STUDENT, Role.LIBRARIAN)) {
                LibraryPanel panel = panel(role, 19997);
                panel.showHistory(ok(Arrays.asList(loan(BorrowStatus.BORROWED))), "测试记录");
                assertTrue(named(panel, "libraryReminderCard").isVisible());
                panel.showHistory(ok(Arrays.asList(loan(BorrowStatus.LOST), loan(BorrowStatus.COMPENSATED))), "测试记录");
                assertFalse(named(panel, "libraryReminderCard").isVisible());
                JTable table = find((Container) find(panel, JTabbedPane.class).getComponentAt(1), JTable.class);
                table.setRowSelectionInterval(0, 0);
                // 非借阅状态在本地就拒绝归还，不会发出网络请求。
                button(panel, "归还选中记录").doClick();
                assertEquals("LOST", table.getModel().getValueAt(0, 8));
                assertEquals("BR-1", table.getModel().getValueAt(0, 0));
            }
        });
    }

    @Test
    void insufficientBalancePreventsDoubleSubmissionAndRequiresRefresh() throws Exception {
        exerciseFailedPayment(false);
    }

    @Test
    void disconnectedPaymentIsNotAutomaticallyRetried() throws Exception {
        exerciseFailedPayment(true);
    }

    private static void exerciseFailedPayment(boolean disconnect) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try {
                    try (Socket socket = listener.accept()) {
                        socket.setSoTimeout(5000);
                        try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                            Message request = (Message) input.readObject();
                            assertEquals(MessageType.LIBRARY_COMPENSATION_PAY_V3, request.getType());
                            assertEquals("LC-1", ((LibraryCompensationPayV3Command) request.getPayload()).getCompensationId());
                            received.countDown();
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            if (!disconnect) {
                                output.writeObject(Message.response(request, StatusCode.PAYMENT_REQUIRED, "insufficient"));
                                output.flush();
                            }
                        }
                    }
                    listener.setSoTimeout(400);
                    assertThrows(SocketTimeoutException.class, () -> {
                        try (Socket unexpected = listener.accept()) { fail("payment must not be retried automatically"); }
                    });
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            LibraryPanel[] panel = new LibraryPanel[1];
            LibraryCompensation pending = bill("LC-1", "reader", 3500, CompensationStatus.PENDING);
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = panel(Role.STUDENT, listener.getLocalPort());
                panel[0].showCompensations(ok(Collections.singletonList(pending)));
                compensationTable(panel[0]).setRowSelectionInterval(0, 0);
                JButton refresh = button(panel[0], "刷新赔偿与余额");
                refresh.addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue())) done.countDown();
                });
                panel[0].submitCompensationPayment(pending);
                panel[0].submitCompensationPayment(pending);
                assertFalse(button(panel[0], "确认支付").isEnabled());
                assertFalse(refresh.isEnabled());
            });
            assertTrue(received.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(button(panel[0], "确认支付").isEnabled());
                assertTrue(button(panel[0], "刷新赔偿与余额").isEnabled());
            });
            server.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    void successfulPaymentRefreshesHistoryCatalogBillAndSameWalletInOneSequence() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        LibraryCompensation pending = bill("LC-1", "reader", 3500, CompensationStatus.PENDING);
        LibraryCompensation paid = bill("LC-1", "reader", 3500, CompensationStatus.PAID);
        CountDownLatch refreshed = new CountDownLatch(1);
        try (ServerSocket listener = new ServerSocket(0)) {
            listener.setSoTimeout(5000);
            Future<?> server = worker.submit(() -> {
                try {
                    try (Socket socket = listener.accept();
                            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        socket.setSoTimeout(5000);
                        Message request = (Message) input.readObject();
                        assertEquals(MessageType.LIBRARY_COMPENSATION_PAY_V3, request.getType());
                        output.writeObject(Message.response(request, StatusCode.OK, paid));
                        output.flush();
                    }
                    try (Socket socket = listener.accept();
                            ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
                            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
                        socket.setSoTimeout(5000);
                        MessageType[] types = {MessageType.LIBRARY_HISTORY_V3, MessageType.LIBRARY_QUERY_V2,
                                MessageType.LIBRARY_COMPENSATION_LIST_V3, MessageType.LIBRARY_WALLET_QUERY_V3};
                        Object[] values = {Collections.singletonList(loan(BorrowStatus.COMPENSATED)),
                                Collections.singletonList(new Book("B001", "测试图书", "作者", "", "文学", "", 35.0, 1, 0, "A1")),
                                Collections.singletonList(paid), Long.valueOf(6500)};
                        for (int index = 0; index < types.length; index++) {
                            Message request = (Message) input.readObject();
                            assertEquals(types[index], request.getType());
                            if (index == 2) assertFalse(((LibraryCompensationListV3Command) request.getPayload()).isAllUsers());
                            output.writeObject(Message.response(request, StatusCode.OK, values[index]));
                            output.flush();
                        }
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            LibraryPanel[] panel = new LibraryPanel[1];
            SwingUtilities.invokeAndWait(() -> {
                panel[0] = panel(Role.STUDENT, listener.getLocalPort());
                panel[0].showHistory(ok(Collections.singletonList(loan(BorrowStatus.BORROWED))), "我的借阅记录");
                panel[0].showCompensations(ok(Collections.singletonList(pending)));
                JLabel balance = (JLabel) named(panel[0], "libraryWalletBalance");
                balance.addPropertyChangeListener("text", event -> {
                    if (String.valueOf(event.getNewValue()).contains("￥65.00")) refreshed.countDown();
                });
                panel[0].submitCompensationPayment(pending);
            });
            assertTrue(refreshed.await(5, TimeUnit.SECONDS), "all dependent views must refresh after payment");
            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = find(panel[0], JTabbedPane.class);
                JTable history = find((Container) tabs.getComponentAt(1), JTable.class);
                assertEquals("测试图书", history.getModel().getValueAt(0, 4));
                assertEquals("COMPENSATED", history.getModel().getValueAt(0, 8));
                assertEquals("PAID", compensationTable(panel[0]).getModel().getValueAt(0, 5));
                assertFalse(named(panel[0], "libraryReminderCard").isVisible());
                assertFalse(button(panel[0], "确认支付").isEnabled());
                JTable catalog = find((Container) tabs.getComponentAt(0), JTable.class);
                assertEquals(Integer.valueOf(0), catalog.getModel().getValueAt(0, 8));
            });
            server.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    private static LibraryCompensation bill(String id, String user, long cents, CompensationStatus status) {
        LocalDateTime created = LocalDateTime.of(2026, 9, 9, 10, 0);
        return new LibraryCompensation(id, "BR-1", user, "B001", "测试图书", cents, status,
                "librarian", created, status == CompensationStatus.PAID ? created.plusMinutes(5) : null);
    }

    private static BorrowRecord loan(BorrowStatus status) {
        LocalDate today = LocalDate.now();
        return new BorrowRecord("BO-1", "BR-1", "reader", "B001", today.minusDays(31), today.minusDays(1), null, status);
    }

    private static Message ok(Object payload) {
        return Message.response(Message.request("test", MessageType.LIBRARY_COMPENSATION_LIST_V3, null), StatusCode.OK, payload);
    }

    private static LibraryPanel panel(Role role, int port) {
        return new LibraryPanel("127.0.0.1", port, new Session("token", new User("reader", "测试读者", role)),
                LibraryReminderState.memoryOnly());
    }

    private static JTable compensationTable(LibraryPanel panel) { return (JTable) named(panel, "libraryCompensationTable"); }

    private static Component named(Container parent, String name) {
        if (name.equals(parent.getName())) return parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) {
            Component found = named((Container) child, name);
            if (found != null) return found;
        }
        return null;
    }

    private static JButton button(Container parent, String text) {
        if (parent instanceof JButton && text.equals(((JButton) parent).getText())) return (JButton) parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) {
            JButton found = button((Container) child, text);
            if (found != null) return found;
        }
        return null;
    }

    private static <T> T find(Container parent, Class<T> type) {
        if (type.isInstance(parent)) return type.cast(parent);
        for (Component child : parent.getComponents()) if (child instanceof Container) {
            T found = find((Container) child, type);
            if (found != null) return found;
        }
        return null;
    }
}

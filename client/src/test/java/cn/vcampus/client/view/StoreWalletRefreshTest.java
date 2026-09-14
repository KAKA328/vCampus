package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.store.*;
import cn.vcampus.user.Session;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 页面读请求使用 Socket 替身控制响应顺序；另有真实 Access + TCP 页面联调证据。 */
class StoreWalletRefreshTest {
    @Test void refreshLedgerButtonAlsoRefreshesHeaderBalance() throws Exception {
        try (WalletServer server = new WalletServer(null)) {
            StorePanel panel = page(server);
            await(() -> balance(panel).contains("100.00"));
            server.paid = true;
            SwingUtilities.invokeAndWait(() -> button(panel).doClick());
            assertPaid(panel);
        }
    }
    @Test void enteringWalletTabReadsBothBalanceAndLedger() throws Exception {
        try (WalletServer server = new WalletServer(null)) {
            StorePanel panel = page(server);
            await(() -> balance(panel).contains("100.00"));
            server.paid = true;
            SwingUtilities.invokeAndWait(() -> selectWallet(panel));
            assertPaid(panel);
        }
    }
    @Test void reenteringCachedStorePageReadsSharedWalletAgain() throws Exception {
        try (WalletServer server = new WalletServer(null)) {
            StorePanel panel = page(server);
            await(() -> balance(panel).contains("100.00"));
            server.paid = true;
            SwingUtilities.invokeAndWait(panel::refreshWalletOnEntry);
            assertPaid(panel);
        }
    }
    @Test void latePrePaymentBalanceResponseCannotOverwriteFreshBalance() throws Exception {
        try (WalletServer server = new WalletServer(MessageType.STORE_ACCOUNT_QUERY)) {
            StorePanel panel = page(server);
            assertTrue(server.blocked.await(5, TimeUnit.SECONDS));
            server.paid = true;
            SwingUtilities.invokeAndWait(() -> button(panel).doClick());
            assertPaid(panel);
            server.release.countDown();
            assertTrue(server.sent.await(5, TimeUnit.SECONDS));
            // SwingWorker 批处理 done 通常延迟约 33 ms；等候迟到响应真正完成 EDT 派发。
            Thread.sleep(250);
            assertPaid(panel);
        }
    }
    @Test void latePrePaymentLedgerResponseCannotEraseNewTransaction() throws Exception {
        try (WalletServer server = new WalletServer(MessageType.STORE_ACCOUNT_LEDGER_V2)) {
            StorePanel panel = page(server);
            await(() -> balance(panel).contains("100.00"));
            SwingUtilities.invokeAndWait(() -> selectWallet(panel));
            assertTrue(server.blocked.await(5, TimeUnit.SECONDS));
            server.paid = true;
            SwingUtilities.invokeAndWait(() -> button(panel).doClick());
            assertPaid(panel);
            server.release.countDown();
            assertTrue(server.sent.await(5, TimeUnit.SECONDS));
            Thread.sleep(250);
            assertPaid(panel);
        }
    }
    private static StorePanel page(WalletServer server) throws Exception {
        StorePanel[] result = new StorePanel[1];
        SwingUtilities.invokeAndWait(() -> result[0] = new StorePanel("127.0.0.1", server.socket.getLocalPort(),
                new Session("test-token", new User("test-reader", "虚构读者", Role.STUDENT)), StorePanel.Mode.CONSUMER));
        return result[0];
    }
    private static void assertPaid(StorePanel panel) throws Exception {
        await(() -> balance(panel).contains("60.50") && model(panel).getRowCount() == 1);
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("图书赔偿", model(panel).getValueAt(0, 1));
            assertEquals(-3950L, model(panel).getValueAt(0, 2));
            assertEquals(6050L, model(panel).getValueAt(0, 3));
        });
    }
    private static void await(BooleanSupplier check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            AtomicBoolean success = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> success.set(check.getAsBoolean()));
            if (success.get()) return;
            Thread.sleep(20);
        }
        fail("wallet page did not reach expected state");
    }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static String balance(StorePanel panel) { return ((JLabel) field(panel, "balanceLabel")).getText(); }
    private static BatchTableModel model(StorePanel panel) { return (BatchTableModel) field(panel, "ledgerModel"); }
    private static JButton button(StorePanel panel) { return (JButton) field(panel, "refreshLedgerButton"); }
    private static void selectWallet(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTabbedPane) {
                JTabbedPane tabs = (JTabbedPane) child;
                for (int i = 0; i < tabs.getTabCount(); i++) if ("钱包".equals(tabs.getTitleAt(i))) tabs.setSelectedIndex(i);
            } else if (child instanceof Container) selectWallet((Container) child);
        }
    }
    private static final class WalletServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        final ExecutorService workers = Executors.newCachedThreadPool();
        final CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1), sent = new CountDownLatch(1);
        final AtomicBoolean blockedOnce = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final MessageType delay;
        volatile boolean paid;
        WalletServer(MessageType delay) throws Exception {
            this.delay = delay;
            workers.submit(() -> {
                while (!socket.isClosed()) {
                    try { Socket client = socket.accept(); workers.submit(() -> exchange(client)); }
                    catch (IOException error) { if (!socket.isClosed()) failure.set(error); }
                }
            });
        }
        void exchange(Socket client) {
            try (Socket closed = client;
                    ObjectInputStream input = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                client.setSoTimeout(10000);
                Message request = (Message) input.readObject();
                boolean snapshot = paid;
                Object payload = Collections.emptyList();
                if (request.getType() == MessageType.STORE_ACCOUNT_QUERY) payload = snapshot ? 6050L : 10000L;
                if (request.getType() == MessageType.STORE_ACCOUNT_LEDGER_V2 && snapshot) {
                    payload = Collections.singletonList(new WalletTransaction("ledger", "test-reader",
                            WalletTransactionType.LIBRARY_LOSS, -3950, 6050, "test-reader",
                            "library compensation TEST", LocalDateTime.now()));
                }
                boolean held = request.getType() == delay && blockedOnce.compareAndSet(false, true);
                if (held) { blocked.countDown(); if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("release timeout"); }
                output.writeObject(Message.response(request, StatusCode.OK, payload)); output.flush();
                if (held) sent.countDown();
            } catch (Exception error) { if (!socket.isClosed()) failure.compareAndSet(null, error); }
        }
        @Override public void close() throws Exception {
            release.countDown(); socket.close(); workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }
}

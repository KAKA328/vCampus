package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.client.service.RemoteStoreService;
import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.*;
import cn.vcampus.library.*;
import cn.vcampus.user.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;

public final class WalletUiEvidence {
    private static StorePanel store;
    private static LibraryPanel library;
    private static void check(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    public static void main(String[] args) throws Exception {
        try { run(args); System.exit(0); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = Integer.parseInt(args[0]);
        Path output = Paths.get(args[1]);
        boolean observeBaseline = args.length > 2 && args[2].equals("observe-baseline");
        Files.createDirectories(output);
        final Session session;
        try (RemoteUserService users = new RemoteUserService(host, port)) {
            Message result = users.login(new UserCredentials("li_rich", "Demo123", "验收学生", "STUDENT"));
            check(result.getStatusCode() == StatusCode.OK, "login failed");
            session = (Session) result.getPayload();
        }
        SwingUtilities.invokeAndWait(() -> {
            VCampusTheme.install();
            store = new StorePanel(host, port, session, StorePanel.Mode.CONSUMER);
            library = new LibraryPanel(host, port, session);
            selectTab(store, "钱包");
            library.state.workspaceTabs.setSelectedIndex(2);
            LibraryCirculationActions.loadCompensations(library.state);
        });
        await(() -> text(store, "balanceLabel").contains("100.00")
                && Long.valueOf(10000).equals(library.state.currentWalletBalance)
                && library.state.compensationsLoaded, "initial real page data");
        final LibraryCompensation[] selected = new LibraryCompensation[1];
        SwingUtilities.invokeAndWait(() -> {
            check(model(store).getRowCount() == 0, "fresh reader ledger not empty");
            selected[0] = library.state.currentCompensations.values().iterator().next();
            check(selected[0].getStatus() == CompensationStatus.PENDING && selected[0].getAmountCents() == 3950,
                    "expected untouched 39.50 pending bill");
            capture(store, output.resolve("store-before.png"));
            capture(library, output.resolve("library-before.png"));
            System.out.println("BEFORE storeLabel=" + text(store, "balanceLabel")
                    + " libraryCents=" + library.state.currentWalletBalance + " ledgerRows=" + model(store).getRowCount());
            // 调用原页面确认后的提交入口：不伪造响应，不注入余额或表格；确认对话框不在此组件测试范围。
            library.submitCompensationPayment(selected[0]);
        });
        await(() -> Long.valueOf(6050).equals(library.state.currentWalletBalance)
                && library.state.currentCompensations.get(selected[0].getCompensationId()).getStatus() == CompensationStatus.PAID,
                "library page payment and refresh");
        SwingUtilities.invokeAndWait(() -> ((JButton) field(store, "refreshLedgerButton")).doClick());
        await(() -> model(store).getRowCount() == 1, "actual store refresh button result");
        if (!observeBaseline) await(() -> text(store, "balanceLabel").contains("60.50"), "store balance refresh");
        SwingUtilities.invokeAndWait(() -> {
            capture(store, output.resolve("store-after.png"));
            capture(library, output.resolve("library-after.png"));
            System.out.println("AFTER storeLabel=" + text(store, "balanceLabel")
                    + " libraryCents=" + library.state.currentWalletBalance + " ledgerRows=" + model(store).getRowCount());
            System.out.println("STORE_LEDGER type=" + model(store).getValueAt(0, 1)
                    + " amountCents=" + model(store).getValueAt(0, 2) + " balanceAfterCents=" + model(store).getValueAt(0, 3)
                    + " note=" + model(store).getValueAt(0, 5));
            check("图书赔偿".equals(model(store).getValueAt(0, 1)), "wrong ledger type");
            check(Long.valueOf(-3950).equals(model(store).getValueAt(0, 2)), "wrong signed debit");
            check(Long.valueOf(6050).equals(model(store).getValueAt(0, 3)), "wrong ledger ending balance");
        });
        try (RemoteStoreService remote = new RemoteStoreService(host, port);
                RemoteLibraryService lib = new RemoteLibraryService(host, port)) {
            Message balance = remote.balance(session.getToken());
            check(balance.getStatusCode() == StatusCode.OK && ((Number) balance.getPayload()).longValue() == 6050, "server balance mismatch");
            check(((List<?>) remote.ledger(session.getToken()).getPayload()).size() == 1, "server ledger count mismatch");
            Message repeat = lib.payCompensation(session.getToken(), selected[0].getCompensationId());
            check(repeat.getStatusCode() == StatusCode.OK, "idempotent payment failed");
            check(((Number) remote.balance(session.getToken()).getPayload()).longValue() == 6050, "duplicate debit");
            check(((List<?>) remote.ledger(session.getToken()).getPayload()).size() == 1, "duplicate ledger after retry");
            System.out.println("TCP_STORE_BALANCE_CENTS=6050 TCP_LEDGER_ROWS=1 REPEAT_PAYMENT_NO_EXTRA_DEBIT=true");
        }
        AtomicBoolean same = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> same.set(text(store, "balanceLabel").contains("60.50")));
        System.out.println("STORE_PAGE_BALANCE_MATCHES_LEDGER=" + same.get());
        if (!observeBaseline) check(same.get(), "store header is stale after refresh");
        System.out.println("WALLET_UI_EVIDENCE=" + (same.get() ? "PASS" : "BASELINE_FAIL_STALE_BALANCE"));
    }
    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(50);
        }
        throw new IllegalStateException("timeout: " + label);
    }
    private static Object field(Object instance, String name) {
        try { Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static String text(Object instance, String name) { return ((JLabel) field(instance, name)).getText(); }
    private static BatchTableModel model(StorePanel panel) { return (BatchTableModel) field(panel, "ledgerModel"); }
    private static void selectTab(Container root, String title) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTabbedPane) {
                JTabbedPane pane = (JTabbedPane) child;
                for (int i = 0; i < pane.getTabCount(); i++) if (title.equals(pane.getTitleAt(i))) pane.setSelectedIndex(i);
            } else if (child instanceof Container) selectTab((Container) child, title);
        }
    }
    private static void layout(Container root) {
        if (root instanceof JScrollPane) {
            JScrollPane scroller = (JScrollPane) root;
            if (scroller.getViewport().getView() instanceof JTable)
                scroller.setColumnHeaderView(((JTable) scroller.getViewport().getView()).getTableHeader());
        }
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    private static void capture(JPanel panel, Path path) {
        try {
            panel.setSize(1200, 880);
            for (int i = 0; i < 5; i++) layout(panel);
            BufferedImage image = new BufferedImage(1200, 880, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0, 0, 1200, 880); panel.printAll(g); g.dispose();
            ImageIO.write(image, "png", path.toFile());
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}

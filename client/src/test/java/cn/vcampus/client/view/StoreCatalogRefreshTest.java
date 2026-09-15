package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.store.Product;
import cn.vcampus.user.Session;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 问题2：并发购买场景下“没抢到的买家”必须能看到真实库存。
 *
 * <p>覆盖：结算失败后重查商品、加购失败后重查商品、静默刷新保留选中行、迟到响应不覆盖新响应、
 * 自动刷新的守卫（写请求进行中 / 对话框打开时不刷新）与定时器生命周期。
 * 用 Socket 替身精确控制服务端响应，不依赖真实 Access 库。
 */
class StoreCatalogRefreshTest {

    @Test
    void failedCheckoutRefreshesProductStock() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 5;
            StorePanel panel = page(server);
            await(() -> stockOfFirstRow(panel) == 5);

            // 别人把最后 5 件买走，本买家结算被服务端拒绝
            server.stock = 0;
            server.checkoutStatus = StatusCode.CONFLICT;
            int queriesBefore = server.productQueries.get();
            SwingUtilities.invokeAndWait(() -> invoke(panel, "submitCheckout", new Class<?>[0]));

            await(() -> server.checkoutRequests.get() >= 1);
            await(() -> server.productQueries.get() > queriesBefore);
            await(() -> stockOfFirstRow(panel) == 0);
        }
    }

    @Test
    void failedAddToCartRefreshesProductStock() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 3;
            StorePanel panel = page(server);
            await(() -> stockOfFirstRow(panel) == 3);

            final Product product = firstVisibleProduct(panel);
            server.stock = 0;
            server.addToCartStatus = StatusCode.CONFLICT;
            int queriesBefore = server.productQueries.get();
            SwingUtilities.invokeAndWait(() -> invoke(panel, "addProductToCart",
                    new Class<?>[] {Product.class, int.class}, product, Integer.valueOf(1)));

            await(() -> server.addToCartRequests.get() >= 1);
            await(() -> server.productQueries.get() > queriesBefore);
            await(() -> stockOfFirstRow(panel) == 0);
        }
    }

    @Test
    void silentRefreshKeepsUserSelectedProduct() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 7;
            StorePanel panel = page(server);
            await(() -> rowCount(panel) == 3);

            JTable table = productTable(panel);
            SwingUtilities.invokeAndWait(() -> table.setRowSelectionInterval(1, 1));
            final String selectedBefore = selectedProductId(panel);
            assertEquals("P002", selectedBefore);

            int queriesBefore = server.productQueries.get();
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.FALSE));
            await(() -> server.productQueries.get() > queriesBefore);
            await(() -> rowCount(panel) == 3);
            Thread.sleep(150);

            // 静默刷新重建了模型行，选中行必须仍指向同一个商品（否则购买按钮会被清灰）
            assertEquals(selectedBefore, selectedProductId(panel));
        }
    }

    @Test
    void lateProductResponseCannotOverwriteNewerOne() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 4;
            StorePanel panel = page(server);
            await(() -> stockOfFirstRow(panel) == 4);

            // 第一次刷新被服务端挂起（携带旧库存 4）
            server.holdNextProductQuery = true;
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.TRUE));
            assertTrue(server.held.await(5, TimeUnit.SECONDS), "服务端应已挂起第一次查询");

            // 第二次刷新（新代次）先返回并写入表格
            server.stock = 1;
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.TRUE));
            await(() -> stockOfFirstRow(panel) == 1);

            // 放行迟到的旧响应：不得把已经更新的库存覆盖回 4
            server.release.countDown();
            Thread.sleep(400);
            assertEquals(1, stockOfFirstRow(panel), "迟到的旧响应不得覆盖新库存");
        }
    }

    @Test
    void autoRefreshIsSkippedWhileBusyOrWhileDialogOpen() throws Exception {
        try (StoreServer server = new StoreServer()) {
            StorePanel panel = page(server);
            await(() -> server.productQueries.get() >= 1);

            SwingUtilities.invokeAndWait(() -> {
                try {
                    assertTrue(panel.canSilentlyRefreshCatalog(), "空闲且无对话框时应允许静默刷新");

                    setIntField(panel, "activeMutationRequests", 1);
                    assertFalse(panel.canSilentlyRefreshCatalog(), "写请求进行中不得自动刷新");

                    setIntField(panel, "activeMutationRequests", 0);
                    setStaticIntField("openThemedDialogs", 1);
                    assertFalse(panel.canSilentlyRefreshCatalog(), "商店对话框打开期间不得自动刷新");

                    setStaticIntField("openThemedDialogs", 0);
                    assertTrue(panel.canSilentlyRefreshCatalog());
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }

    @Test
    void autoRefreshTimerStartsAndStopsWithPanelLifecycle() throws Exception {
        try (StoreServer server = new StoreServer()) {
            StorePanel panel = page(server);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    panel.startCatalogAutoRefresh();
                    assertTrue(timer(panel).isRunning());
                    assertTrue(timer(panel).getDelay() > 0);
                    panel.stopCatalogAutoRefresh();
                    assertFalse(timer(panel).isRunning(), "离开商店页后不得继续轮询");
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        }
    }

    // —— 评审意见①：热销榜被自动刷新切回普通列表 ——

    @Test
    void silentAutoRefreshKeepsHotViewInsteadOfFallingBackToAllProducts() throws Exception {
        try (StoreServer server = new StoreServer()) {
            StorePanel panel = page(server);
            await(() -> server.productQueries.get() >= 1);

            // 用户切到热销榜
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadHotProducts", new Class<?>[0]));
            await(() -> server.hotProductQueries.get() >= 1);
            await(() -> "返回全部商品".equals(hotButtonText(panel)));
            assertTrue(hotViewVisible(panel));

            int productQueriesBefore = server.productQueries.get();
            int hotQueriesBefore = server.hotProductQueries.get();

            // 定时器到点：静默刷新必须留在热销视图
            SwingUtilities.invokeAndWait(panel::silentlyRefreshCatalog);
            await(() -> server.hotProductQueries.get() > hotQueriesBefore);
            Thread.sleep(200);

            assertTrue(hotViewVisible(panel), "静默刷新不得把用户切回全部商品");
            assertEquals("返回全部商品", hotButtonText(panel), "热销视图按钮文案不得被静默刷新改回");
            assertEquals(productQueriesBefore, server.productQueries.get(),
                    "热销视图中静默刷新不得发普通商品查询");
        }
    }

    @Test
    void silentAutoRefreshOnPlainListStillQueriesPlainProducts() throws Exception {
        try (StoreServer server = new StoreServer()) {
            StorePanel panel = page(server);
            await(() -> server.productQueries.get() >= 1);
            assertFalse(hotViewVisible(panel));

            int productQueriesBefore = server.productQueries.get();
            int hotQueriesBefore = server.hotProductQueries.get();
            SwingUtilities.invokeAndWait(panel::silentlyRefreshCatalog);

            await(() -> server.productQueries.get() > productQueriesBefore);
            assertEquals(hotQueriesBefore, server.hotProductQueries.get(), "普通列表不得被静默刷新切到热销榜");
            assertFalse(hotViewVisible(panel));
        }
    }

    // —— 评审意见②：离开页面后未作废在途请求 ——

    @Test
    void removedPageDropsLateSuccessResponse() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 5;
            StorePanel panel = page(server);
            await(() -> stockOfFirstRow(panel) == 5);

            // 挂起一次查询，其响应携带新库存 99
            server.holdNextProductQuery = true;
            server.stock = 99;
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.FALSE));
            assertTrue(server.held.await(5, TimeUnit.SECONDS), "服务端应已挂起查询");

            // 离开商店页（removeNotify 的等价动作）→ 在途请求作废
            SwingUtilities.invokeAndWait(panel::invalidatePendingResponses);

            // 放行迟到的成功响应：不得写回已离开的页面
            server.release.countDown();
            Thread.sleep(400);
            assertEquals(5, stockOfFirstRow(panel), "页面已移除，迟到的成功响应不得写回页面");
        }
    }

    // —— 评审意见③：过期请求的失败不得覆盖最新状态提示 ——

    @Test
    void supersededRequestFailureDoesNotOverwriteLatestStatus() throws Exception {
        try (StoreServer server = new StoreServer()) {
            server.stock = 5;
            StorePanel panel = page(server);
            await(() -> stockOfFirstRow(panel) == 5);

            // 第 1 次查询被挂起，放行后以失败返回
            server.holdNextProductQuery = true;
            server.failHeldProductQuery = true;
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.FALSE));
            assertTrue(server.held.await(5, TimeUnit.SECONDS), "服务端应已挂起第 1 次查询");

            // 第 2 次查询（新代次）成功，状态栏写入成功提示
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.TRUE));
            await(() -> statusText(panel).contains("已显示商品"));

            // 放行过期的失败响应：不得覆盖最新提示
            server.release.countDown();
            Thread.sleep(400);
            assertFalse(statusText(panel).contains("库存冲突，查询失败"),
                    "过期请求的失败不得覆盖最新状态提示，实际为：" + statusText(panel));
            assertTrue(statusText(panel).contains("已显示商品"));
        }
    }

    @Test
    void currentRequestFailureStillReportsStatus() throws Exception {
        try (StoreServer server = new StoreServer()) {
            StorePanel panel = page(server);
            await(() -> server.productQueries.get() >= 1);

            // 没有更新请求取代它：失败必须照常报出来，证明过期守卫没有过度抑制
            server.productQueryStatus = StatusCode.CONFLICT;
            SwingUtilities.invokeAndWait(() -> invoke(panel, "loadProducts",
                    new Class<?>[] {boolean.class}, Boolean.TRUE));

            await(() -> statusText(panel).contains("查询失败"));
        }
    }

    // —— helpers ——

    private static StorePanel page(StoreServer server) throws Exception {
        StorePanel[] result = new StorePanel[1];
        SwingUtilities.invokeAndWait(() -> result[0] = new StorePanel("127.0.0.1", server.socket.getLocalPort(),
                new Session("probe-token", new User("probe-buyer", "探针买家", Role.STUDENT)),
                StorePanel.Mode.CONSUMER));
        return result[0];
    }

    private static int rowCount(StorePanel panel) {
        try {
            return productTable(panel).getRowCount();
        } catch (Exception notReady) {
            return -1;
        }
    }

    private static int stockOfFirstRow(StorePanel panel) {
        try {
            JTable table = productTable(panel);
            if (table.getRowCount() == 0) {
                return -1;
            }
            return ((Number) table.getValueAt(0, 4)).intValue();
        } catch (Exception notReady) {
            return -1;
        }
    }

    private static Product firstVisibleProduct(StorePanel panel) throws Exception {
        List<?> visible = (List<?>) field(panel, "visibleProducts");
        return (Product) visible.get(0);
    }

    private static String selectedProductId(StorePanel panel) throws Exception {
        Method method = StorePanel.class.getDeclaredMethod("selectedProduct");
        method.setAccessible(true);
        Product product = (Product) method.invoke(panel);
        return product == null ? null : product.getProductId();
    }

    private static JTable productTable(StorePanel panel) throws Exception {
        return (JTable) field(panel, "productTable");
    }

    private static Timer timer(StorePanel panel) throws Exception {
        return (Timer) field(panel, "catalogAutoRefresh");
    }

    /** 供 await 轮询调用：不抛受检异常。 */
    private static String hotButtonText(StorePanel panel) {
        try {
            return ((JButton) field(panel, "hotButton")).getText();
        } catch (Exception notReady) {
            return "";
        }
    }

    private static boolean hotViewVisible(StorePanel panel) throws Exception {
        return ((Boolean) field(panel, "hotViewVisible")).booleanValue();
    }

    /** 供 await 轮询调用：不抛受检异常。 */
    private static String statusText(StorePanel panel) {
        try {
            return ((JLabel) field(panel, "status")).getText();
        } catch (Exception notReady) {
            return "";
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setIntField(Object target, String name, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setStaticIntField(String name, int value) throws Exception {
        Field field = StorePanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static void invoke(StorePanel panel, String name, Class<?>[] types, Object... args) {
        try {
            Method method = StorePanel.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(panel, args);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        fail("商店页面未在预期时间内达到目标状态");
    }

    /** Socket 替身：商品查询固定返回 3 个商品，库存由 stock 控制；加购/结算可切成失败，可选挂起一次查询。 */
    private static final class StoreServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        final ExecutorService workers = Executors.newCachedThreadPool();
        final AtomicInteger productQueries = new AtomicInteger();
        final AtomicInteger hotProductQueries = new AtomicInteger();
        final AtomicInteger addToCartRequests = new AtomicInteger();
        final AtomicInteger checkoutRequests = new AtomicInteger();
        final CountDownLatch held = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean heldOnce = new AtomicBoolean();
        volatile int stock = 5;
        volatile boolean holdNextProductQuery;
        /** 被挂起的那次查询放行后是否以失败返回（用于验证过期失败不得覆盖状态栏）。 */
        volatile boolean failHeldProductQuery;
        /** 非挂起查询的返回状态：用于验证"未过期"的失败仍然正常报错。 */
        volatile StatusCode productQueryStatus = StatusCode.OK;
        volatile StatusCode addToCartStatus = StatusCode.OK;
        volatile StatusCode checkoutStatus = StatusCode.OK;

        StoreServer() throws IOException {
            workers.submit(() -> {
                while (!socket.isClosed()) {
                    try {
                        Socket client = socket.accept();
                        workers.submit(() -> exchange(client));
                    } catch (IOException closed) {
                        return;
                    }
                }
            });
        }

        private void exchange(Socket client) {
            try (Socket closed = client;
                    ObjectInputStream input = new ObjectInputStream(client.getInputStream());
                    ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                Message request = (Message) input.readObject();
                StatusCode status = StatusCode.OK;
                Object payload;
                switch (request.getType()) {
                    case STORE_QUERY:
                        productQueries.incrementAndGet();
                        int snapshot = stock;// 快照：挂起期间 stock 可能已被改新
                        boolean heldThis = holdNextProductQuery && heldOnce.compareAndSet(false, true);
                        if (heldThis) {
                            held.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        }
                        if (heldThis && failHeldProductQuery) {
                            status = StatusCode.CONFLICT;
                            payload = "库存冲突，查询失败";
                        } else {
                            status = productQueryStatus;
                            payload = status == StatusCode.OK ? products(snapshot) : "查询失败";
                        }
                        break;
                    case STORE_HOT_PRODUCTS:
                        hotProductQueries.incrementAndGet();
                        payload = products(stock);
                        break;
                    case STORE_ACCOUNT_QUERY:
                        payload = Long.valueOf(10000L);
                        break;
                    case STORE_CART_ADD:
                        addToCartRequests.incrementAndGet();
                        status = addToCartStatus;
                        payload = status == StatusCode.OK ? "已加入购物车" : "库存不足，加购失败";
                        break;
                    case STORE_CART_CHECKOUT:
                        checkoutRequests.incrementAndGet();
                        status = checkoutStatus;
                        payload = status == StatusCode.OK ? "结算成功" : "库存冲突，结算已回滚";
                        break;
                    default:
                        payload = Collections.emptyList();
                        break;
                }
                output.writeObject(Message.response(request, status, payload));
                output.flush();
            } catch (Exception ignored) {
                // 面板关闭连接属正常收尾
            }
        }

        private List<Product> products(int stockSnapshot) {
            List<Product> products = new ArrayList<Product>();
            products.add(new Product("P001", "甲商品", stockSnapshot, 10.0d, "说明", "文具"));
            products.add(new Product("P002", "乙商品", stockSnapshot + 1, 20.0d, "说明", "文具"));
            products.add(new Product("P003", "丙商品", stockSnapshot + 2, 30.0d, "说明", "文具"));
            return products;
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            socket.close();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}

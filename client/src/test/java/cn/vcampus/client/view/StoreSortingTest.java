package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.store.Product;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Arrays;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 商店商品列表：确认 StorePanel 真的装了数值感知排序器（问题1的接线验证），
 * 而不是只测 SortableTables 本身。商品数据经真实 showProducts 路径写入表格。
 */
class StoreSortingTest {

    @Test
    void productTableSortsStockAndPriceNumericallyThenCyclesBackToUnsorted() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                showProducts(panel,
                        new Product("P001", "甲商品", 5, 12.5d, "说明", "文具"),
                        new Product("P002", "乙商品", 100, 9.0d, "说明", "文具"),
                        new Product("P003", "丙商品", 10, 30.0d, "说明", "文具"));
                JTable table = productTable(panel);
                assertNotNull(table.getRowSorter(), "商品表必须安装行排序器");
                assertEquals(3, table.getRowCount());

                // 库存列（第 4 列）：字典序会排成 10 < 100 < 5
                table.getRowSorter().toggleSortOrder(4);
                assertEquals(5, table.getValueAt(0, 4));
                assertEquals(10, table.getValueAt(1, 4));
                assertEquals(100, table.getValueAt(2, 4));

                // 再点一次 → 降序
                table.getRowSorter().toggleSortOrder(4);
                assertEquals(100, table.getValueAt(0, 4));
                assertEquals(5, table.getValueAt(2, 4));

                // 第三次 → 取消排序，恢复模型原序（服务端返回顺序）
                table.getRowSorter().toggleSortOrder(4);
                assertEquals(5, table.getValueAt(0, 4));
                assertEquals(100, table.getValueAt(1, 4));

                // 价格列（第 3 列）：9.0 < 12.5 < 30.0
                table.getRowSorter().toggleSortOrder(3);
                assertEquals(9.0d, ((Number) table.getValueAt(0, 3)).doubleValue());
                assertEquals(12.5d, ((Number) table.getValueAt(1, 3)).doubleValue());
                assertEquals(30.0d, ((Number) table.getValueAt(2, 3)).doubleValue());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test
    void sortingKeepsSelectionPointingAtSameProduct() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                showProducts(panel,
                        new Product("P001", "甲商品", 5, 12.5d, "说明", "文具"),
                        new Product("P002", "乙商品", 100, 9.0d, "说明", "文具"),
                        new Product("P003", "丙商品", 10, 30.0d, "说明", "文具"));
                JTable table = productTable(panel);
                table.setRowSelectionInterval(0, 0);
                assertEquals("P001", selectedProductId(panel));

                table.getRowSorter().toggleSortOrder(4);
                // 排序后选中行仍应指向同一个商品，而不是因为行号错位指向别家
                assertEquals("P001", selectedProductId(panel));
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    private static String selectedProductId(StorePanel panel) throws Exception {
        Method method = StorePanel.class.getDeclaredMethod("selectedProduct");
        method.setAccessible(true);
        Product product = (Product) method.invoke(panel);
        return product == null ? null : product.getProductId();
    }

    private static JTable productTable(StorePanel panel) throws Exception {
        Field field = StorePanel.class.getDeclaredField("productTable");
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static void showProducts(StorePanel panel, Product... products) throws Exception {
        Method method = StorePanel.class.getDeclaredMethod("showProducts", Message.class, boolean.class);
        method.setAccessible(true);
        Message response = Message.response(Message.request("probe", MessageType.STORE_QUERY, null),
                StatusCode.OK, Arrays.asList(products));
        method.invoke(panel, response, Boolean.TRUE);
    }

    /** 指向一个已关闭的端口：构造函数里的自动加载会快速失败，不影响注入的表格数据。 */
    private static StorePanel panel() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        StorePanel panel = new StorePanel("127.0.0.1", port,
                new Session("probe-token", new User("probe-manager", "探针管理员", Role.STORE_MANAGER)),
                StorePanel.Mode.MANAGER);
        Field retries = StorePanel.class.getDeclaredField("initialProductRetryAttempts");
        retries.setAccessible(true);
        retries.setInt(panel, 0);
        return panel;
    }
}

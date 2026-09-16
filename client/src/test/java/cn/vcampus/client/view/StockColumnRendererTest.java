package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.MessageType;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.common.User;
import cn.vcampus.store.Product;
import cn.vcampus.user.Session;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.Arrays;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 库存列单元格渲染：渲染器实例被整列复用，一个「缺货」单元格设过的红字
 * 不得泄漏给其后的单元格。曾经卖光一件商品会让整列库存都显示成红色的「缺货」。
 */
class StockColumnRendererTest {

    @Test
    void soldOutCellDoesNotLeakRedIntoFollowingCells() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                showProducts(panel,
                        new Product("P001", "卖光的商品", 0, 10.0d, "说明", "文具"),
                        new Product("P002", "充足商品甲", 100, 20.0d, "说明", "文具"),
                        new Product("P003", "充足商品乙", 100, 30.0d, "说明", "文具"),
                        new Product("P004", "低库存商品", 3, 40.0d, "说明", "文具"));
                JTable table = table(panel);
                assertEquals(4, table.getRowCount());

                JLabel soldOut = render(table, 0);
                assertEquals("缺货", soldOut.getText());
                assertEquals(VCampusTheme.DANGER, soldOut.getForeground());

                for (int row = 1; row <= 2; row++) {
                    JLabel plenty = render(table, row);
                    assertEquals("100", plenty.getText());
                    assertEquals(table.getForeground(), plenty.getForeground(),
                            "第 " + row + " 行库存充足，不得继承上一行「缺货」的红字");
                }

                JLabel low = render(table, 3);
                assertEquals("3", low.getText());
                assertNotEquals(VCampusTheme.DANGER, low.getForeground(),
                        "低库存是琥珀提醒，不是缺货红");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test
    void soldOutCellStillRendersRedWhenItComesLast() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                showProducts(panel,
                        new Product("P001", "充足商品", 100, 10.0d, "说明", "文具"),
                        new Product("P002", "卖光的商品", 0, 20.0d, "说明", "文具"));
                JTable table = table(panel);

                JLabel first = render(table, 0);
                assertEquals("100", first.getText());
                assertEquals(table.getForeground(), first.getForeground());

                JLabel soldOut = render(table, 1);
                assertEquals("缺货", soldOut.getText());
                assertEquals(VCampusTheme.DANGER, soldOut.getForeground());
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    private static JLabel render(JTable table, int row) {
        return (JLabel) table.prepareRenderer(table.getCellRenderer(row, 4), row, 4);
    }

    private static JTable table(StorePanel panel) throws Exception {
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
                new Session("probe-token", new User("probe-buyer", "探针买家", Role.STUDENT)),
                StorePanel.Mode.CONSUMER);
        Field retries = StorePanel.class.getDeclaredField("initialProductRetryAttempts");
        retries.setAccessible(true);
        retries.setInt(panel, 0);
        return panel;
    }
}

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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 数量框：商品刷新不得把用户已经调好的数量打回 1。
 *
 * <p>缺陷成因：刷新会重建模型行并临时清空表格选中，恢复同一行选中时会再次触发选中监听器；
 * 若监听器在「无选中」时清空了 quantityTargetProductId，同一个商品就会被误判成「换了商品」而复位数量。
 */
class StoreQuantitySpinnerTest {

    @Test
    void productRefreshKeepsUserChosenQuantity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                injectProducts(panel);
                JTable table = table(panel);
                JSpinner quantity = quantity(panel);

                table.setRowSelectionInterval(0, 0);
                quantity.setValue(Integer.valueOf(5));
                assertEquals(5, quantity.getValue());

                // 一次商品刷新（等价于 5 秒自动刷新）
                injectProducts(panel);

                assertEquals(5, quantity.getValue(), "刷新后用户选择的数量必须保留");
                assertEquals("5", editorText(quantity));
                assertEquals(0, table.getSelectedRow(), "刷新后仍应选中同一行");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test
    void typedQuantitySurvivesRefresh() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                injectProducts(panel);
                JTable table = table(panel);
                JSpinner quantity = quantity(panel);
                table.setRowSelectionInterval(0, 0);

                // 模拟用户直接在数量框里键入 7（尚未提交到模型）
                editorText(quantity, "7");
                injectProducts(panel);

                assertEquals("7", editorText(quantity), "刷新后输入框里刚键入的数字必须还在");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    @Test
    void switchingToAnotherProductStillResetsQuantity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                StorePanel panel = panel();
                injectProducts(panel);
                JTable table = table(panel);
                JSpinner quantity = quantity(panel);

                table.setRowSelectionInterval(0, 0);
                quantity.setValue(Integer.valueOf(4));
                assertEquals(4, quantity.getValue());

                // 换到另一个商品：数量必须复位为 1，避免把上一个商品的数量带过去
                table.setRowSelectionInterval(1, 1);
                assertEquals(1, quantity.getValue(), "切换到另一个商品时数量必须复位为 1");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            }
        });
    }

    private static String editorText(JSpinner spinner) throws Exception {
        return ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText();
    }

    private static void editorText(JSpinner spinner, String text) throws Exception {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setText(text);
    }

    private static JSpinner quantity(StorePanel panel) throws Exception {
        return (JSpinner) field(panel, "quantity");
    }

    private static JTable table(StorePanel panel) throws Exception {
        return (JTable) field(panel, "productTable");
    }

    private static void injectProducts(StorePanel panel) throws Exception {
        Method method = StorePanel.class.getDeclaredMethod("showProducts", Message.class, boolean.class);
        method.setAccessible(true);
        Message response = Message.response(Message.request("probe", MessageType.STORE_QUERY, null), StatusCode.OK,
                Arrays.asList(
                        new Product("P001", "甲商品", 100, 10.0d, "说明", "文具"),
                        new Product("P002", "乙商品", 100, 20.0d, "说明", "文具"),
                        new Product("P003", "丙商品", 100, 30.0d, "说明", "文具")));
        method.invoke(panel, response, Boolean.FALSE);
    }

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

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

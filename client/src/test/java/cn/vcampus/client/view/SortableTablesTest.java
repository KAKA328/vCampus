package cn.vcampus.client.view;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 排序比较器：数值列必须按数值大小排序，文本列仍按中文 Collator；
 * 表头点击循环为 增序 → 降序 → 取消排序（恢复模型原序）。
 */
class SortableTablesTest {

    @Test
    void numericColumnSortsByValueNotByTextAndCyclesBackToModelOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"库存"}, 0);
            // 字典序会得到 10 < 100 < 5，数值序必须是 5 < 10 < 100
            model.addRow(new Object[] {Integer.valueOf(5)});
            model.addRow(new Object[] {Integer.valueOf(100)});
            model.addRow(new Object[] {Integer.valueOf(10)});
            JTable table = new JTable(model);
            SortableTables.apply(table);

            // 第一次：增序
            table.getRowSorter().toggleSortOrder(0);
            assertEquals(5, table.getValueAt(0, 0));
            assertEquals(10, table.getValueAt(1, 0));
            assertEquals(100, table.getValueAt(2, 0));

            // 第二次：降序
            table.getRowSorter().toggleSortOrder(0);
            assertEquals(100, table.getValueAt(0, 0));
            assertEquals(10, table.getValueAt(1, 0));
            assertEquals(5, table.getValueAt(2, 0));

            // 第三次：取消排序，恢复模型原序 5, 100, 10
            table.getRowSorter().toggleSortOrder(0);
            assertEquals(0, table.getRowSorter().getSortKeys().size());
            assertEquals(5, table.getValueAt(0, 0));
            assertEquals(100, table.getValueAt(1, 0));
            assertEquals(10, table.getValueAt(2, 0));

            // 第四次：重新回到增序
            table.getRowSorter().toggleSortOrder(0);
            assertEquals(5, table.getValueAt(0, 0));
            assertEquals(100, table.getValueAt(2, 0));
        });
    }

    @Test
    void mixedNumberTypesStillSortNumerically() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"金额"}, 0);
            model.addRow(new Object[] {Long.valueOf(6050L)});
            model.addRow(new Object[] {Double.valueOf(9.5d)});
            model.addRow(new Object[] {Integer.valueOf(100)});
            JTable table = new JTable(model);
            SortableTables.apply(table);

            table.getRowSorter().toggleSortOrder(0);
            assertEquals(9.5d, ((Number) table.getValueAt(0, 0)).doubleValue());
            assertEquals(100, ((Number) table.getValueAt(1, 0)).intValue());
            assertEquals(6050L, ((Number) table.getValueAt(2, 0)).longValue());
        });
    }

    @Test
    void textColumnSortsByCollatorAndCyclesBackToModelOrder() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"名称"}, 0);
            // 模型原序刻意不同于排序结果，才能验证“第三次回到原序”
            model.addRow(new Object[] {"甲商品"});
            model.addRow(new Object[] {"丙商品"});
            model.addRow(new Object[] {"乙商品"});
            JTable table = new JTable(model);
            SortableTables.apply(table);

            table.getRowSorter().toggleSortOrder(0);
            Object[] ascending = viewOrder(table);
            assertEquals(3, ascending.length);

            // 第二次是第一次的逆序（降序）
            table.getRowSorter().toggleSortOrder(0);
            Object[] descending = viewOrder(table);
            assertEquals(ascending[0], descending[2]);
            assertEquals(ascending[1], descending[1]);
            assertEquals(ascending[2], descending[0]);

            // 第三次取消排序，精确恢复模型原序
            table.getRowSorter().toggleSortOrder(0);
            assertArrayEquals(new Object[] {"甲商品", "丙商品", "乙商品"}, viewOrder(table));
        });
    }

    private static Object[] viewOrder(JTable table) {
        Object[] values = new Object[table.getRowCount()];
        for (int row = 0; row < table.getRowCount(); row++) {
            values[row] = table.getValueAt(row, 0);
        }
        return values;
    }

    @Test
    void nullValuesDoNotBreakSorting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"说明"}, 0);
            model.addRow(new Object[] {null});
            model.addRow(new Object[] {"有说明"});
            JTable table = new JTable(model);
            SortableTables.apply(table);

            table.getRowSorter().toggleSortOrder(0);
            assertEquals(2, table.getRowCount());
        });
    }

    @Test
    void sortingKeepsEveryRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultTableModel model = new DefaultTableModel(new Object[] {"库存", "名称"}, 0);
            for (int i = 0; i < 20; i++) {
                model.addRow(new Object[] {Integer.valueOf(i), "商品" + i});
            }
            JTable table = new JTable(model);
            SortableTables.apply(table);

            table.getRowSorter().toggleSortOrder(0);
            assertEquals(20, table.getRowCount());
            assertEquals("商品0", table.getValueAt(0, 1));
            assertEquals("商品19", table.getValueAt(19, 1));
        });
    }
}

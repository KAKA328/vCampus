package cn.vcampus.client.view;

import java.text.Collator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * 表格排序支持：为每一列安装显式比较器，保证数值列按数值大小排序，并实现三态表头循环。
 *
 * <p>背景一（比较器）：{@code DefaultTableModel} 不声明列类型（{@code getColumnClass} 恒返回
 * {@code Object}），{@code TableRowSorter} 对非 {@code Comparable} 的列类型会回退到 {@link Collator}，
 * 按 {@code toString()} 字典序比较，于是库存 5/10/100 会被排成 10 &lt; 100 &lt; 5，
 * 价格、数量、金额等同理——表现为“点表头顺序随机变化”。
 *
 * <p>背景二（三态循环）：JDK 默认的 {@code TableRowSorter.toggleSortOrder} 只在
 * 增序 / 降序之间来回，第三次点击又回到增序，用户没有任何办法回到列表原始顺序。
 * 这里覆写为 增序 → 降序 → 取消排序（恢复模型原序） 三态。
 */
final class SortableTables {
    private SortableTables() {
    }

    /** 给表格安装「数值感知比较器 + 三态表头循环」的行排序器。 */
    static void apply(JTable table) {
        CyclingRowSorter sorter = new CyclingRowSorter(table.getModel());
        Collator collator = Collator.getInstance(Locale.CHINA);
        for (int column = 0; column < table.getColumnCount(); column++) {
            sorter.setComparator(column, (a, b) -> {
                if (a instanceof Number && b instanceof Number) {
                    return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
                }
                return collator.compare(String.valueOf(a), String.valueOf(b));
            });
        }
        table.setRowSorter(sorter);
    }

    /** 表头点击三态循环：增序 → 降序 → 取消排序（空 sortKeys，视图回到模型原序）。 */
    private static final class CyclingRowSorter extends TableRowSorter<TableModel> {
        CyclingRowSorter(TableModel model) {
            super(model);
        }

        @Override
        public void toggleSortOrder(int column) {
            SortOrder current = currentOrder(column);
            if (current == SortOrder.ASCENDING) {
                setSortKeys(Collections.singletonList(new SortKey(column, SortOrder.DESCENDING)));
            } else if (current == SortOrder.DESCENDING) {
                setSortKeys(Collections.<SortKey>emptyList());
            } else {
                setSortKeys(Collections.singletonList(new SortKey(column, SortOrder.ASCENDING)));
            }
        }

        private SortOrder currentOrder(int column) {
            List<? extends RowSorter.SortKey> keys = getSortKeys();
            for (RowSorter.SortKey key : keys) {
                if (key.getColumn() == column) {
                    return key.getSortOrder();
                }
            }
            return null;
        }
    }
}

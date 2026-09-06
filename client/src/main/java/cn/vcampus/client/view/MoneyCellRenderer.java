package cn.vcampus.client.view;

import java.awt.Component;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * 金额列渲染器：右对齐并固定两位小数，让金额可竖向扫读比较。
 * 表格模型里金额存数值类型（Double 元或 Long 分）以保证按数值排序，展示格式统一交给本渲染器，
 * 因此排序正确性与显示美观不互相牺牲。
 */
final class MoneyCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    /** 模型值的金额单位与符号约定，决定如何换算成展示文本。 */
    enum MoneyFormat {
        YUAN, // 模型值是 Double 元，如商品价格、购物车小计、订单总价
        CENTS, // 模型值是 Long 分且恒非负，如流水的变动后余额
        SIGNED_CENTS// 模型值是 Long 分且带符号，如流水的变动金额
    }

    private final MoneyFormat format;

    MoneyCellRenderer(MoneyFormat format) {
        this.format = format;
        setHorizontalAlignment(SwingConstants.RIGHT);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setText(format(value));
        return this;
    }

    private String format(Object value) {
        if (!(value instanceof Number)) {
            return value == null ? "" : String.valueOf(value);
        }
        Number amount = (Number) value;
        if (format == MoneyFormat.SIGNED_CENTS) {
            return StoreRowMapper.formatSignedYuan(amount.longValue());
        }
        if (format == MoneyFormat.CENTS) {
            return StoreRowMapper.formatYuan(amount.longValue());
        }
        // 元转分再格式化，避免出现 12.5 这类缺位小数；换算走 StoreRowMapper.toCents（委托唯一入口 Money.toCents）
        return StoreRowMapper.formatYuan(StoreRowMapper.toCents(amount.doubleValue()));
    }
}

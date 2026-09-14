package cn.vcampus.client.view;

import cn.vcampus.library.CompensationStatus;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

/** 馆藏和流通表格的列宽、排序、选择模式与状态渲染。 */
final class LibraryTableStyles {
    /** 设置馆藏表格的选择模式、列宽和排序。 */
    static void configureBookTable(LibraryViewState v) {
        LibraryTableStyles.configureTable(v.bookTable, v.manager
                ? ListSelectionModel.SINGLE_SELECTION
                : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        v.bookTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {88, 180, 120, 92, 100, 135, 130, 64, 64, 100};
        LibraryTableStyles.applyColumnWidths(v.bookTable, widths);
    }

    /** 设置借阅列宽与中文状态，并将书名显示在前。 */
    static void configureHistoryTable(LibraryViewState v) {
        LibraryTableStyles.configureTable(v.historyTable, ListSelectionModel.SINGLE_SELECTION);
        v.historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {110, 110, 100, 88, 220, 100, 100, 100, 140, 360, 160};
        LibraryTableStyles.applyColumnWidths(v.historyTable, widths);
        v.historyTable.getColumnModel().getColumn(LibraryViewState.HISTORY_STATUS_COLUMN).setCellRenderer(new LibraryTableStyles.BorrowStatusRenderer());
        // 书名放在最前面方便阅读；底层记录号仍保持第 0 列，归还继续使用记录号。
        v.historyTable.moveColumn(LibraryViewState.HISTORY_TITLE_COLUMN, 0);
        // 把状态、应还日及实体册前置，关键业务信息不用先滚过技术编号。
        v.historyTable.moveColumn(v.historyTable.convertColumnIndexToView(LibraryViewState.HISTORY_STATUS_COLUMN), 1);
        v.historyTable.moveColumn(v.historyTable.convertColumnIndexToView(6), 2);
        v.historyTable.moveColumn(v.historyTable.convertColumnIndexToView(9), 3);
        v.historyTable.moveColumn(v.historyTable.convertColumnIndexToView(10), 4);
    }

    /** 设置表格通用主题、选择模式及排序。 */
    static void configureTable(JTable table, int selectionMode) {
        VCampusTheme.table(table);
        table.setPreferredScrollableViewportSize(UiMetrics.dimension(0, 260));
        table.setSelectionMode(selectionMode);
        table.setAutoCreateRowSorter(true);
    }

    /** 应用缩放后的最小和推荐列宽，避免窄屏文字重叠。 */
    static void applyColumnWidths(JTable table, int[] widths) {
        for (int column = 0; column < widths.length && column < table.getColumnCount(); column++) {
            int width = UiMetrics.px(widths[column]);
            table.getColumnModel().getColumn(column).setMinWidth(width);
            table.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
    }

    /** 赔偿状态的中文文本和颜色渲染。 */
    static final class CompensationStatusRenderer extends DefaultTableCellRenderer {
        /** 保留模型原值，用中文文本和颜色呈现状态。 */
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            boolean paid = CompensationStatus.PAID.name().equals(String.valueOf(value));
            setText(paid ? "已结清" : "待支付");
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!selected) {
                Color color = paid ? VCampusTheme.SUCCESS : VCampusTheme.ACCENT;
                setForeground(color);
                setBackground(VCampusTheme.tintOf(color, 12));
            }
            return this;
        }
    }

    /** 借阅状态的中文文本和颜色渲染。 */
    static final class BorrowStatusRenderer extends DefaultTableCellRenderer {
        /** 保留模型原值，用中文文本和颜色呈现状态。 */
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            String label = LibraryRowMapper.borrowStatusLabel(value);
            setText(label);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(VCampusTheme.font(Font.BOLD, 12));
            setBorder(VCampusTheme.padding(5, 8, 5, 8));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if ("借阅中".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 12));
                setForeground(VCampusTheme.PRIMARY);
            } else if ("已归还".equals(label) || "已赔偿".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.SUCCESS, 12));
                setForeground(VCampusTheme.SUCCESS);
            } else if ("已遗失·待赔偿".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.ACCENT, 12));
                setForeground(VCampusTheme.ACCENT);
            } else {
                setBackground(row % 2 == 0 ? VCampusTheme.PANEL : VCampusTheme.TABLE_STRIPE);
                setForeground(VCampusTheme.TEXT);
            }
            return this;
        }
    }
}

package cn.vcampus.client.view;

import java.awt.Point;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

/** 库存轮询只更新数据；保留图书编号对应的多选、排序与滚动位置。 */
final class LibraryCatalogSelection {
    /** 数据未变不重绘；数据变化后按业务编号恢复仍存在的选中行。 */
    static void replace(LibraryViewState v, List<Object[]> rows) {
        if (sameRows(v.bookModel, rows)) return;
        Set<String> selected = new HashSet<String>(LibraryCatalogActions.selectedBookIds(v));
        JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, v.bookTable);
        Point position = viewport == null ? null : viewport.getViewPosition();
        v.bookModel.replaceRows(rows);
        v.bookTable.clearSelection();
        for (int row = 0; row < v.bookModel.getRowCount(); row++) {
            if (!selected.contains(String.valueOf(v.bookModel.getValueAt(row, 0)))) continue;
            int visibleRow = v.bookTable.convertRowIndexToView(row);
            if (visibleRow >= 0) v.bookTable.addRowSelectionInterval(visibleRow, visibleRow);
        }
        if (viewport != null) {
            position.x = Math.min(position.x, Math.max(0, v.bookTable.getPreferredSize().width - viewport.getExtentSize().width));
            position.y = Math.min(position.y, Math.max(0, v.bookTable.getPreferredSize().height - viewport.getExtentSize().height));
            viewport.setViewPosition(position);
        }
    }

    /** 逐单元格比较已投影数据，避免每5秒无变化的表格事件和闪烁。 */
    private static boolean sameRows(BatchTableModel model, List<Object[]> rows) {
        if (model.getRowCount() != rows.size()) return false;
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < model.getColumnCount(); col++) {
                if (!Objects.equals(model.getValueAt(row, col), rows.get(row)[col])) return false;
            }
        }
        return true;
    }
}

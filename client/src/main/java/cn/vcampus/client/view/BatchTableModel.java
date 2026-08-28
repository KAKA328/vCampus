package cn.vcampus.client.view;

import java.util.List;
import javax.swing.table.DefaultTableModel;

/** Table model that publishes one repaint notification for a complete response. */
final class BatchTableModel extends DefaultTableModel {
    private final Object[] columns;

    BatchTableModel(Object[] columns) {
        super(columns, 0);
        this.columns = columns.clone();
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    void replaceRows(List<Object[]> rows) {
        Object[][] tableRows = rows.toArray(new Object[rows.size()][]);
        setDataVector(tableRows, columns);
    }
}

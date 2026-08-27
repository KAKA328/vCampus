package cn.vcampus.client.view;

import java.util.List;
import javax.swing.table.DefaultTableModel;

/** Table model that publishes one repaint notification for a complete response. */
final class BatchTableModel extends DefaultTableModel {
    BatchTableModel(Object[] columns) {
        super(columns, 0);
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    void replaceRows(List<Object[]> rows) {
        dataVector.clear();
        for (Object[] row : rows) {
            dataVector.add(convertToVector(row));
        }
        fireTableDataChanged();
    }
}

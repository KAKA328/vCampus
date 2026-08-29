package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.user.UserImportFailure;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.DefaultTableModel;

/** Editable table model for the administrator batch user import page. */
final class UserImportTableModel extends DefaultTableModel {
    private static final int USER_ID = 0;
    private static final int DISPLAY_NAME = 1;
    private static final int PASSWORD = 2;
    private static final int ROLE = 3;
    private static final int RESULT = 4;

    UserImportTableModel() {
        super(new Object[] {"账号", "姓名", "初始密码", "角色", "导入结果"}, 0);
        addBlankRow();
    }

    void addBlankRow() {
        addRow(new Object[] {"", "", "", Role.STUDENT.name(), ""});
    }

    @Override public boolean isCellEditable(int row, int column) {
        return column >= USER_ID && column <= ROLE;
    }

    List<UserImportRow> toImportRows() {
        List<UserImportRow> rows = new ArrayList<UserImportRow>();
        for (int row = 0; row < getRowCount(); row++) {
            if (!isImportRow(row)) {
                continue;
            }
            rows.add(new UserImportRow(
                    text(row, USER_ID),
                    text(row, PASSWORD),
                    text(row, DISPLAY_NAME),
                    text(row, ROLE)));
        }
        return rows;
    }

    void clearResults() {
        for (int row = 0; row < getRowCount(); row++) {
            setValueAt("", row, RESULT);
        }
    }

    void applyResult(UserImportResult result) {
        clearResults();
        Map<Integer, UserImportFailure> failuresByRow = new HashMap<Integer, UserImportFailure>();
        for (UserImportFailure failure : result.getFailures()) {
            failuresByRow.put(failure.getRowNumber(), failure);
        }

        int importRowNumber = 0;
        for (int row = 0; row < getRowCount(); row++) {
            if (!isImportRow(row)) {
                continue;
            }
            importRowNumber++;
            UserImportFailure failure = failuresByRow.get(importRowNumber);
            setValueAt(failure == null ? "成功" : "失败：" + failure.getMessage(), row, RESULT);
        }
    }

    void removeSelectedRows(int[] viewRows, javax.swing.JTable table) {
        if (viewRows == null || viewRows.length == 0) {
            return;
        }
        int[] modelRows = new int[viewRows.length];
        for (int i = 0; i < viewRows.length; i++) {
            modelRows[i] = table.convertRowIndexToModel(viewRows[i]);
        }
        java.util.Arrays.sort(modelRows);
        for (int i = modelRows.length - 1; i >= 0; i--) {
            removeRow(modelRows[i]);
        }
        if (getRowCount() == 0) {
            addBlankRow();
        }
    }

    void commitActiveEditor(javax.swing.JTable table) {
        if (table != null && table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private boolean isImportRow(int row) {
        return !text(row, USER_ID).isEmpty()
                || !text(row, DISPLAY_NAME).isEmpty()
                || !text(row, PASSWORD).isEmpty();
    }

    private String text(int row, int column) {
        Object value = getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }
}

package cn.vcampus.client.view;

import cn.vcampus.user.UserImportFailure;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.table.DefaultTableModel;

/** Preview table model for accounts read from an external import file. */
final class UserImportTableModel extends DefaultTableModel {
    private static final int RESULT = 4;

    private final List<UserImportRow> importRows = new ArrayList<UserImportRow>();

    UserImportTableModel() {
        super(new Object[] {"账号", "姓名", "角色", "档案编号", "导入结果"}, 0);
    }

    @Override public boolean isCellEditable(int row, int column) {
        return false;
    }

    void replaceImportRows(List<UserImportRow> rows) {
        importRows.clear();
        dataVector.clear();
        if (rows != null) {
            for (UserImportRow row : rows) {
                if (row == null) {
                    continue;
                }
                importRows.add(row);
                dataVector.add(convertToVector(new Object[] {
                        row.getUserId(), row.getDisplayName(), row.getRoleCode(), row.getProfileId(), ""
                }));
            }
        }
        fireTableDataChanged();
    }

    List<UserImportRow> toImportRows() {
        return new ArrayList<UserImportRow>(importRows);
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
        for (int row = 0; row < getRowCount(); row++) {
            UserImportFailure failure = failuresByRow.get(row + 1);
            setValueAt(failure == null ? "成功" : "失败：" + failure.getMessage(), row, RESULT);
        }
    }
}

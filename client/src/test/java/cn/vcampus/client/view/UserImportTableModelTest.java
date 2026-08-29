package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.user.UserImportFailure;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserImportTableModelTest {
    @Test
    void newModelStartsWithOneStudentRowForConvenientEntry() {
        UserImportTableModel model = new UserImportTableModel();

        assertEquals(1, model.getRowCount());
        assertEquals(Role.STUDENT.name(), model.getValueAt(0, 3));
        assertEquals("", model.getValueAt(0, 4));
        assertFalse(model.isCellEditable(0, 4), "result column should be controlled by import response");
    }

    @Test
    void importRowsSkipBlankRowsAndPreserveVisibleOrder() {
        UserImportTableModel model = new UserImportTableModel();
        fill(model, 0, " stu1001 ", " 初始密码 ", " 张三 ", Role.STUDENT.name());
        model.addBlankRow();
        model.addBlankRow();
        fill(model, 2, "tea1001", "Teacher1", "李老师", Role.TEACHER.name());

        List<UserImportRow> rows = model.toImportRows();

        assertEquals(2, rows.size());
        assertEquals("stu1001", rows.get(0).getUserId());
        assertEquals("张三", rows.get(0).getDisplayName());
        assertEquals("tea1001", rows.get(1).getUserId());
    }

    @Test
    void importResultMarksSuccessAndFailureRowsByImportOrder() {
        UserImportTableModel model = new UserImportTableModel();
        fill(model, 0, "stu1002", "Demo123", "学生甲", Role.STUDENT.name());
        model.addBlankRow();
        fill(model, 1, "stu1003", "Demo123", "学生乙", Role.STUDENT.name());

        model.applyResult(new UserImportResult("batch-1", 2, 1, Arrays.asList(
                new UserImportFailure(2, "stu1003", "user already exists"))));

        assertEquals("成功", model.getValueAt(0, 4));
        assertTrue(String.valueOf(model.getValueAt(1, 4)).contains("user already exists"));
    }

    @Test
    void clearResultsOnlyTouchesResultColumn() {
        UserImportTableModel model = new UserImportTableModel();
        fill(model, 0, "stu1004", "Demo123", "学生丙", Role.STUDENT.name());
        model.applyResult(new UserImportResult("batch-2", 1, 1, Collections.<UserImportFailure>emptyList()));

        model.clearResults();

        assertEquals("stu1004", model.getValueAt(0, 0));
        assertEquals("", model.getValueAt(0, 4));
    }

    @Test
    void commitActiveEditorBeforeImportKeepsLastEditedCell() {
        UserImportTableModel model = new UserImportTableModel();
        JTable table = new JTable(model);
        table.editCellAt(0, 0);
        table.getEditorComponent();
        ((javax.swing.JTextField) table.getEditorComponent()).setText("stu1005");

        model.commitActiveEditor(table);

        assertEquals("stu1005", model.toImportRows().get(0).getUserId());
    }

    private static void fill(UserImportTableModel model, int row,
            String userId, String password, String displayName, String role) {
        model.setValueAt(userId, row, 0);
        model.setValueAt(displayName, row, 1);
        model.setValueAt(password, row, 2);
        model.setValueAt(role, row, 3);
    }
}

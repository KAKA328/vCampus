package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.user.UserImportFailure;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserImportTableModelTest {
    @Test
    void newModelStartsEmptyForFilePreview() {
        UserImportTableModel model = new UserImportTableModel();

        assertEquals(0, model.getRowCount());
        assertEquals("账号", model.getColumnName(0));
        assertEquals("姓名", model.getColumnName(1));
        assertEquals("角色", model.getColumnName(2));
        assertEquals("档案编号", model.getColumnName(3));
        assertEquals("导入结果", model.getColumnName(4));
    }

    @Test
    void previewRowsHideInitialPasswordsAndAreNotEditable() {
        UserImportTableModel model = new UserImportTableModel();

        model.replaceImportRows(Arrays.asList(
                new UserImportRow("stu1001", "Secret123", "张三", Role.STUDENT.name(), "20240001")));

        assertEquals(1, model.getRowCount());
        assertEquals("stu1001", model.getValueAt(0, 0));
        assertEquals("张三", model.getValueAt(0, 1));
        assertEquals(Role.STUDENT.name(), model.getValueAt(0, 2));
        assertEquals("20240001", model.getValueAt(0, 3));
        assertEquals("", model.getValueAt(0, 4));
        assertFalse(model.isCellEditable(0, 0));
    }

    @Test
    void importRowsComeFromLoadedFileRows() {
        UserImportTableModel model = new UserImportTableModel();
        model.replaceImportRows(Arrays.asList(
                new UserImportRow("stu1001", "Secret123", "张三", Role.STUDENT.name()),
                new UserImportRow("tea1001", "Teacher1", "李老师", Role.TEACHER.name())));

        assertEquals(2, model.toImportRows().size());
        assertEquals("Secret123", model.toImportRows().get(0).getPassword());
        assertEquals("tea1001", model.toImportRows().get(1).getUserId());
        assertEquals("", model.toImportRows().get(1).getProfileId());
    }

    @Test
    void importResultMarksSuccessAndFailureRowsByFileOrder() {
        UserImportTableModel model = new UserImportTableModel();
        model.replaceImportRows(Arrays.asList(
                new UserImportRow("stu1002", "Demo123", "学生甲", Role.STUDENT.name()),
                new UserImportRow("stu1003", "Demo123", "学生乙", Role.STUDENT.name())));

        model.applyResult(new UserImportResult("batch-1", 2, 1, Arrays.asList(
                new UserImportFailure(2, "stu1003", "user already exists"))));

        assertEquals("成功", model.getValueAt(0, 4));
        assertTrue(String.valueOf(model.getValueAt(1, 4)).contains("user already exists"));
    }

    @Test
    void clearResultsOnlyTouchesResultColumn() {
        UserImportTableModel model = new UserImportTableModel();
        model.replaceImportRows(Arrays.asList(
                new UserImportRow("stu1004", "Demo123", "学生丙", Role.STUDENT.name())));
        model.applyResult(new UserImportResult("batch-2", 1, 1,
                java.util.Collections.<UserImportFailure>emptyList()));

        model.clearResults();

        assertEquals("stu1004", model.getValueAt(0, 0));
        assertEquals("", model.getValueAt(0, 4));
    }
}

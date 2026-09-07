package cn.vcampus.client.view;

import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcademicAdministrationPanelTest {
    @Test void historyAttemptNumbersSortNumerically() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = new AcademicAdministrationPanel("token", command -> null);
                panel.display(Action.HISTORY, response(java.util.Arrays.asList(
                        new CourseHistoryRecord("S001", "C1", "课程", "2026-2027-1", 10, "重修", 60, true, 3),
                        new CourseHistoryRecord("S001", "C1", "课程", "2026-2027-1", 2, "重修", 50, false, 0))));
                JTable table = field(panel, "table", JTable.class);
                table.getRowSorter().toggleSortOrder(3);
                assertEquals(2, table.getValueAt(0, 3));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void sortingAndFilteringStillSelectTheCorrectStudent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = new AcademicAdministrationPanel("token", command -> null);
                StudentRecord first = new StudentRecord("S002", "a", "乙", "未知", "院系", "专业", "班", 2026, "在读", "", "");
                StudentRecord second = new StudentRecord("S001", "b", "甲", "未知", "院系", "专业", "班", 2026, "在读", "", "");
                panel.display(Action.STUDENTS, response(java.util.Arrays.asList(first, second)));
                JTable table = field(panel, "table", JTable.class);
                table.getRowSorter().toggleSortOrder(0);
                table.setRowSelectionInterval(0, 0);
                assertEquals("S001", field(panel, "studentId", JTextField.class).getText());
                field(panel, "filter", JTextField.class).setText("S002");
                assertEquals(1, table.getRowCount());
                table.setRowSelectionInterval(0, 0);
                assertEquals("S002", field(panel, "studentId", JTextField.class).getText());
                field(panel, "filter", JTextField.class).setText("[");
                assertEquals(0, table.getRowCount());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void directoryDisplaysCompleteStudentAndTeacherInformation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = new AcademicAdministrationPanel("token", command -> null);
                panel.display(Action.STUDENTS, response(Collections.singletonList(new StudentRecord(
                        "S001", "account", "学生", "未知", "院系", "专业", "班级", 2026, "休学", "123", "mail"))));
                JTable table = field(panel, "table", JTable.class);
                assertEquals(11, table.getColumnCount());
                assertEquals("123", table.getValueAt(0, 9));
                table.setRowSelectionInterval(0, 0);
                assertEquals("S001", field(panel, "studentId", JTextField.class).getText());
                panel.display(Action.TEACHERS, response(Collections.singletonList(
                        new TeacherProfile("T001", "teacher", "教师", "院系", "教授", false))));
                assertEquals("非在职", table.getValueAt(0, 5));
                assertFalse(table.isCellEditable(0, 0));
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void changingTargetOrFailureDisablesGraduationAndClearsResults() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = new AcademicAdministrationPanel("token", command -> null);
                JTextField id = field(panel, "studentId", JTextField.class);
                id.setText("S001");
                AcademicAssessment review = new AcademicAssessment("review", new CreditSummary("S001", 6, 2, 0, 1),
                        6, "evidence", "academic", Instant.now(), "依据", null, null, null);
                panel.display(Action.REVIEW, response(review));
                JButton graduate = field(panel, "graduate", JButton.class);
                assertFalse(graduate.isEnabled());
                field(panel, "confirmed", JCheckBox.class).doClick();
                assertTrue(graduate.isEnabled());
                id.setText("S002");
                assertFalse(graduate.isEnabled());
                panel.display(Action.ASSESSMENTS, Message.response(request(), StatusCode.CONFLICT, "过期"));
                assertEquals(0, field(panel, "table", JTable.class).getRowCount());
                assertFalse(graduate.isEnabled());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    @Test void insufficientReviewCannotEnableGraduationEvenAfterCheckbox() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                AcademicAdministrationPanel panel = new AcademicAdministrationPanel("token", command -> null);
                field(panel, "studentId", JTextField.class).setText("S001");
                panel.display(Action.REVIEW, response(new AcademicAssessment("review",
                        new CreditSummary("S001", 3, 1, 1, 0), 6, "hash", "academic", Instant.now(),
                        "依据", null, null, null)));
                field(panel, "confirmed", JCheckBox.class).doClick();
                assertFalse(field(panel, "graduate", JButton.class).isEnabled());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static Message request() { return Message.request("admin", MessageType.ACADEMIC_ADMIN_V1, null); }
    private static Message response(Object data) { return Message.response(request(), StatusCode.OK, data); }
    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return type.cast(field.get(object));
    }
}

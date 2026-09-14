package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证教师教学班页使用统一表格样式，且不会在请求期间重复查询。 */
class TeacherTeachingPanelTest {
    @Test
    void presentsTeachingClassIdentifierAndCourseNameBeforeEnteringDetail() throws Exception {
        TeacherTeachingPanel panel = panel();
        JTable table = table(panel);

        assertTrue(table.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF);
        assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() >= UiMetrics.px(170));
        assertTrue(table.getColumnModel().getColumn(1).getPreferredWidth() >= UiMetrics.px(280));
        assertTrue(button(panel).isEnabled());
        assertFalse(button(panel, "viewRosterButton").isEnabled());
        assertFalse(button(panel, "openDraftButton").isEnabled());
        assertFalse(button(panel, "saveGradeButton").isEnabled());
        assertFalse(button(panel, "submitGradesButton").isEnabled());
        assertFalse(button(panel, "chooseGradeFileButton").isEnabled());
        assertFalse(button(panel, "importGradesButton").isEnabled());
    }

    @Test
    void usesFixedTermChoicesForAutomaticTeachingClassRefresh() throws Exception {
        TeacherTeachingPanel panel = panel();
        Field field = TeacherTeachingPanel.class.getDeclaredField("term");
        field.setAccessible(true);
        JComboBox<?> term = (JComboBox<?>) field.get(panel);

        assertEquals(3, term.getItemCount());
        assertEquals("2026-2027-1", term.getSelectedItem());
    }

    @Test
    void refreshIsDisabledWhileRequestIsRunning() throws Exception {
        TeacherTeachingPanel panel = panel();
        Field busy = TeacherTeachingPanel.class.getDeclaredField("requestInProgress");
        busy.setAccessible(true);
        busy.setBoolean(panel, true);
        java.lang.reflect.Method update = TeacherTeachingPanel.class
                .getDeclaredMethod("updateInteractiveState");
        update.setAccessible(true);
        update.invoke(panel);

        assertFalse(button(panel).isEnabled());
        assertFalse(button(panel, "viewRosterButton").isEnabled());
        assertFalse(button(panel, "openDraftButton").isEnabled());
        assertFalse(button(panel, "saveGradeButton").isEnabled());
        assertFalse(button(panel, "submitGradesButton").isEnabled());
        assertFalse(button(panel, "chooseGradeFileButton").isEnabled());
        assertFalse(button(panel, "importGradesButton").isEnabled());
    }

    @Test
    void presentsTeachingAndGradeWorkAsSeparateSteps() throws Exception {
        TeacherTeachingPanel panel = panel();

        assertEquals("学生名单", label(panel, "rosterTitle").getText());
        assertTrue(label(panel, "rosterHint").getText().contains("选择一个教学班"));
        assertTrue(button(panel, "viewRosterButton").getUI()
                instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "submitGradesButton").getUI()
                instanceof VCampusTheme.ReadableButtonUI);
    }

    @Test
    void exposesTheOpenDraftActionInTheTeachingClassDetailWorkflow() throws Exception {
        TeacherTeachingPanel panel = panel();

        assertNotNull(button(panel, "openDraftButton").getParent(),
                "教师必须能够从教学班详情页触发打开成绩草稿");
    }

    @Test
    void providesDedicatedDraftPageAndReturnToRosterAction() throws Exception {
        TeacherTeachingPanel panel = panel();
        Field pagesField = TeacherTeachingPanel.class.getDeclaredField("pages");
        pagesField.setAccessible(true);
        javax.swing.JPanel pages = (javax.swing.JPanel) pagesField.get(panel);

        assertEquals(3, pages.getComponentCount(), "教学班、名单和成绩草稿应为三个独立页面");
        assertNotNull(button(panel, "backToRosterButton").getParent(),
                "成绩草稿页必须提供返回学生名单的操作");
        assertNotNull(button(panel, "saveGradeButton").getParent(),
                "成绩登记操作必须保留在独立成绩草稿页");
    }

    private static TeacherTeachingPanel panel() {
        return new TeacherTeachingPanel("localhost", 19090,
                new Session("token", new User("teacher-001", "任课老师", Role.TEACHER)));
    }

    private static JButton button(TeacherTeachingPanel panel) throws Exception {
        return button(panel, "refreshButton");
    }

    private static JButton button(TeacherTeachingPanel panel, String name) throws Exception {
        Field field = TeacherTeachingPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }

    private static JTable table(TeacherTeachingPanel panel) throws Exception {
        Field field = TeacherTeachingPanel.class.getDeclaredField("table");
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static JLabel label(TeacherTeachingPanel panel, String name) throws Exception {
        Field field = TeacherTeachingPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JLabel) field.get(panel);
    }
}

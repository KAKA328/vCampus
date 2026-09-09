package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class CourseSelectionPanelTest {
    @Test
    void presentsRoundCardsInsteadOfLegacyDropdown() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("roundCards");
        field.setAccessible(true);
        assertNotNull(field.get(panel));
        assertTrue(field.get(panel) instanceof JPanel);
    }

    @Test
    void disablesAllCourseActionsWhileRequestIsInProgress() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field busy = CourseSelectionPanel.class.getDeclaredField("requestInProgress");
        busy.setAccessible(true);
        busy.setBoolean(panel, true);
        Method update = CourseSelectionPanel.class.getDeclaredMethod("updateInteractiveState");
        update.setAccessible(true);
        update.invoke(panel);

        assertFalse(button(panel, "courseDetailButton").isEnabled());
        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
        assertFalse(button(panel, "backToRoundsButton").isEnabled());
        assertFalse(button(panel, "backToCoursesButton").isEnabled());
        assertFalse(button(panel, "dropButton").isEnabled());

        busy.setBoolean(panel, false);
        update.invoke(panel);
        assertFalse(button(panel, "courseDetailButton").isEnabled());
        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
    }

    @Test
    void appliesSharedThemeToCourseActions() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        assertTrue(button(panel, "courseDetailButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "selectedCoursesButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "backToRoundsButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "backToCoursesButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "dropButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    @Test
    void presentsStudentFlowAsRoundSelectionFirst() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));

        assertTrue(label(panel, "pageSubtitle").getText().contains("请选择一个当前开放的选课轮次"));
        assertFalse(button(panel, "selectedCoursesButton").isEnabled());
    }

    @Test
    void keepsCourseTableColumnsReadableOnCompactWindows() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("courseTable");
        field.setAccessible(true);
        JTable table = (JTable) field.get(panel);

        assertTrue(table.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF);
        assertTrue(table.getColumnModel().getColumn(0).getPreferredWidth() >= UiMetrics.px(150));
        assertTrue(table.getColumnModel().getColumn(1).getPreferredWidth() >= UiMetrics.px(260));
    }

    private static AbstractButton button(CourseSelectionPanel panel, String fieldName) throws Exception {
        Field field = CourseSelectionPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (AbstractButton) field.get(panel);
    }

    private static JLabel label(CourseSelectionPanel panel, String fieldName) throws Exception {
        Field field = CourseSelectionPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JLabel) field.get(panel);
    }
}

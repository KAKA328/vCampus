package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class CourseSelectionPanelTest {
    @Test
    void providesRoundSelectorForCurrentCourseSelectionFlow() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("roundBox");
        field.setAccessible(true);
        assertNotNull(field.get(panel));
        assertNotNull((JComboBox<?>) field.get(panel));
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

        assertFalse(button(panel, "loadRoundsButton").isEnabled());
        assertFalse(button(panel, "refreshViewButton").isEnabled());
        assertFalse(button(panel, "offeringsViewButton").isEnabled());
        assertFalse(button(panel, "selectedViewButton").isEnabled());
        assertFalse(button(panel, "primaryActionButton").isEnabled());

        busy.setBoolean(panel, false);
        update.invoke(panel);
        assertTrue(button(panel, "loadRoundsButton").isEnabled());
        assertFalse(button(panel, "refreshViewButton").isEnabled());
        assertFalse(button(panel, "primaryActionButton").isEnabled());
    }

    @Test
    void appliesSharedThemeToCourseActions() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        assertTrue(button(panel, "loadRoundsButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "refreshViewButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "offeringsViewButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "selectedViewButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "primaryActionButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    @Test
    void presentsStudentFlowAsOfferingsFirstThenSelectedCourses() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));

        assertTrue(button(panel, "offeringsViewButton").isSelected());
        assertFalse(button(panel, "selectedViewButton").isSelected());
        assertEquals("本轮可选教学班", label(panel, "tableTitle").getText());
        assertEquals("选择所选教学班", button(panel, "primaryActionButton").getText());
    }

    @Test
    void keepsCourseTableColumnsReadableOnCompactWindows() throws Exception {
        CourseSelectionPanel panel = new CourseSelectionPanel("localhost", 19090,
                new Session("token", new User("student-001", "测试学生", Role.STUDENT)));
        Field field = CourseSelectionPanel.class.getDeclaredField("table");
        field.setAccessible(true);
        JTable table = (JTable) field.get(panel);

        assertTrue(table.getAutoResizeMode() == JTable.AUTO_RESIZE_OFF);
        assertTrue(table.getColumnModel().getColumn(2).getPreferredWidth() >= UiMetrics.px(170));
        assertTrue(table.getColumnModel().getColumn(6).getPreferredWidth() >= UiMetrics.px(220));
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

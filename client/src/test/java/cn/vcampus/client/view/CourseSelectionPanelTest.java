package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
        assertFalse(button(panel, "loadOfferingsButton").isEnabled());
        assertFalse(button(panel, "selectedButton").isEnabled());
        assertFalse(button(panel, "selectButton").isEnabled());
        assertFalse(button(panel, "dropButton").isEnabled());

        busy.setBoolean(panel, false);
        update.invoke(panel);
        assertTrue(button(panel, "selectButton").isEnabled());
    }

    private static JButton button(CourseSelectionPanel panel, String fieldName) throws Exception {
        Field field = CourseSelectionPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }
}

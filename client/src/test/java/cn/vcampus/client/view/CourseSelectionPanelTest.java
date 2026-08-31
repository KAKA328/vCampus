package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
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
}

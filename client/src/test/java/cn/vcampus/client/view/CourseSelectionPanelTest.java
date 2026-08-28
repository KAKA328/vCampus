package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CourseSelectionPanelTest {
    @Test
    void refreshDoesNotImmediatelyReplaceExistingStatusWithLoadingMessage() throws Exception {
        final CourseSelectionPanel[] panel = new CourseSelectionPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new CourseSelectionPanel(
                "127.0.0.1", 1, new Session("token", new User("20230001", "测试学生", Role.STUDENT))));

        JLabel status = field(panel[0], "status", JLabel.class);
        JButton refreshButton = field(panel[0], "refreshButton", JButton.class);

        SwingUtilities.invokeAndWait(() -> {
            status.setText("已显示全部课程，共 3 门");
            status.setForeground(VCampusTheme.SUCCESS);

            refreshButton.doClick();

            assertEquals("已显示全部课程，共 3 门", status.getText());
            assertEquals(VCampusTheme.SUCCESS, status.getForeground());
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}

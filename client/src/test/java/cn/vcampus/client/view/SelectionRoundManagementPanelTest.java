package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证选课轮次页保留分步骤操作所需的核心控件。 */
class SelectionRoundManagementPanelTest {
    @Test
    void keepsRoundListReadableAndCreationActionProminent() throws Exception {
        SelectionRoundManagementPanel panel = panel();
        JTable table = table(panel, "table");

        assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
        assertTrue(table.getColumnModel().getColumn(3).getPreferredWidth() >= UiMetrics.px(170));
        assertTrue(button(panel, "createButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "refreshButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    private static SelectionRoundManagementPanel panel() {
        return new SelectionRoundManagementPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));
    }

    private static JTable table(SelectionRoundManagementPanel panel, String name) throws Exception {
        Field field = SelectionRoundManagementPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static JButton button(SelectionRoundManagementPanel panel, String name) throws Exception {
        Field field = SelectionRoundManagementPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }
}

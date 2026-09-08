package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证成绩审核页初始状态不会在未选择成绩单时误开放审核操作。 */
class GradeReviewPanelTest {
    @Test
    void reviewActionsRequireSelectingSubmission() throws Exception {
        GradeReviewPanel panel = new GradeReviewPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));

        assertTrue(button(panel, "refreshButton").isEnabled());
        assertFalse(button(panel, "detailButton").isEnabled());
        assertFalse(button(panel, "auditButton").isEnabled());
        assertFalse(button(panel, "approveButton").isEnabled());
        assertFalse(button(panel, "returnButton").isEnabled());
        assertTrue(table(panel, "submissionTable").getAutoResizeMode()
                == JTable.AUTO_RESIZE_OFF);
    }

    @Test
    void presentsReviewAsSelectionThenVerificationThenDecision() throws Exception {
        GradeReviewPanel panel = new GradeReviewPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));

        assertTrue(label(panel, "selectionHint").getText().contains("选择"));
        assertTrue(button(panel, "detailButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "approveButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    private static JButton button(GradeReviewPanel panel, String name) throws Exception {
        Field field = GradeReviewPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }

    private static JTable table(GradeReviewPanel panel, String name) throws Exception {
        Field field = GradeReviewPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static JLabel label(GradeReviewPanel panel, String name) throws Exception {
        Field field = GradeReviewPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JLabel) field.get(panel);
    }
}

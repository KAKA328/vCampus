package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.course.TrainingPlanStatus;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.lang.reflect.Field;
import javax.swing.JButton;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证培养方案页将方案与课程要求分别放入可读的工作区。 */
class TrainingPlanManagementPanelTest {
    @Test
    void keepsPlanAndCourseRequirementTablesReadable() throws Exception {
        TrainingPlanManagementPanel panel = panel();
        JTable plans = table(panel, "planTable");
        JTable courses = table(panel, "courseTable");

        assertEquals(JTable.AUTO_RESIZE_OFF, plans.getAutoResizeMode());
        assertEquals(JTable.AUTO_RESIZE_OFF, courses.getAutoResizeMode());
        assertEquals("课程名称", courses.getColumnName(1));
        assertEquals("学分", courses.getColumnName(2));
        assertTrue(courses.getColumnModel().getColumn(1).getPreferredWidth() >= UiMetrics.px(220));
        assertTrue(button(panel, "createButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "editPlanButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
        assertTrue(button(panel, "saveCourseButton").getUI() instanceof VCampusTheme.ReadableButtonUI);
    }

    @Test
    void describesTheAvailableStatusActionForEachPlanState() {
        assertEquals("发布方案", TrainingPlanManagementPanel.statusActionText(
                TrainingPlanStatus.DRAFT));
        assertEquals("撤回或归档", TrainingPlanManagementPanel.statusActionText(
                TrainingPlanStatus.PUBLISHED));
        assertEquals("恢复为草稿", TrainingPlanManagementPanel.statusActionText(
                TrainingPlanStatus.ARCHIVED));
    }

    private static TrainingPlanManagementPanel panel() {
        return new TrainingPlanManagementPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));
    }

    private static JTable table(TrainingPlanManagementPanel panel, String name) throws Exception {
        Field field = TrainingPlanManagementPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }

    private static JButton button(TrainingPlanManagementPanel panel, String name) throws Exception {
        Field field = TrainingPlanManagementPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JButton) field.get(panel);
    }
}

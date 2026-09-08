package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Component;
import java.lang.reflect.Field;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证教务选课管理页已接入轮次、培养方案和成绩审核三个工作区。 */
class CourseManagementPanelTest {
    @Test
    void includesAllCourseManagementTabs() {
        CourseManagementPanel panel = new CourseManagementPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));
        JScrollPane scroll = (JScrollPane) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        JPanel body = (JPanel) scroll.getViewport().getView();
        Component center = ((BorderLayout) body.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        JTabbedPane tabs = (JTabbedPane) center;

        assertEquals(5, tabs.getTabCount());
        assertEquals("选课轮次", tabs.getTitleAt(2));
        assertTrue(tabs.getComponentAt(2) instanceof SelectionRoundManagementPanel);
        assertEquals("培养方案", tabs.getTitleAt(3));
        assertTrue(tabs.getComponentAt(3) instanceof TrainingPlanManagementPanel);
        assertEquals("成绩审核", tabs.getTitleAt(4));
        assertTrue(tabs.getPreferredSize().height >= UiMetrics.px(720));
    }

    @Test
    void keepsCatalogAndOfferingTablesReadableAtCompactWidths() throws Exception {
        CourseManagementPanel panel = new CourseManagementPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));
        JTable courseTable = table(panel, "courseTable");
        JTable offeringTable = table(panel, "offeringTable");

        assertEquals(JTable.AUTO_RESIZE_OFF, courseTable.getAutoResizeMode());
        assertEquals(JTable.AUTO_RESIZE_OFF, offeringTable.getAutoResizeMode());
        assertTrue(offeringTable.getColumnModel().getColumn(4).getPreferredWidth()
                >= UiMetrics.px(220));
    }

    private static JTable table(CourseManagementPanel panel, String name) throws Exception {
        Field field = CourseManagementPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return (JTable) field.get(panel);
    }
}

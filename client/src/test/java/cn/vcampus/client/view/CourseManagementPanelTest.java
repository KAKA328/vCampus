package cn.vcampus.client.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.lang.reflect.Field;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

/** 验证教务选课管理页以入口卡片组织五个独立工作区。 */
class CourseManagementPanelTest {
    @Test
    void presentsFiveManagementWorkspacesBehindLandingPage() throws Exception {
        CourseManagementPanel panel = new CourseManagementPanel("localhost", 19090,
                new Session("token", new User("academic-001", "教务老师", Role.ACADEMIC_ADMIN)));
        Field field = CourseManagementPanel.class.getDeclaredField("managementPages");
        field.setAccessible(true);
        ScrollablePagePanel pages = (ScrollablePagePanel) field.get(panel);

        assertTrue(pages.getLayout() instanceof CardLayout);
        assertEquals(6, pages.getComponentCount());
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

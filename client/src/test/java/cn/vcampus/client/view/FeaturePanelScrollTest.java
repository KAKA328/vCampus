package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeaturePanelScrollTest {
    @Test
    void studentManagementBodyCanScrollWhenWindowIsShort() {
        assertPageBodyScrolls(new StudentManagementPanel("127.0.0.1", 1,
                session(Role.ACADEMIC_ADMIN)));
    }

    @Test
    void studentManagementUsesResultAndDetailWorkspace() {
        StudentManagementPanel panel = new StudentManagementPanel("127.0.0.1", 1,
                session(Role.ACADEMIC_ADMIN));
        JScrollPane scroller = (JScrollPane) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        JPanel body = (JPanel) scroller.getViewport().getView();
        Component center = ((BorderLayout) body.getLayout()).getLayoutComponent(BorderLayout.CENTER);

        assertTrue(center instanceof JSplitPane,
                "student management should use a result table plus detail editor workspace");
    }

    @Test
    void courseSelectionBodyCanScrollWhenWindowIsShort() {
        assertPageBodyScrolls(new CourseSelectionPanel("127.0.0.1", 1,
                session(Role.STUDENT)));
    }

    @Test
    void courseManagementBodyCanScrollWhenWindowIsShort() {
        assertPageBodyScrolls(new CourseManagementPanel("127.0.0.1", 1,
                session(Role.ACADEMIC_ADMIN)));
    }

    @Test
    void storeBodyCanScrollWhenWindowIsShort() {
        assertPageBodyScrolls(new StorePanel("127.0.0.1", 1,
                session(Role.STUDENT)));
    }

    @Test
    void libraryBodyCanScrollWhenWindowIsShort() {
        assertPageBodyScrolls(new LibraryPanel("127.0.0.1", 1,
                session(Role.STUDENT)));
    }

    private static Session session(Role role) {
        return new Session("token", new User("user-001", "测试用户", role));
    }

    private static void assertPageBodyScrolls(JPanel panel) {
        assertTrue(panel.getLayout() instanceof BorderLayout);
        Component center = ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        assertTrue(center instanceof JScrollPane, "feature page center should be a page-level scroll pane");
        Component view = ((JScrollPane) center).getViewport().getView();
        assertTrue(view instanceof ScrollablePagePanel);
        Scrollable scrollable = (Scrollable) view;
        assertTrue(scrollable.getScrollableTracksViewportWidth());
        assertTrue(!scrollable.getScrollableTracksViewportHeight());
    }
}

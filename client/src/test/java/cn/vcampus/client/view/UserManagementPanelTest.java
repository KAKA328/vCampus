package cn.vcampus.client.view;

import cn.vcampus.common.Role;
import cn.vcampus.common.User;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementPanelTest {
    @Test
    void pageKeepsHeaderSeparateAndLetsImportAreaGrow() {
        UserManagementPanel panel = new UserManagementPanel(null,
                "127.0.0.1", 19090, new Session("token", new User("admin", "管理员", Role.ADMIN)));

        assertTrue(panel.getLayout() instanceof BorderLayout);
        BorderLayout pageLayout = (BorderLayout) panel.getLayout();
        assertNotNull(pageLayout.getLayoutComponent(BorderLayout.NORTH));
        JScrollPane scroller = (JScrollPane) pageLayout.getLayoutComponent(BorderLayout.CENTER);
        assertTrue(scroller.getViewport().getView() instanceof JPanel);
        assertTrue(scroller.getViewport().getView() instanceof Scrollable);

        JPanel body = (JPanel) scroller.getViewport().getView();
        assertTrue(body.getLayout() instanceof BorderLayout);

        BorderLayout bodyLayout = (BorderLayout) body.getLayout();
        assertNotNull(bodyLayout.getLayoutComponent(BorderLayout.CENTER));
        assertTrue(buttonTexts(panel).contains("刷新账号列表"));
        assertTrue(buttonTexts(panel).contains("刷新审计记录"));
        assertTrue(components(panel, JTable.class).size() >= 4);
    }

    @Test
    void compactInitialWindowUsesScrollingInsteadOfSquashingCards() {
        UserManagementPanel panel = new UserManagementPanel(null,
                "127.0.0.1", 19090, new Session("token", new User("admin", "管理员", Role.ADMIN)));

        BorderLayout pageLayout = (BorderLayout) panel.getLayout();
        JScrollPane scroller = (JScrollPane) pageLayout.getLayoutComponent(BorderLayout.CENTER);
        Scrollable body = (Scrollable) scroller.getViewport().getView();

        assertTrue(body.getScrollableTracksViewportWidth(),
                "user management content should follow the available width");
        assertTrue(!body.getScrollableTracksViewportHeight(),
                "user management content should scroll vertically instead of being squeezed shorter");
    }

    @Test
    void pageOffersSingleCreateAndBatchImportActions() {
        UserManagementPanel panel = new UserManagementPanel(null,
                "127.0.0.1", 19090, new Session("token", new User("admin", "管理员", Role.ADMIN)));

        assertTrue(buttonTexts(panel).contains("创建单个账号"));
        assertTrue(buttonTexts(panel).contains("选择Excel/CSV文件"));
        assertTrue(buttonTexts(panel).contains("导入文件账号"));
        assertTrue(buttonTexts(panel).contains("刷新重置申请"));
        assertTrue(buttonTexts(panel).contains("通过重置"));
        assertTrue(buttonTexts(panel).contains("拒绝重置"));
        assertTrue(buttonTexts(panel).contains("刷新账号列表"));
        assertTrue(buttonTexts(panel).contains("启用账号"));
        assertTrue(buttonTexts(panel).contains("停用账号"));
        assertTrue(buttonTexts(panel).contains("注销账号"));
        assertTrue(buttonTexts(panel).contains("刷新审计记录"));
        assertTrue(labels(panel).contains("尚未选择导入文件"));
        assertTrue(components(panel, JTable.class).size() >= 4);
        assertTrue(components(panel, JScrollPane.class).size() >= 4);
    }

    @Test
    void singleAccountCardUsesConciseTeacherFacingCopy() {
        UserManagementPanel panel = new UserManagementPanel(null,
                "127.0.0.1", 19090, new Session("token", new User("admin", "管理员", Role.ADMIN)));

        for (JTextArea textArea : components(panel, JTextArea.class)) {
            assertTrue(!textArea.getText().contains("适合临时补一个账号"));
            assertTrue(!textArea.getText().contains("学生账号创建后仍需"));
        }
    }

    private static List<String> buttonTexts(Container root) {
        List<String> texts = new ArrayList<String>();
        for (JButton button : components(root, JButton.class)) {
            texts.add(button.getText());
        }
        return texts;
    }

    private static List<String> labels(Container root) {
        List<String> texts = new ArrayList<String>();
        for (JLabel label : components(root, JLabel.class)) {
            texts.add(label.getText());
        }
        return texts;
    }

    private static <T> List<T> components(Container root, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        collect(root, type, matches);
        return matches;
    }

    private static <T> void collect(Component component, Class<T> type, List<T> matches) {
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, type, matches);
            }
        }
    }
}

package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardWorkbenchPanelTest {
    @Test
    void dashboardSeparatesModuleGridFromInsightRail() {
        List<ModuleDescriptor> modules = Arrays.asList(
                new ModuleDescriptor("用户管理", "维护账号和角色。", "可用：已接入"),
                new ModuleDescriptor("学籍查询", "查看学生档案。", "可用：已接入"));

        DashboardWorkbenchPanel panel = new DashboardWorkbenchPanel(
                "管理员", "ADMIN", modules, module -> new JButton(module.getTitle()));

        assertTrue(panel.getLayout() instanceof BorderLayout);
        JScrollPane scroller = (JScrollPane) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        assertTrue(scroller.getViewport().getView() instanceof ScrollablePagePanel);
        assertTrue(scroller.getViewport().getView() instanceof Scrollable);
        assertTrue(labels(panel).contains("系统状态"));
        assertTrue(labels(panel).contains("快捷操作"));
    }

    @Test
    void dashboardRailStacksBelowModulesWhenCompact() {
        DashboardWorkbenchPanel panel = dashboard();
        Component workspace = workspace(panel);

        workspace.setBounds(0, 0, UiMetrics.px(620), UiMetrics.px(700));
        workspace.doLayout();

        Component grid = ((Container) workspace).getComponent(0);
        Component rail = ((Container) workspace).getComponent(1);
        assertEquals(0, grid.getX());
        assertEquals(0, rail.getX());
        assertTrue(rail.getY() > grid.getY(), "insight rail should move below module grid on compact windows");
    }

    @Test
    void dashboardRailUsesSideColumnWhenWide() {
        DashboardWorkbenchPanel panel = dashboard();
        Component workspace = workspace(panel);

        workspace.setBounds(0, 0, UiMetrics.px(920), UiMetrics.px(700));
        workspace.doLayout();

        Component grid = ((Container) workspace).getComponent(0);
        Component rail = ((Container) workspace).getComponent(1);
        assertTrue(rail.getX() > grid.getX(), "insight rail should be a side column when width allows");
        assertTrue(grid.getWidth() >= UiMetrics.px(300));
    }

    @Test
    void dashboardStatsStayReadableOnUltrawideWindows() {
        DashboardWorkbenchPanel panel = dashboard();
        Component stats = statRow(panel);

        stats.setBounds(0, 0, UiMetrics.px(2000), UiMetrics.px(140));
        stats.doLayout();

        for (Component card : ((Container) stats).getComponents()) {
            assertTrue(card.getWidth() <= UiMetrics.px(360));
        }
        assertTrue(((Container) stats).getComponent(0).getX() > 0);
    }

    @Test
    void dashboardStatsReserveEnoughHeightForEveryTextLine() {
        DashboardWorkbenchPanel panel = new DashboardWorkbenchPanel(
                "演示系统管理员", "ADMIN", Arrays.asList(
                        new ModuleDescriptor("用户管理", "维护账号和角色。", "可用：已接入")),
                module -> new JButton(module.getTitle()));
        Container stats = (Container) statRow(panel);

        for (Component component : stats.getComponents()) {
            Container card = (Container) component;
            BorderLayout layout = (BorderLayout) card.getLayout();
            Insets insets = card.getInsets();
            int contentHeight = layout.getLayoutComponent(BorderLayout.NORTH).getPreferredSize().height
                    + layout.getLayoutComponent(BorderLayout.CENTER).getPreferredSize().height
                    + layout.getLayoutComponent(BorderLayout.SOUTH).getPreferredSize().height
                    + layout.getVgap() * 2;
            assertTrue(card.getPreferredSize().height >= contentHeight + insets.top + insets.bottom,
                    "stat card must not clip or overlap its three text lines");
        }
    }

    @Test
    void dashboardStatusPillsFitInsideInsightRail() {
        DashboardWorkbenchPanel panel = dashboard();

        for (JLabel label : labelComponents(panel)) {
            if (label.isOpaque()) {
                assertTrue(label.getPreferredSize().width <= UiMetrics.px(220),
                        "status pill must fit inside the insight rail: " + label.getText());
            }
        }
    }

    @Test
    void quickActionIsAnEnterableModuleButton() {
        AtomicReference<String> opened = new AtomicReference<String>();
        List<ModuleDescriptor> modules = Arrays.asList(
                new ModuleDescriptor("用户管理", "维护账号和角色。", "可用：已接入"));
        DashboardWorkbenchPanel panel = new DashboardWorkbenchPanel(
                "管理员", "ADMIN", modules, module -> new JLabel(module.getTitle()),
                module -> opened.set(module.getTitle()));

        JButton quickAction = findButton(panel, "用户管理");

        assertTrue(quickAction != null, "quick action should be a button for the module");
        assertEquals(1, quickAction.getActionListeners().length);
        quickAction.doClick();
        assertEquals("用户管理", opened.get());
    }

    private static DashboardWorkbenchPanel dashboard() {
        return new DashboardWorkbenchPanel("管理员", "ADMIN", Arrays.asList(
                new ModuleDescriptor("用户管理", "维护账号和角色。", "可用：已接入"),
                new ModuleDescriptor("学籍查询", "查看学生档案。", "可用：已接入")),
                module -> new JButton(module.getTitle()));
    }

    private static Component workspace(DashboardWorkbenchPanel panel) {
        JScrollPane scroller = (JScrollPane) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        JViewport viewport = scroller.getViewport();
        JPanel page = (JPanel) viewport.getView();
        return ((BorderLayout) page.getLayout()).getLayoutComponent(BorderLayout.CENTER);
    }

    private static Component statRow(DashboardWorkbenchPanel panel) {
        JScrollPane scroller = (JScrollPane) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        JPanel page = (JPanel) scroller.getViewport().getView();
        return ((BorderLayout) page.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    }

    private static java.util.List<String> labels(Component root) {
        java.util.List<String> values = new java.util.ArrayList<String>();
        collect(root, values);
        return values;
    }

    private static java.util.List<JLabel> labelComponents(Component root) {
        java.util.List<JLabel> values = new java.util.ArrayList<JLabel>();
        collectLabels(root, values);
        return values;
    }

    private static void collectLabels(Component component, java.util.List<JLabel> values) {
        if (component instanceof JLabel) {
            values.add((JLabel) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectLabels(child, values);
            }
        }
    }

    private static void collect(Component component, java.util.List<String> values) {
        if (component instanceof JLabel) {
            values.add(((JLabel) component).getText());
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                collect(child, values);
            }
        }
    }

    private static JButton findButton(Component component, String text) {
        if (component instanceof JButton && text.equals(((JButton) component).getText())) {
            return (JButton) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}

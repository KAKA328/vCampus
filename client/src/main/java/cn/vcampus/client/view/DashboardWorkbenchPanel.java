package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Role-aware home dashboard with module shortcuts and an operational insight rail. */
final class DashboardWorkbenchPanel extends JPanel {
    interface ModuleComponentFactory {
        Component create(ModuleDescriptor module);
    }

    interface ModuleAction {
        void open(ModuleDescriptor module);
    }

    DashboardWorkbenchPanel(String displayName, String role, List<ModuleDescriptor> modules,
            ModuleComponentFactory moduleFactory) {
        this(displayName, role, modules, moduleFactory, module -> { });
    }

    DashboardWorkbenchPanel(String displayName, String role, List<ModuleDescriptor> modules,
            ModuleComponentFactory moduleFactory, ModuleAction moduleAction) {
        super(new BorderLayout());
        setOpaque(false);
        ScrollablePagePanel page = new ScrollablePagePanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        page.add(statRow(displayName, role, modules), BorderLayout.NORTH);
        page.add(workspace(modules, moduleFactory, moduleAction), BorderLayout.CENTER);
        add(VCampusTheme.pageScroll(page), BorderLayout.CENTER);
    }

    private JPanel statRow(String displayName, String role, List<ModuleDescriptor> modules) {
        ResponsiveCardRowPanel stats = new ResponsiveCardRowPanel(220, 14);
        stats.add(statCard("当前账号", displayName, "角色 " + role, VCampusTheme.PRIMARY));
        stats.add(statCard("可用模块", String.valueOf(modules.size()), "按当前权限显示", VCampusTheme.ACCENT));
        stats.add(statCard("数据状态", "Access", "服务端持久化接入", VCampusTheme.SUCCESS));
        return stats;
    }

    private JPanel statCard(String titleText, String valueText, String noteText, java.awt.Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(220, 112));
        card.setMinimumSize(new Dimension(200, 106));
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(3, 0, 0, 0, accent),
                VCampusTheme.padding(16, 18, 16, 18)));

        JLabel title = new JLabel(titleText);
        title.setForeground(VCampusTheme.MUTED);
        JLabel value = new JLabel(valueText);
        value.setFont(VCampusTheme.font(Font.BOLD, 22));
        value.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel note = new JLabel(noteText);
        note.setForeground(VCampusTheme.MUTED);
        card.add(title, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(note, BorderLayout.SOUTH);
        return card;
    }

    private JPanel workspace(List<ModuleDescriptor> modules, ModuleComponentFactory moduleFactory,
            ModuleAction moduleAction) {
        ResponsiveModuleGridPanel grid = new ResponsiveModuleGridPanel();
        for (ModuleDescriptor module : modules) {
            grid.add(moduleFactory.create(module));
        }
        return new DashboardWorkspacePanel(grid, insightRail(modules, moduleAction));
    }

    private JPanel insightRail(List<ModuleDescriptor> modules, ModuleAction moduleAction) {
        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setPreferredSize(new Dimension(260, 0));
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.add(infoCard("系统状态", new String[] {
                "Access 数据库已接入",
                "用户、学籍、选课、图书馆、商店分模块运行",
                "操作结果通过服务端返回"
        }));
        rail.add(Box.createVerticalStrut(14));
        rail.add(quickActionCard(modules, moduleAction));
        return rail;
    }

    private JPanel quickActionCard(List<ModuleDescriptor> modules, ModuleAction moduleAction) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel title = new JLabel("快捷操作");
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        int count = Math.min(3, modules.size());
        if (count == 0) {
            rows.add(chipRow("暂无可用模块"));
        } else {
            for (int index = 0; index < count; index++) {
                final ModuleDescriptor module = modules.get(index);
                JButton action = new JButton(module.getTitle());
                VCampusTheme.secondaryButton(action);
                action.setAlignmentX(Component.LEFT_ALIGNMENT);
                action.addActionListener(event -> moduleAction.open(module));
                rows.add(action);
                rows.add(Box.createVerticalStrut(8));
            }
        }
        card.add(title, BorderLayout.NORTH);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JPanel infoCard(String titleText, String[] items) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel title = new JLabel(titleText);
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (String item : items) {
            rows.add(chipRow(item));
            rows.add(Box.createVerticalStrut(8));
        }

        card.add(title, BorderLayout.NORTH);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JPanel chipRow(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        JLabel chip = new JLabel(text);
        VCampusTheme.statusPill(chip, VCampusTheme.PRIMARY);
        row.add(chip);
        return row;
    }

    private static final class DashboardWorkspacePanel extends JPanel {
        private static final int GAP = 18;
        private static final int RAIL_WIDTH = 260;
        private static final int STACK_BREAKPOINT = 760;

        private final ResponsiveModuleGridPanel grid;
        private final JPanel rail;

        DashboardWorkspacePanel(ResponsiveModuleGridPanel grid, JPanel rail) {
            super(null);
            this.grid = grid;
            this.rail = rail;
            setOpaque(false);
            add(grid);
            add(rail);
        }

        @Override public void doLayout() {
            Insets insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom);
            if (stacks(width)) {
                Dimension gridSize = grid.preferredSizeForWidth(width);
                Dimension railSize = rail.getPreferredSize();
                grid.setBounds(insets.left, insets.top, width, gridSize.height);
                rail.setBounds(insets.left, insets.top + gridSize.height + GAP,
                        width, Math.max(railSize.height, 180));
                return;
            }

            int railWidth = Math.min(RAIL_WIDTH, width / 3);
            int gridWidth = Math.max(0, width - railWidth - GAP);
            Dimension gridSize = grid.preferredSizeForWidth(gridWidth);
            Dimension railSize = rail.getPreferredSize();
            int layoutHeight = Math.max(height, Math.max(gridSize.height, railSize.height));
            grid.setBounds(insets.left, insets.top, gridWidth, layoutHeight);
            rail.setBounds(insets.left + gridWidth + GAP, insets.top, railWidth, layoutHeight);
        }

        @Override public Dimension getPreferredSize() {
            int width = currentWidth();
            Insets insets = getInsets();
            int contentWidth = Math.max(0, width - insets.left - insets.right);
            Dimension gridSize;
            Dimension railSize = rail.getPreferredSize();
            int height;
            if (stacks(contentWidth)) {
                gridSize = grid.preferredSizeForWidth(contentWidth);
                height = gridSize.height + GAP + Math.max(railSize.height, 180);
            } else {
                int railWidth = Math.min(RAIL_WIDTH, contentWidth / 3);
                int gridWidth = Math.max(0, contentWidth - railWidth - GAP);
                gridSize = grid.preferredSizeForWidth(gridWidth);
                height = Math.max(gridSize.height, railSize.height);
            }
            return new Dimension(width, height + insets.top + insets.bottom);
        }

        private int currentWidth() {
            if (getWidth() > 0) {
                return getWidth();
            }
            if (getParent() != null && getParent().getWidth() > 0) {
                return getParent().getWidth();
            }
            return STACK_BREAKPOINT;
        }

        private static boolean stacks(int width) {
            return width < STACK_BREAKPOINT;
        }
    }
}

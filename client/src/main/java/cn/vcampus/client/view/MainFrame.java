package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Role;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Main dashboard shell after login. */
public final class MainFrame extends JFrame {
    private static final String HOME = "首页";

    private final String host;
    private final int port;
    private final Session session;
    private final ModuleNavigationModel navigationModel = new ModuleNavigationModel();
    private final JPanel content = new JPanel(new BorderLayout());
    private final Map<String, JButton> navButtons = new LinkedHashMap<String, JButton>();

    public MainFrame(String host, int port, Session session) {
        super("vCampus 主界面");
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        VCampusTheme.install();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(980, 620));
        setSize(980, 620);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(VCampusTheme.BACKGROUND);
        root.add(header(), BorderLayout.NORTH);
        root.add(nav(), BorderLayout.WEST);
        root.add(contentPanel(), BorderLayout.CENTER);
        setContentPane(root);
        showHome();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(VCampusTheme.PRIMARY_DARK);
        panel.setBorder(VCampusTheme.padding(16, 24, 16, 24));

        JLabel title = new JLabel("vCampus 虚拟校园综合管理系统");
        title.setForeground(Color.WHITE);
        title.setFont(VCampusTheme.font(Font.BOLD, 18));

        JLabel user = new JLabel(session.getUser().getDisplayName() + "  /  " + session.getUser().getRole());
        user.setForeground(new Color(223, 236, 248));
        user.setHorizontalAlignment(SwingConstants.RIGHT);

        JButton logout = new JButton("退出登录");
        VCampusTheme.secondaryButton(logout);
        logout.addActionListener(e -> logout());

        JPanel right = new JPanel(new BorderLayout(16, 0));
        right.setOpaque(false);
        right.add(user, BorderLayout.CENTER);
        right.add(logout, BorderLayout.EAST);

        panel.add(title, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel nav() {
        JPanel outer = new JPanel(new BorderLayout(0, 14));
        outer.setBackground(VCampusTheme.SIDEBAR);
        outer.setBorder(VCampusTheme.padding(20, 16, 20, 16));
        outer.setPreferredSize(new Dimension(190, 0));

        JLabel title = new JLabel("功能导航");
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        outer.add(title, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 10));
        buttons.setOpaque(false);
        addNavButton(buttons, HOME, e -> showHome());
        for (ModuleDescriptor module : navigationModel.visibleModuleCards(session.getUser().getRole())) {
            addNavButton(buttons, module.getTitle(), e -> showModule(module));
        }
        outer.add(buttons, BorderLayout.CENTER);
        return outer;
    }

    private void addNavButton(JPanel panel, String title, java.awt.event.ActionListener listener) {
        JButton button = new JButton(title);
        VCampusTheme.navButton(button, false);
        button.addActionListener(listener);
        navButtons.put(title, button);
        panel.add(button);
    }

    private JPanel contentPanel() {
        content.setOpaque(false);
        content.setBorder(VCampusTheme.padding(24, 24, 24, 24));
        return content;
    }

    private void showHome() {
        selectNav(HOME);
        content.removeAll();
        content.add(dashboard(), BorderLayout.CENTER);
        refreshContent();
    }

    private JPanel dashboard() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setOpaque(false);
        panel.add(sectionTitle("工作台", "当前角色可访问的业务入口如下，后续成员模块会接入对应页面。"), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 16));
        grid.setOpaque(false);
        List<ModuleDescriptor> modules = navigationModel.visibleModuleCards(session.getUser().getRole());
        for (ModuleDescriptor module : modules) {
            grid.add(new ModuleCardPanel(module, e -> showModule(module)));
        }
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private void showModule(ModuleDescriptor module) {
        selectNav(module.getTitle());
        content.removeAll();
        if (useStudentCourseSelectionPanel(session.getUser().getRole(), module)) {
            content.add(new CourseSelectionPanel(host, port, session), BorderLayout.CENTER);
            refreshContent();
            return;
        }
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setOpaque(false);
        panel.add(sectionTitle(module.getTitle(), module.getSummary()), BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        JLabel status = new JLabel(module.getStatus());
        status.setFont(VCampusTheme.font(Font.BOLD, 15));
        status.setForeground(module.getStatus().contains("可用") ? VCampusTheme.SUCCESS : VCampusTheme.MUTED);
        JLabel placeholder = new JLabel("<html><div style='line-height:1.8;'>"
                + "这里是模块页面预留区域。后续对应成员只需要提供 JPanel，"
                + "即可替换当前占位内容并接入主界面。<br/>"
                + "当前登录用户：" + escapeHtml(session.getUser().getDisplayName())
                + "，角色：" + session.getUser().getRole()
                + "</div></html>");
        placeholder.setForeground(VCampusTheme.TEXT);
        card.add(status, BorderLayout.NORTH);
        card.add(placeholder, BorderLayout.CENTER);
        panel.add(card, BorderLayout.CENTER);
        content.add(panel, BorderLayout.CENTER);
        refreshContent();
    }

    private JPanel sectionTitle(String titleText, String subtitleText) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel(titleText);
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel(subtitleText);
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private void selectNav(String activeTitle) {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            VCampusTheme.navButton(entry.getValue(), entry.getKey().equals(activeTitle));
        }
    }

    private void refreshContent() {
        content.revalidate();
        content.repaint();
    }

    static boolean useStudentCourseSelectionPanel(Role role, ModuleDescriptor module) {
        return role == Role.STUDENT && module != null && "选课系统".equals(module.getTitle());
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void logout() {
        try (RemoteUserService service = new RemoteUserService(host, port)) {
            service.logout(session.getToken());
        } catch (IOException | ClassNotFoundException ignored) {
            // Closing the local window is still allowed if the connection is already gone.
        }
        dispose();
        new LoginFrame(host, port).setVisible(true);
    }
}

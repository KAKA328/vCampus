package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Main dashboard shell after login. */
public final class MainFrame extends JFrame {
    private final String host;
    private final int port;
    private final Session session;
    private final JLabel content = new JLabel("", SwingConstants.CENTER);

    public MainFrame(String host, int port, Session session) {
        super("vCampus 主界面");
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(880, 560);
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(VCampusTheme.BACKGROUND);
        root.add(header(), BorderLayout.NORTH);
        root.add(nav(), BorderLayout.WEST);
        root.add(contentPanel(), BorderLayout.CENTER);
        setContentPane(root);
        showModule("首页");
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(VCampusTheme.PRIMARY);
        panel.setBorder(VCampusTheme.padding(12, 18, 12, 18));
        JLabel label = new JLabel(session.getUser().getDisplayName() + " / " + session.getUser().getRole());
        label.setForeground(Color.WHITE);
        label.setFont(VCampusTheme.font(java.awt.Font.BOLD, 15));
        JButton logout = new JButton("退出");
        logout.addActionListener(e -> logout());
        panel.add(label, BorderLayout.WEST);
        panel.add(logout, BorderLayout.EAST);
        return panel;
    }

    private JPanel nav() {
        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
        panel.setBackground(new Color(232, 238, 244));
        panel.setBorder(VCampusTheme.padding(18, 14, 18, 14));
        panel.setPreferredSize(new Dimension(170, 0));
        for (String module : new ModuleNavigationModel().visibleModules(session.getUser().getRole())) {
            JButton button = new JButton(module);
            button.addActionListener(e -> showModule(module));
            panel.add(button);
        }
        return panel;
    }

    private JPanel contentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);
        content.setForeground(VCampusTheme.TEXT);
        content.setFont(VCampusTheme.font(java.awt.Font.PLAIN, 18));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private void showModule(String module) {
        content.setText("<html><h2>" + module + "</h2><p>模块页面待对应成员接入 JPanel。</p></html>");
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

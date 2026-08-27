package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Login window for the Swing client. */
public final class LoginFrame extends JFrame {
    private final String host;
    private final int port;
    private final JTextField userId = new PromptTextField(20, CredentialInputGuidance.USER_ID_HINT);
    private final PromptPasswordField password = new PromptPasswordField(20, CredentialInputGuidance.PASSWORD_HINT);
    private final JLabel status = new JLabel("请输入账号和密码");

    public LoginFrame(String host, int port) {
        super("vCampus 登录");
        this.host = host;
        this.port = port;
        build();
    }

    private void build() {
        VCampusTheme.install();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 460));
        setSize(760, 460);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(VCampusTheme.BACKGROUND);
        root.setBorder(VCampusTheme.padding(28, 32, 28, 32));
        root.add(brandPanel(), BorderLayout.WEST);
        root.add(formPanel(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel brandPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setPreferredSize(new Dimension(270, 0));
        panel.setBackground(VCampusTheme.PRIMARY_DARK);
        panel.setBorder(VCampusTheme.padding(36, 26, 32, 26));

        JLabel title = new JLabel("<html><div style='color:white;font-size:24px;font-weight:bold;'>vCampus<br/>虚拟校园</div></html>");
        JLabel summary = new JLabel("<html><div style='color:#EAF3F8;line-height:1.7;'>校园业务一站式办理<br/>学习、借阅、购物与个人信息管理</div></html>");
        JLabel foot = new JLabel("<html><div style='color:#BFD7EE;'>欢迎使用校园综合服务平台</div></html>");
        panel.add(title, BorderLayout.NORTH);
        panel.add(summary, BorderLayout.CENTER);
        panel.add(foot, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel formPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(VCampusTheme.padding(0, 28, 0, 0));

        JPanel card = new JPanel(new GridBagLayout());
        VCampusTheme.panel(card);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.BORDER),
                VCampusTheme.padding(28, 34, 28, 34)));

        JLabel title = new JLabel("账号登录");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        GridBagConstraints c = base(0, 0);
        c.gridwidth = 2;
        card.add(title, c);

        JLabel hint = new JLabel("请输入账号密码进入系统");
        hint.setForeground(VCampusTheme.MUTED);
        c = base(0, 1);
        c.gridwidth = 2;
        c.insets = new Insets(0, 8, 18, 8);
        card.add(hint, c);

        VCampusTheme.field(userId);
        VCampusTheme.field(password);
        password.addActionListener(e -> login());
        addField(card, "账号", userId, 2);
        addField(card, "密码", password, 3);

        JButton login = new JButton("登录系统");
        VCampusTheme.primaryButton(login);
        login.addActionListener(e -> login());
        getRootPane().setDefaultButton(login);

        c = base(0, 4);
        c.gridwidth = 2;
        card.add(buttons(login), c);

        c = base(0, 5);
        c.gridwidth = 2;
        status.setForeground(VCampusTheme.MUTED);
        status.setBorder(VCampusTheme.padding(8, 0, 0, 0));
        card.add(status, c);

        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private void login() {
        char[] secret = password.getPassword();
        try (RemoteUserService service = new RemoteUserService(host, port)) {
            Message response = service.login(new UserCredentials(userId.getText().trim(), new String(secret), "Login User", "STUDENT"));
            if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof Session)) {
                showStatus("登录失败：" + response.getStatusCode(), VCampusTheme.DANGER);
                return;
            }
            showStatus("登录成功，正在进入系统…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(() -> openMain((Session) response.getPayload()));
        } catch (RuntimeException | IOException | ClassNotFoundException failure) {
            showStatus("无法连接服务器，或账号/密码格式不正确", VCampusTheme.DANGER);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private void openMain(Session session) {
        dispose();
        new MainFrame(host, port, session).setVisible(true);
    }

    private static void addField(JPanel panel, String label, java.awt.Component field, int row) {
        GridBagConstraints c = base(0, row);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(VCampusTheme.TEXT);
        panel.add(labelComponent, c);
        c = base(1, row);
        c.weightx = 1;
        panel.add(field, c);
    }

    private static GridBagConstraints base(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static JPanel buttons(JButton login) {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        panel.setOpaque(false);
        panel.add(login);
        return panel;
    }
}

package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserCredentials;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Login window for the Swing client. */
public final class LoginFrame extends JFrame {
    private final String host;
    private final int port;
    private final JTextField userId = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
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
        setSize(460, 330);
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(VCampusTheme.BACKGROUND);
        root.setBorder(VCampusTheme.padding(26, 36, 26, 36));
        root.add(titlePanel(), BorderLayout.NORTH);
        root.add(formPanel(), BorderLayout.CENTER);
        setContentPane(root);
    }

    private JPanel titlePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel title = new JLabel("vCampus 虚拟校园");
        title.setFont(VCampusTheme.font(java.awt.Font.BOLD, 22));
        title.setForeground(VCampusTheme.PRIMARY);
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JLabel("Java C/S 客户端"), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        VCampusTheme.panel(panel);
        addField(panel, "账号", userId, 0);
        addField(panel, "密码", password, 1);
        JButton login = new JButton("登录");
        JButton register = new JButton("注册");
        login.addActionListener(e -> login());
        register.addActionListener(e -> new RegisterDialog(this, host, port).setVisible(true));
        GridBagConstraints c = base(0, 2);
        c.gridwidth = 2;
        panel.add(buttons(login, register), c);
        c = base(0, 3);
        c.gridwidth = 2;
        status.setForeground(VCampusTheme.TEXT);
        panel.add(status, c);
        return panel;
    }

    private void login() {
        try (RemoteUserService service = new RemoteUserService(host, port)) {
            Message response = service.login(new UserCredentials(userId.getText(), passwordText(), "Login User", "STUDENT"));
            if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof Session)) {
                status.setText("登录失败：" + response.getStatusCode());
                return;
            }
            SwingUtilities.invokeLater(() -> openMain((Session) response.getPayload()));
        } catch (RuntimeException | IOException | ClassNotFoundException failure) {
            status.setText("无法连接服务器或输入不合法");
        }
    }

    private void openMain(Session session) {
        dispose();
        new MainFrame(host, port, session).setVisible(true);
    }

    private static void addField(JPanel panel, String label, java.awt.Component field, int row) {
        GridBagConstraints c = base(0, row);
        panel.add(new JLabel(label), c);
        c = base(1, row);
        panel.add(field, c);
    }

    private static GridBagConstraints base(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static JPanel buttons(JButton login, JButton register) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.add(login);
        panel.add(register);
        return panel;
    }

    private String passwordText() {
        return new String(password.getPassword());
    }
}

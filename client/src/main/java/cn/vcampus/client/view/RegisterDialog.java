package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.UserCredentials;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** Registration dialog for demo accounts. */
public final class RegisterDialog extends JDialog {
    private final String host;
    private final int port;
    private final JTextField userId = new JTextField(16);
    private final JTextField displayName = new JTextField(16);
    private final JPasswordField password = new JPasswordField(16);
    private final JComboBox<Role> role = new JComboBox<Role>(Role.values());

    RegisterDialog(java.awt.Frame owner, String host, int port) {
        super(owner, "注册用户", true);
        this.host = host;
        this.port = port;
        build();
    }

    private void build() {
        setSize(380, 300);
        setLocationRelativeTo(getOwner());
        JPanel form = new JPanel(new GridBagLayout());
        VCampusTheme.panel(form);
        add(form, "账号", userId, 0);
        add(form, "姓名", displayName, 1);
        add(form, "密码", password, 2);
        add(form, "角色", role, 3);
        JPanel buttons = new JPanel();
        JButton submit = new JButton("注册");
        JButton cancel = new JButton("取消");
        submit.addActionListener(e -> submit());
        cancel.addActionListener(e -> dispose());
        buttons.add(submit);
        buttons.add(cancel);
        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void submit() {
        Role selectedRole = (Role) role.getSelectedItem();
        try (RemoteUserService service = new RemoteUserService(host, port)) {
            UserCredentials credentials = new UserCredentials(
                    userId.getText(), new String(password.getPassword()), displayName.getText(), selectedRole.name());
            Message response = service.register(credentials);
            if (response.getStatusCode() == StatusCode.OK) {
                JOptionPane.showMessageDialog(this, "注册成功，请返回登录");
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "注册失败：" + response.getStatusCode());
            }
        } catch (RuntimeException | IOException | ClassNotFoundException failure) {
            JOptionPane.showMessageDialog(this, "注册失败，请检查输入和服务器连接");
        }
    }

    private static void add(JPanel panel, String label, java.awt.Component field, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.insets = new Insets(7, 7, 7, 7);
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }
}

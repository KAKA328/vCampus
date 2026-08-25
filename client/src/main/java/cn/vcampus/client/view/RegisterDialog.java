package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Registration dialog for demo accounts. */
public final class RegisterDialog extends JDialog {
    private final String host;
    private final int port;
    private final JTextField userId = new PromptTextField(18, CredentialInputGuidance.USER_ID_HINT);
    private final JTextField displayName = new PromptTextField(18, CredentialInputGuidance.DISPLAY_NAME_HINT);
    private final PromptPasswordField password = new PromptPasswordField(18, CredentialInputGuidance.PASSWORD_HINT);
    private final JComboBox<Role> role = new JComboBox<Role>(Role.values());
    private final JLabel status = new JLabel("请按输入框提示填写，带提示文字的空框不会作为内容提交");

    RegisterDialog(java.awt.Frame owner, String host, int port) {
        super(owner, "注册用户", true);
        this.host = host;
        this.port = port;
        build();
    }

    private void build() {
        setMinimumSize(new Dimension(540, 470));
        setSize(540, 470);
        setResizable(false);
        setLocationRelativeTo(getOwner());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(VCampusTheme.BACKGROUND);
        root.setBorder(VCampusTheme.padding(22, 26, 22, 26));
        root.add(titlePanel(), BorderLayout.NORTH);
        root.add(formPanel(), BorderLayout.CENTER);
        root.add(buttonPanel(), BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel titlePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel title = new JLabel("创建 vCampus 用户");
        title.setFont(VCampusTheme.font(Font.BOLD, 21));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("请填写账号信息，并选择对应用户角色。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel formPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        VCampusTheme.panel(form);
        role.setSelectedItem(Role.STUDENT);
        VCampusTheme.field(userId);
        VCampusTheme.field(displayName);
        VCampusTheme.field(password);
        VCampusTheme.field(role);
        add(form, "账号", userId, 0);
        add(form, "姓名", displayName, 1);
        add(form, "密码", password, 2);
        add(form, "角色", role, 3);
        GridBagConstraints c = base(0, 4);
        c.gridwidth = 2;
        status.setForeground(VCampusTheme.MUTED);
        form.add(status, c);
        return form;
    }

    private JPanel buttonPanel() {
        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0));
        buttons.setOpaque(false);
        JButton submit = new JButton("提交注册");
        JButton cancel = new JButton("取消");
        VCampusTheme.primaryButton(submit);
        VCampusTheme.secondaryButton(cancel);
        submit.addActionListener(e -> submit());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(submit);
        buttons.add(cancel);
        buttons.add(submit);
        return buttons;
    }

    private void submit() {
        Role selectedRole = (Role) role.getSelectedItem();
        char[] secret = password.getPassword();
        try (RemoteUserService service = new RemoteUserService(host, port)) {
            UserCredentials credentials = new UserCredentials(
                    userId.getText().trim(), new String(secret), displayName.getText().trim(), selectedRole.name());
            Message response = service.register(credentials);
            if (response.getStatusCode() == StatusCode.OK) {
                JOptionPane.showMessageDialog(this, "注册成功，请返回登录");
                dispose();
            } else {
                showStatus("注册失败：" + response.getStatusCode(), VCampusTheme.DANGER);
            }
        } catch (IllegalArgumentException invalidInput) {
            showStatus(invalidInput.getMessage(), VCampusTheme.DANGER);
        } catch (IOException | ClassNotFoundException failure) {
            showStatus("注册失败，请检查输入和服务器连接", VCampusTheme.DANGER);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static void add(JPanel panel, String label, java.awt.Component field, int row) {
        GridBagConstraints c = base(0, row);
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
}

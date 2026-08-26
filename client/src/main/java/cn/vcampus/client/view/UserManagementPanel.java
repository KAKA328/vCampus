package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Admin account management actions shown inside the user-management module. */
final class UserManagementPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JTextField targetUserId = new PromptTextField(18, CredentialInputGuidance.USER_ID_HINT);
    private final JLabel status = new JLabel(" ");

    UserManagementPanel(String host, int port, Session session) {
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        JLabel note = new JLabel("<html><div style='line-height:1.8;'>"
                + "当前页面用于用户账号维护。普通账号可在右上角注销自己，管理员可在此注销指定账号。"
                + "</div></html>");
        note.setForeground(VCampusTheme.TEXT);
        add(note, BorderLayout.NORTH);

        if (session.getUser().getRole() == Role.ADMIN) {
            add(adminActions(), BorderLayout.CENTER);
        } else {
            JLabel limited = new JLabel("当前角色没有管理员账号维护权限。");
            limited.setForeground(VCampusTheme.MUTED);
            add(limited, BorderLayout.CENTER);
        }

        status.setForeground(VCampusTheme.MUTED);
        add(status, BorderLayout.SOUTH);
    }

    private JPanel adminActions() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 0, 6, 12);
        c.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("管理员账号注销");
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 3;
        panel.add(title, c);

        JLabel label = new JLabel("账号");
        label.setForeground(VCampusTheme.TEXT);
        c.gridy = 1;
        c.gridwidth = 1;
        panel.add(label, c);

        VCampusTheme.field(targetUserId);
        targetUserId.addActionListener(e -> unregisterTarget());
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(targetUserId, c);

        JButton unregister = new JButton(UserManagementActions.ADMIN_UNREGISTER);
        VCampusTheme.dangerButton(unregister);
        unregister.addActionListener(e -> unregisterTarget());
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(unregister, c);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(panel);
        return wrapper;
    }

    private void unregisterTarget() {
        String userId = targetUserId.getText().trim();
        if (userId.isEmpty()) {
            showStatus("请输入要注销的账号。", VCampusTheme.DANGER);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "确认注销账号“" + userId + "”？注销后该账号将不可再登录。",
                UserManagementActions.ADMIN_UNREGISTER,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }

        try (RemoteUserService service = new RemoteUserService(host, port)) {
            Message response = service.unregister(userId, session.getToken());
            if (response.getStatusCode() == StatusCode.OK) {
                targetUserId.setText("");
                showStatus("账号“" + userId + "”已注销。", VCampusTheme.SUCCESS);
            } else {
                showStatus("注销失败：" + response.getStatusCode(), VCampusTheme.DANGER);
            }
        } catch (IllegalArgumentException e) {
            showStatus("账号格式不正确。", VCampusTheme.DANGER);
        } catch (IOException | ClassNotFoundException e) {
            showStatus("无法连接服务器，请确认服务器已启动。", VCampusTheme.DANGER);
        }
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }
}

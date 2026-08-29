package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;
import java.util.List;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** Administrator user-management page with single-account and batch-import actions. */
final class UserManagementPanel extends JPanel {
    private final Frame owner;
    private final String host;
    private final int port;
    private final Session session;
    private final UserImportTableModel importTableModel = new UserImportTableModel();
    private final JTable importTable = new JTable(importTableModel);
    private final JLabel status = new JLabel("可直接在表格中填写多行账号，再一次性导入。");
    private final JButton createButton = new JButton("创建单个账号");
    private final JButton addRowButton = new JButton("添加一行");
    private final JButton removeRowButton = new JButton("删除选中行");
    private final JButton importButton = new JButton("导入账号");
    private boolean requestInProgress;

    UserManagementPanel(Frame owner, String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session are required");
        }
        this.owner = owner;
        this.host = host.trim();
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);

        createButton.addActionListener(event -> new RegisterDialog(owner, host, port, session).setVisible(true));
        addRowButton.addActionListener(event -> importTableModel.addBlankRow());
        removeRowButton.addActionListener(event ->
                importTableModel.removeSelectedRows(importTable.getSelectedRows(), importTable));
        importButton.addActionListener(event -> importUsers());
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("用户管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("创建账号、批量发放初始密码，并留下导入人和审计记录。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel body() {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setOpaque(false);
        panel.add(singleAccountCard(), BorderLayout.WEST);
        panel.add(importCard(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel singleAccountCard() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(300, 0));
        card.setMinimumSize(new Dimension(260, 220));

        JLabel title = new JLabel("单个开户注册");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JTextArea summary = textBlock("适合临时补一个账号。学生账号创建后仍需到学籍管理维护档案；"
                + "教师账号创建后需维护教师档案。");
        VCampusTheme.primaryButton(createButton);

        card.add(title, BorderLayout.NORTH);
        card.add(summary, BorderLayout.CENTER);
        card.add(createButton, BorderLayout.SOUTH);
        return card;
    }

    private JPanel importCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setMinimumSize(new Dimension(420, 260));

        JPanel titlePanel = new JPanel(new BorderLayout(0, 4));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("批量导入账号");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel hint = new JLabel("账号支持字母、数字、下划线；密码 6-16 位；角色可在单元格中选择。");
        hint.setForeground(VCampusTheme.MUTED);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(hint, BorderLayout.SOUTH);

        configureTable();
        JScrollPane tableScroll = new JScrollPane(importTable);
        tableScroll.setBorder(javax.swing.BorderFactory.createLineBorder(VCampusTheme.BORDER));

        card.add(titlePanel, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(importFooter(), BorderLayout.SOUTH);
        return card;
    }

    private void configureTable() {
        importTable.setRowHeight(30);
        importTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        importTable.getTableHeader().setReorderingAllowed(false);
        importTable.setGridColor(VCampusTheme.BORDER);
        importTable.setShowVerticalLines(false);
        importTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(4).setPreferredWidth(180);

        JComboBox<String> roles = new JComboBox<String>();
        for (Role role : Role.values()) {
            roles.addItem(role.name());
        }
        importTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(roles));
    }

    private JPanel importFooter() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(addRowButton);
        VCampusTheme.secondaryButton(removeRowButton);
        VCampusTheme.primaryButton(importButton);
        actions.add(addRowButton);
        actions.add(removeRowButton);
        actions.add(importButton);

        status.setForeground(VCampusTheme.MUTED);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void importUsers() {
        importTableModel.commitActiveEditor(importTable);
        final List<UserImportRow> rows = importTableModel.toImportRows();
        if (rows.isEmpty()) {
            showStatus("请至少填写一行账号信息", VCampusTheme.DANGER);
            return;
        }
        requestInProgress = true;
        updateButtonState();
        importTableModel.clearResults();
        showStatus("正在导入账号，请稍候…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.importUsers(session.getToken(), rows);
                }
            }

            @Override protected void done() {
                try {
                    handleImportResponse(get());
                } catch (Exception failure) {
                    showStatus("导入失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void handleImportResponse(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showStatus("导入失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
            return;
        }
        if (!(response.getPayload() instanceof UserImportResult)) {
            showStatus("服务器返回的导入结果格式不正确", VCampusTheme.DANGER);
            return;
        }
        UserImportResult result = (UserImportResult) response.getPayload();
        importTableModel.applyResult(result);
        Color color = result.getFailureCount() == 0 ? VCampusTheme.SUCCESS : VCampusTheme.MUTED;
        showStatus("导入完成：成功 " + result.getSuccessCount() + "/" + result.getTotalCount()
                + "，失败 " + result.getFailureCount()
                + "，批次 " + result.getImportBatchId(), color);
    }

    private void updateButtonState() {
        createButton.setEnabled(!requestInProgress);
        addRowButton.setEnabled(!requestInProgress);
        removeRowButton.setEnabled(!requestInProgress);
        importButton.setEnabled(!requestInProgress);
        importTable.setEnabled(!requestInProgress);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static JTextArea textBlock(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(VCampusTheme.TEXT);
        area.setFont(VCampusTheme.font(Font.PLAIN, 14));
        return area;
    }

    private static String statusMessage(StatusCode code) {
        if (code == StatusCode.BAD_REQUEST) {
            return "导入数据格式不正确";
        }
        if (code == StatusCode.UNAUTHORIZED) {
            return "登录状态已失效，请重新登录";
        }
        if (code == StatusCode.FORBIDDEN) {
            return "当前账号没有批量导入权限";
        }
        if (code == StatusCode.CONFLICT) {
            return "存在重复账号";
        }
        return "服务器处理请求失败";
    }
}

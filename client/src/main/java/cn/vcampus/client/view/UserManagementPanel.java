package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteUserService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.user.AuditEvent;
import cn.vcampus.user.PasswordResetApplicationSummary;
import cn.vcampus.user.Session;
import cn.vcampus.user.UserAccountSummary;
import cn.vcampus.user.UserImportResult;
import cn.vcampus.user.UserImportRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Administrator user-management page with single-account and batch-import actions. */
final class UserManagementPanel extends JPanel {
    private final Frame owner;
    private final String host;
    private final int port;
    private final Session session;
    private final UserImportTableModel importTableModel = new UserImportTableModel();
    private final JTable importTable = new JTable(importTableModel);
    private final JLabel selectedFile = new JLabel("尚未选择导入文件");
    private final JLabel status = new JLabel("请选择 Excel 或 CSV 文件，系统会先预览账号，再写入 Access 数据库。");
    private final JButton createButton = new JButton("创建单个账号");
    private final JButton chooseFileButton = new JButton("选择Excel/CSV文件");
    private final JButton importButton = new JButton("导入文件账号");
    private final DefaultTableModel accountTableModel = new DefaultTableModel(
            new Object[]{"账号", "姓名", "角色", "档案编号", "状态", "创建人", "导入批次"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable accountTable = new JTable(accountTableModel);
    private final JLabel accountStatus = new JLabel("刷新后查看已有账号。");
    private final JButton refreshAccountsButton = new JButton("刷新账号列表");
    private final JButton enableAccountButton = new JButton("启用账号");
    private final JButton disableAccountButton = new JButton("停用账号");
    private final JButton unregisterAccountButton = new JButton("注销账号");
    private final DefaultTableModel resetTableModel = new DefaultTableModel(
            new Object[]{"账号", "申请时间", "状态"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable resetTable = new JTable(resetTableModel);
    private final JLabel resetStatus = new JLabel("刷新后查看待审批的密码重置申请。");
    private final JButton refreshResetButton = new JButton("刷新重置申请");
    private final JButton approveResetButton = new JButton("通过重置");
    private final JButton rejectResetButton = new JButton("拒绝重置");
    private final DefaultTableModel auditTableModel = new DefaultTableModel(
            new Object[]{"时间", "操作人", "操作", "目标"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable auditTable = new JTable(auditTableModel);
    private final JLabel auditStatus = new JLabel("刷新后查看账号管理审计记录。");
    private final JButton refreshAuditButton = new JButton("刷新审计记录");
    private final UserImportFileReader fileReader = new UserImportFileReader();
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
        add(scrollingBody(), BorderLayout.CENTER);

        createButton.addActionListener(event -> new RegisterDialog(owner, host, port, session).setVisible(true));
        chooseFileButton.addActionListener(event -> chooseImportFile());
        importButton.addActionListener(event -> importUsers());
        refreshAccountsButton.addActionListener(event -> loadAccounts());
        enableAccountButton.addActionListener(event -> setSelectedAccountActive(true));
        disableAccountButton.addActionListener(event -> setSelectedAccountActive(false));
        unregisterAccountButton.addActionListener(event -> unregisterSelectedAccount());
        refreshResetButton.addActionListener(event -> loadPasswordResetApplications());
        approveResetButton.addActionListener(event -> reviewSelectedPasswordReset(true));
        rejectResetButton.addActionListener(event -> reviewSelectedPasswordReset(false));
        refreshAuditButton.addActionListener(event -> loadAuditEvents());
        updateButtonState();
    }

    private JScrollPane scrollingBody() {
        return VCampusTheme.pageScroll(body());
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("用户管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("创建账号、从文件批量导入账号，并留下导入人和审计记录。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, 0));
        panel.setOpaque(false);

        ResponsiveCardRowPanel accountActions = new ResponsiveCardRowPanel(300, 18);
        accountActions.add(singleAccountCard());
        accountActions.add(importCard());

        ResponsiveCardRowPanel lowerCards = new ResponsiveCardRowPanel(300, 18);
        lowerCards.add(passwordResetCard());
        lowerCards.add(auditCard());

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(accountActions);
        stack.add(Box.createVerticalStrut(18));
        stack.add(accountListCard());
        stack.add(Box.createVerticalStrut(18));
        stack.add(lowerCards);

        panel.add(stack, BorderLayout.CENTER);
        return panel;
    }

    private JPanel singleAccountCard() {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(300, 260));
        card.setMinimumSize(new Dimension(260, 220));

        JLabel title = new JLabel("单个账号注册");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JPanel action = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 0, 0));
        action.setOpaque(false);
        VCampusTheme.primaryButton(createButton);
        action.add(createButton);

        JLabel hint = new JLabel("<html>适合为已有学生/教师档案或管理岗位创建单个账号。</html>");
        hint.setForeground(VCampusTheme.MUTED);

        card.add(title, BorderLayout.NORTH);
        card.add(hint, BorderLayout.CENTER);
        card.add(action, BorderLayout.SOUTH);
        return card;
    }

    private JPanel accountListCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setMinimumSize(new Dimension(0, 250));
        card.setPreferredSize(new Dimension(0, 280));

        JLabel title = new JLabel("账号列表");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        configureAccountTable();
        JScrollPane tableScroll = VCampusTheme.scrollPane(accountTable);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshAccountsButton);
        VCampusTheme.primaryButton(enableAccountButton);
        VCampusTheme.secondaryButton(disableAccountButton);
        VCampusTheme.secondaryButton(unregisterAccountButton);
        actions.add(refreshAccountsButton);
        actions.add(enableAccountButton);
        actions.add(disableAccountButton);
        actions.add(unregisterAccountButton);
        accountStatus.setForeground(VCampusTheme.MUTED);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(accountStatus, BorderLayout.SOUTH);

        card.add(title, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel passwordResetCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(0, 260));
        card.setMinimumSize(new Dimension(0, 220));

        JLabel title = new JLabel("密码重置审批");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        configureResetTable();
        JScrollPane tableScroll = VCampusTheme.scrollPane(resetTable);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshResetButton);
        VCampusTheme.primaryButton(approveResetButton);
        VCampusTheme.secondaryButton(rejectResetButton);
        actions.add(refreshResetButton);
        actions.add(approveResetButton);
        actions.add(rejectResetButton);
        resetStatus.setForeground(VCampusTheme.MUTED);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(resetStatus, BorderLayout.SOUTH);

        card.add(title, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel importCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(620, 260));
        card.setMinimumSize(new Dimension(300, 260));

        JPanel titlePanel = new JPanel(new BorderLayout(0, 4));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("批量导入账号");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel hint = new JLabel("支持 .xlsx、.csv、.tsv；导入源是表格文件，最终数据仍保存到 Access。");
        hint.setForeground(VCampusTheme.MUTED);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(hint, BorderLayout.SOUTH);

        configureTable();
        JScrollPane tableScroll = VCampusTheme.scrollPane(importTable);

        card.add(titlePanel, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(importFooter(), BorderLayout.SOUTH);
        return card;
    }

    private JPanel auditCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(card);
        card.setPreferredSize(new Dimension(0, 260));
        card.setMinimumSize(new Dimension(0, 220));

        JLabel title = new JLabel("审计记录");
        title.setFont(VCampusTheme.font(Font.BOLD, 18));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        configureAuditTable();
        JScrollPane tableScroll = VCampusTheme.scrollPane(auditTable);

        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 8, 4));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshAuditButton);
        actions.add(refreshAuditButton);
        auditStatus.setForeground(VCampusTheme.MUTED);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(auditStatus, BorderLayout.SOUTH);

        card.add(title, BorderLayout.NORTH);
        card.add(tableScroll, BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private void configureTable() {
        VCampusTheme.table(importTable);
        importTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        importTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        importTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        importTable.getColumnModel().getColumn(4).setPreferredWidth(220);
    }

    private void configureAccountTable() {
        VCampusTheme.table(accountTable);
        accountTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountTable.getSelectionModel().addListSelectionListener(event -> updateButtonState());
        accountTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        accountTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        accountTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        accountTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        accountTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        accountTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        accountTable.getColumnModel().getColumn(6).setPreferredWidth(220);
    }

    private void configureResetTable() {
        VCampusTheme.table(resetTable);
        resetTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resetTable.getSelectionModel().addListSelectionListener(event -> updateButtonState());
        resetTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        resetTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        resetTable.getColumnModel().getColumn(2).setPreferredWidth(70);
    }

    private void configureAuditTable() {
        VCampusTheme.table(auditTable);
        auditTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        auditTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        auditTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        auditTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        auditTable.getColumnModel().getColumn(3).setPreferredWidth(120);
    }

    private JPanel importFooter() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 4));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(chooseFileButton);
        VCampusTheme.primaryButton(importButton);
        actions.add(chooseFileButton);
        actions.add(importButton);

        selectedFile.setForeground(VCampusTheme.MUTED);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(selectedFile, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void chooseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择用户导入文件");
        chooser.setFileFilter(new FileNameExtensionFilter("Excel/CSV 文件 (*.xlsx, *.csv, *.tsv)",
                "xlsx", "csv", "tsv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        loadImportFile(chooser.getSelectedFile().toPath());
    }

    void loadImportFile(Path file) {
        try {
            List<UserImportRow> rows = fileReader.read(file);
            importTableModel.replaceImportRows(rows);
            selectedFile.setText(file.getFileName() + "，共读取 " + rows.size() + " 行");
            showStatus(rows.isEmpty() ? "文件中没有可导入账号" : "文件读取成功，请确认预览后导入",
                    rows.isEmpty() ? VCampusTheme.DANGER : VCampusTheme.SUCCESS);
            updateButtonState();
        } catch (IllegalArgumentException | IOException failure) {
            importTableModel.replaceImportRows(java.util.Collections.<UserImportRow>emptyList());
            selectedFile.setText("尚未选择导入文件");
            showStatus(failure.getMessage(), VCampusTheme.DANGER);
            updateButtonState();
        }
    }

    private void importUsers() {
        final List<UserImportRow> rows = importTableModel.toImportRows();
        if (rows.isEmpty()) {
            showStatus("请先选择包含账号数据的 Excel 或 CSV 文件", VCampusTheme.DANGER);
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

    private void loadAccounts() {
        requestInProgress = true;
        updateButtonState();
        showAccountStatus("正在刷新账号列表…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.listAccounts(session.getToken());
                }
            }

            @Override protected void done() {
                try {
                    handleAccountListResponse(get());
                } catch (Exception failure) {
                    showAccountStatus("刷新失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void handleAccountListResponse(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showAccountStatus("刷新失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showAccountStatus("服务器返回的账号列表格式不正确", VCampusTheme.DANGER);
            return;
        }
        accountTableModel.setRowCount(0);
        List<?> rows = (List<?>) response.getPayload();
        for (Object row : rows) {
            if (row instanceof UserAccountSummary) {
                UserAccountSummary summary = (UserAccountSummary) row;
                accountTableModel.addRow(new Object[] {
                        summary.getUserId(), summary.getDisplayName(), summary.getRole().name(),
                        summary.getProfileId(), summary.getStatusText(), summary.getCreatedBy(),
                        summary.getImportBatchId()});
            }
        }
        showAccountStatus("账号共 " + accountTableModel.getRowCount() + " 条", VCampusTheme.SUCCESS);
    }

    private void setSelectedAccountActive(final boolean active) {
        final String targetUserId = selectedAccountUserId();
        if (targetUserId == null) {
            showAccountStatus("请先选择一条账号", VCampusTheme.DANGER);
            return;
        }
        String label = active ? "启用" : "停用";
        if (JOptionPane.showConfirmDialog(this, "确定要" + label + "账号 “" + targetUserId + "” 吗？",
                "确认" + label, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        requestInProgress = true;
        updateButtonState();
        showAccountStatus("正在" + label + "账号…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.setAccountActive(session.getToken(), targetUserId, active);
                }
            }

            @Override protected void done() {
                try {
                    Message response = get();
                    if (response.getStatusCode() == StatusCode.OK) {
                        loadAccounts();
                        showAccountStatus("已" + label + "账号", VCampusTheme.SUCCESS);
                    } else {
                        showAccountStatus(label + "失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
                    }
                } catch (Exception failure) {
                    showAccountStatus(label + "失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void unregisterSelectedAccount() {
        final String targetUserId = selectedAccountUserId();
        if (targetUserId == null) {
            showAccountStatus("请先选择一条账号", VCampusTheme.DANGER);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "注销后账号将无法登录，确认继续？",
                "确认注销", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        requestInProgress = true;
        updateButtonState();
        showAccountStatus("正在注销账号…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.unregister(session.getToken(), targetUserId);
                }
            }

            @Override protected void done() {
                try {
                    Message response = get();
                    if (response.getStatusCode() == StatusCode.OK) {
                        loadAccounts();
                        showAccountStatus("已注销账号", VCampusTheme.SUCCESS);
                    } else {
                        showAccountStatus("注销失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
                    }
                } catch (Exception failure) {
                    showAccountStatus("注销失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void loadAuditEvents() {
        requestInProgress = true;
        updateButtonState();
        showAuditStatus("正在刷新审计记录…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.listAuditEvents(session.getToken());
                }
            }

            @Override protected void done() {
                try {
                    handleAuditListResponse(get());
                } catch (Exception failure) {
                    showAuditStatus("刷新失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void handleAuditListResponse(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showAuditStatus("刷新失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showAuditStatus("服务器返回的审计记录格式不正确", VCampusTheme.DANGER);
            return;
        }
        auditTableModel.setRowCount(0);
        List<?> rows = (List<?>) response.getPayload();
        for (Object row : rows) {
            if (row instanceof AuditEvent) {
                AuditEvent event = (AuditEvent) row;
                auditTableModel.addRow(new Object[] {
                        String.valueOf(event.getCreatedAt()), event.getActorUserId(), event.getAction(),
                        event.getTargetType() + ":" + event.getTargetId()});
            }
        }
        showAuditStatus("审计记录共 " + auditTableModel.getRowCount() + " 条", VCampusTheme.SUCCESS);
    }

    private String selectedAccountUserId() {
        int row = accountTable.getSelectedRow();
        if (row < 0) {
            return null;
        }
        Object value = accountTableModel.getValueAt(row, 0);
        return value == null ? null : String.valueOf(value);
    }

    private void loadPasswordResetApplications() {
        requestInProgress = true;
        updateButtonState();
        showResetStatus("正在刷新重置申请…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.listPasswordResetApplications(session.getToken());
                }
            }

            @Override protected void done() {
                try {
                    handlePasswordResetListResponse(get());
                } catch (Exception failure) {
                    showResetStatus("刷新失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void handlePasswordResetListResponse(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResetStatus("刷新失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showResetStatus("服务器返回的重置申请格式不正确", VCampusTheme.DANGER);
            return;
        }
        resetTableModel.setRowCount(0);
        List<?> rows = (List<?>) response.getPayload();
        for (Object row : rows) {
            if (row instanceof PasswordResetApplicationSummary) {
                PasswordResetApplicationSummary summary = (PasswordResetApplicationSummary) row;
                resetTableModel.addRow(new Object[]{
                        summary.getUserId(), String.valueOf(summary.getSubmittedAt()), summary.getStatus().name()});
            }
        }
        showResetStatus("待审批申请 " + resetTableModel.getRowCount() + " 条", VCampusTheme.SUCCESS);
    }

    private void reviewSelectedPasswordReset(final boolean approved) {
        int row = resetTable.getSelectedRow();
        if (row < 0) {
            showResetStatus("请先选择一条重置申请", VCampusTheme.DANGER);
            return;
        }
        final int selectedRow = row;
        final String targetUserId = String.valueOf(resetTableModel.getValueAt(selectedRow, 0));
        requestInProgress = true;
        updateButtonState();
        showResetStatus("正在处理 " + targetUserId + " 的申请…", VCampusTheme.MUTED);

        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteUserService service = new RemoteUserService(host, port)) {
                    return service.reviewPasswordReset(session.getToken(), targetUserId, approved);
                }
            }

            @Override protected void done() {
                try {
                    Message response = get();
                    if (response.getStatusCode() == StatusCode.OK) {
                        resetTableModel.removeRow(selectedRow);
                        showResetStatus(approved ? "已通过重置申请" : "已拒绝重置申请", VCampusTheme.SUCCESS);
                    } else {
                        showResetStatus("处理失败：" + statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
                    }
                } catch (Exception failure) {
                    showResetStatus("处理失败，请确认服务器已启动且网络连接正常", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateButtonState();
                }
            }
        }.execute();
    }

    private void updateButtonState() {
        createButton.setEnabled(!requestInProgress);
        chooseFileButton.setEnabled(!requestInProgress);
        importButton.setEnabled(!requestInProgress && !importTableModel.toImportRows().isEmpty());
        importTable.setEnabled(!requestInProgress);
        refreshAccountsButton.setEnabled(!requestInProgress);
        enableAccountButton.setEnabled(!requestInProgress && accountTable.getSelectedRow() >= 0);
        disableAccountButton.setEnabled(!requestInProgress && accountTable.getSelectedRow() >= 0);
        unregisterAccountButton.setEnabled(!requestInProgress && accountTable.getSelectedRow() >= 0);
        accountTable.setEnabled(!requestInProgress);
        refreshResetButton.setEnabled(!requestInProgress);
        approveResetButton.setEnabled(!requestInProgress && resetTable.getSelectedRow() >= 0);
        rejectResetButton.setEnabled(!requestInProgress && resetTable.getSelectedRow() >= 0);
        resetTable.setEnabled(!requestInProgress);
        refreshAuditButton.setEnabled(!requestInProgress);
        auditTable.setEnabled(!requestInProgress);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private void showResetStatus(String message, Color color) {
        resetStatus.setText(message);
        resetStatus.setForeground(color);
    }

    private void showAccountStatus(String message, Color color) {
        accountStatus.setText(message);
        accountStatus.setForeground(color);
    }

    private void showAuditStatus(String message, Color color) {
        auditStatus.setText(message);
        auditStatus.setForeground(color);
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
        if (code == StatusCode.NOT_FOUND) {
            return "未找到目标账号";
        }
        return "服务器处理请求失败";
    }

}

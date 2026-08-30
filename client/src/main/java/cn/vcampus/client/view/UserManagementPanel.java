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
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
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
        add(body(), BorderLayout.CENTER);

        createButton.addActionListener(event -> new RegisterDialog(owner, host, port, session).setVisible(true));
        chooseFileButton.addActionListener(event -> chooseImportFile());
        importButton.addActionListener(event -> importUsers());
        updateButtonState();
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
        JLabel hint = new JLabel("支持 .xlsx、.csv、.tsv；导入源是表格文件，最终数据仍保存到 Access。");
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
        importTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        importTable.getTableHeader().setReorderingAllowed(false);
        importTable.setGridColor(VCampusTheme.BORDER);
        importTable.setShowVerticalLines(false);
        importTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        importTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        importTable.getColumnModel().getColumn(3).setPreferredWidth(220);
    }

    private JPanel importFooter() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
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

    private void updateButtonState() {
        createButton.setEnabled(!requestInProgress);
        chooseFileButton.setEnabled(!requestInProgress);
        importButton.setEnabled(!requestInProgress && !importTableModel.toImportRows().isEmpty());
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

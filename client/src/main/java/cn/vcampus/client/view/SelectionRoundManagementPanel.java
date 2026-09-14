package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** 教务端维护选课轮次的启用状态和开放时间。 */
final class SelectionRoundManagementPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String host;
    private final int port;
    private final Session session;
    private final JComboBox<String> term = CourseTermOptions.comboBox();
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "轮次编号", "学期", "类型", "开始时间", "结束时间", "是否启用", "当前是否开放" });
    private final JTable table = new JTable(tableModel);
    private final List<SelectionRound> rounds = new ArrayList<SelectionRound>();
    private final JButton refreshButton = new JButton("查询轮次");
    private final JButton createButton = new JButton("新建轮次");
    private final JButton editButton = new JButton("编辑时间");
    private final JButton toggleEnabledButton = new JButton("启用/停用");
    private final JLabel statusHint = new JLabel();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;

    SelectionRoundManagementPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(12)));
        setOpaque(false);
        VCampusTheme.field(term);
        refreshButton.addActionListener(e -> loadRounds());
        createButton.addActionListener(e -> createRound());
        editButton.addActionListener(e -> editRound());
        toggleEnabledButton.addActionListener(e -> toggleEnabled());
        add(controls(), BorderLayout.NORTH);
        add(tableCard(), BorderLayout.CENTER);
        add(statusHint, BorderLayout.SOUTH);
        showStatus("正在自动加载选课轮次", VCampusTheme.MUTED);
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadRounds);
    }

    private JPanel controls() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("选课轮次");
        JLabel hint = sectionHint("已启用的轮次仅在开始与结束时间之间向学生开放；修改时间前须先停用。 ");
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(4)));
        fields.setOpaque(false);
        fields.add(new JLabel("学期"));
        fields.add(term);
        addSecondary(fields, refreshButton);
        addPrimary(fields, createButton);
        addSecondary(fields, editButton);
        addSecondary(fields, toggleEnabledButton);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        return card;
    }

    private JPanel tableCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        header.add(sectionTitle("本学期轮次"), BorderLayout.NORTH);
        header.add(sectionHint("选择轮次后可编辑停用轮次的时间，或切换启用状态。"), BorderLayout.SOUTH);
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setColumnWidth(0, 180);
        setColumnWidth(1, 130);
        setColumnWidth(2, 110);
        setColumnWidth(3, 170);
        setColumnWidth(4, 170);
        setColumnWidth(5, 100);
        setColumnWidth(6, 120);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void loadRounds() {
        String selectedTerm = (String) term.getSelectedItem();
        request(service -> service.selectionRoundsByTerm(session.getToken(), selectedTerm), response -> {
            if (!requireList(response)) return;
            rounds.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            LocalDateTime now = LocalDateTime.now();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof SelectionRound) {
                    SelectionRound round = (SelectionRound) item;
                    rounds.add(round);
                    rows.add(new Object[] { round.getRoundId(), round.getTerm(), round.getType().getDisplayName(),
                            format(round.getStartsAt()), format(round.getEndsAt()), enabledText(round),
                            availabilityText(round, now) });
                }
            }
            tableModel.replaceRows(rows);
            showStatus(rows.isEmpty() ? "该学期尚未创建选课轮次" : "已加载 " + rows.size() + " 个选课轮次",
                    rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void createRound() {
        SelectionRound round = SelectionRoundEditorDialog.create(this);
        if (round == null) return;
        request(service -> service.createSelectionRound(session.getToken(), round),
                response -> showSuccessThenReload(response, "选课轮次已新建，当前为停用状态"));
    }

    private void editRound() {
        SelectionRound selected = selectedRound();
        if (selected == null) {
            showStatus("请先选择一个选课轮次", VCampusTheme.DANGER);
            return;
        }
        if (isEnabled(selected)) {
            showStatus("请先停用该轮次，再修改开放时间", VCampusTheme.DANGER);
            return;
        }
        SelectionRound changed = SelectionRoundEditorDialog.edit(this, selected);
        if (changed == null) return;
        request(service -> service.updateSelectionRoundTimeWindow(session.getToken(), selected.getRoundId(),
                changed.getStartsAt(), changed.getEndsAt()), response -> showSuccessThenReload(response, "开放时间已更新"));
    }

    private void toggleEnabled() {
        SelectionRound selected = selectedRound();
        if (selected == null) {
            showStatus("请先选择一个选课轮次", VCampusTheme.DANGER);
            return;
        }
        boolean enabling = !isEnabled(selected);
        String action = enabling ? "启用" : "停用";
        if (!CourseUiSupport.confirmHighImpact(this, "确认" + action + "轮次",
                "确定" + action + "轮次“" + selected.getRoundId() + "”吗？",
                enabling ? "启用后，系统会按服务器当前时间自动判断学生端是否开放。" : "停用后学生将无法进入该轮次选课。")) return;
        SelectionRoundStatus target = enabling ? SelectionRoundStatus.OPEN : SelectionRoundStatus.CLOSED;
        request(service -> service.changeSelectionRoundStatus(session.getToken(), selected.getRoundId(), target),
                response -> showSuccessThenReload(response, "轮次已" + (enabling ? "启用" : "停用")));
    }

    private SelectionRound selectedRound() {
        int row = table.getSelectedRow();
        return row >= 0 && row < rounds.size() ? rounds.get(row) : null;
    }

    private void showSuccessThenReload(Message response, String message) {
        if (response.getStatusCode() == StatusCode.OK) {
            showStatus(message, VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadRounds);
        } else showFailure(response);
    }

    private void request(Request request, Response response) {
        if (requestInProgress) return;
        int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateInteractiveState();
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); }
            }
            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
                try { response.handle(get()); }
                catch (Exception failure) { showStatus("无法连接选课服务器", VCampusTheme.DANGER); }
                finally { requestInProgress = false; updateInteractiveState(); }
            }
        }.execute();
    }

    private boolean requireList(Message response) {
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) return true;
        showFailure(response);
        return false;
    }
    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        showStatus(response.getPayload() instanceof String ? (String) response.getPayload() : fallback, VCampusTheme.DANGER);
    }
    private void showStatus(String message, Color color) { CourseUiSupport.showStatus(statusHint, message, color); }
    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        term.setEnabled(interactive); refreshButton.setEnabled(interactive); createButton.setEnabled(interactive);
        editButton.setEnabled(interactive); toggleEnabledButton.setEnabled(interactive); table.setEnabled(interactive);
    }
    private void setColumnWidth(int index, int width) { table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(width)); }
    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text); label.setFont(VCampusTheme.font(java.awt.Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK); return label;
    }
    private JLabel sectionHint(String text) { JLabel label = new JLabel(text); label.setForeground(VCampusTheme.MUTED); return label; }
    private static void addPrimary(JPanel parent, JButton button) { VCampusTheme.primaryButton(button); parent.add(button); }
    private static void addSecondary(JPanel parent, JButton button) { VCampusTheme.secondaryButton(button); parent.add(button); }
    private static boolean isEnabled(SelectionRound round) { return round.getStatus() == SelectionRoundStatus.OPEN; }
    private static String enabledText(SelectionRound round) { return isEnabled(round) ? "已启用" : "已停用"; }
    private static String availabilityText(SelectionRound round, LocalDateTime now) {
        if (!isEnabled(round)) return "未启用";
        if (now.isBefore(round.getStartsAt())) return "未开始";
        if (now.isAfter(round.getEndsAt())) return "已结束";
        return "开放中";
    }
    private static String format(LocalDateTime value) { return TIME_FORMAT.format(value); }
    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

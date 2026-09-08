package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.SelectionRoundType;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** 教务端维护首修、重修选课轮次及其开放时间窗口的页面。 */
final class SelectionRoundManagementPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String host;
    private final int port;
    private final Session session;
    private final JTextField term = new JTextField("2026-2027-1", 11);
    private final JTextField roundId = new JTextField(14);
    private final JComboBox<SelectionRoundType> type =
            new JComboBox<SelectionRoundType>(SelectionRoundType.values());
    private final JTextField startsAt = new JTextField("2026-09-01 08:00", 16);
    private final JTextField endsAt = new JTextField("2026-09-07 18:00", 16);
    private final JComboBox<SelectionRoundStatus> status =
            new JComboBox<SelectionRoundStatus>(SelectionRoundStatus.values());
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "轮次编号", "学期", "类型", "开始时间", "结束时间", "状态" });
    private final JTable table = new JTable(tableModel);
    private final List<SelectionRound> rounds = new ArrayList<SelectionRound>();
    private final JButton refreshButton = new JButton("查询本学期轮次");
    private final JButton createButton = new JButton("新建轮次");
    private final JButton updateTimeButton = new JButton("更新开放时间");
    private final JButton changeStatusButton = new JButton("更新轮次状态");
    private final JLabel statusHint = new JLabel();
    private final List<JComponent> inputs = new ArrayList<JComponent>();
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
        styleAndTrackFields(term, roundId, startsAt, endsAt);
        VCampusTheme.field(type);
        VCampusTheme.field(status);
        inputs.add(type);
        inputs.add(status);
        styleRoundCombo(type);
        styleRoundStatusCombo(status);

        refreshButton.addActionListener(e -> loadRounds());
        createButton.addActionListener(e -> createRound());
        updateTimeButton.addActionListener(e -> updateTimeWindow());
        changeStatusButton.addActionListener(e -> changeStatus());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillFieldsFromSelection();
            }
        });

        add(controls(), BorderLayout.NORTH);
        add(tableCard(), BorderLayout.CENTER);
        add(statusHint, BorderLayout.SOUTH);
        showStatus("填写学期后查询，或新建首修/重修轮次", VCampusTheme.MUTED);
        updateInteractiveState();
    }

    private JPanel controls() {
        JPanel controls = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        controls.setOpaque(false);
        controls.add(queryCard(), BorderLayout.NORTH);
        controls.add(roundFormCard(), BorderLayout.CENTER);
        return controls;
    }

    /** 先按学期定位轮次，避免在不同学期的轮次之间误改状态。 */
    private JPanel queryCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("第 1 步：查询选课轮次");
        JLabel hint = sectionHint("同一学期的首修轮次和重修轮次各只能创建一个。 ");
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        fields.setOpaque(false);
        fields.add(new JLabel("学期"));
        fields.add(term);
        addSecondary(fields, refreshButton);
        card.add(title, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    private JPanel roundFormCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("第 2 步：新建或维护选课轮次");
        JLabel hint = sectionHint("从下方选择一行后，信息会自动带入；开放轮次会向符合条件的学生显示。 ");
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        fields.setOpaque(false);
        fields.add(new JLabel("轮次编号")); fields.add(roundId);
        fields.add(new JLabel("轮次类型")); fields.add(type);
        fields.add(new JLabel("开始时间")); fields.add(startsAt);
        fields.add(new JLabel("结束时间")); fields.add(endsAt);
        fields.add(new JLabel("轮次状态")); fields.add(status);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        addPrimary(actions, createButton);
        addSecondary(actions, updateTimeButton);
        addSecondary(actions, changeStatusButton);
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        content.setOpaque(false);
        content.add(fields, BorderLayout.NORTH);
        content.add(actions, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel tableCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        JLabel title = sectionTitle("本学期选课轮次");
        JLabel hint = sectionHint("选择一行后，可调整时间窗口或轮次状态。 ");
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        setColumnWidth(0, 180);
        setColumnWidth(1, 130);
        setColumnWidth(2, 110);
        setColumnWidth(3, 170);
        setColumnWidth(4, 170);
        setColumnWidth(5, 90);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void loadRounds() {
        try {
            String selectedTerm = text(term, "学期");
            request(service -> service.selectionRoundsByTerm(session.getToken(), selectedTerm), response -> {
                if (!requireList(response, "选课轮次")) {
                    return;
                }
                rounds.clear();
                List<Object[]> rows = new ArrayList<Object[]>();
                for (Object item : (List<?>) response.getPayload()) {
                    if (item instanceof SelectionRound) {
                        SelectionRound round = (SelectionRound) item;
                        rounds.add(round);
                        rows.add(new Object[] { round.getRoundId(), round.getTerm(),
                                round.getType().getDisplayName(), format(round.getStartsAt()),
                                format(round.getEndsAt()), round.getStatus().getDisplayName() });
                    }
                }
                tableModel.replaceRows(rows);
                showStatus(rows.isEmpty() ? "该学期尚未创建选课轮次" : "已加载 " + rows.size()
                        + " 个选课轮次", rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
            });
        } catch (IllegalArgumentException invalid) {
            showStatus(invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void createRound() {
        try {
            SelectionRound round = new SelectionRound(text(roundId, "轮次编号"), text(term, "学期"),
                    (SelectionRoundType) type.getSelectedItem(), parseTime(startsAt, "开始时间"),
                    parseTime(endsAt, "结束时间"), (SelectionRoundStatus) status.getSelectedItem());
            request(service -> service.createSelectionRound(session.getToken(), round), response -> {
                if (response.getStatusCode() == StatusCode.OK) {
                    showStatus("选课轮次已新建，请查询本学期轮次确认", VCampusTheme.SUCCESS);
                } else {
                    showFailure(response);
                }
            });
        } catch (IllegalArgumentException invalid) {
            showStatus("轮次信息填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void updateTimeWindow() {
        try {
            String id = text(roundId, "轮次编号");
            LocalDateTime start = parseTime(startsAt, "开始时间");
            LocalDateTime end = parseTime(endsAt, "结束时间");
            if (!CourseUiSupport.confirmHighImpact(this, "确认修改开放时间",
                    "确定更新轮次“" + id + "”的开放时间吗？",
                    "时间窗口会直接影响学生能否进入该轮次选课。")) {
                return;
            }
            request(service -> service.updateSelectionRoundTimeWindow(session.getToken(), id, start, end),
                    response -> showSuccess(response, "开放时间已更新，请重新查询确认"));
        } catch (IllegalArgumentException invalid) {
            showStatus("时间填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void changeStatus() {
        try {
            String id = text(roundId, "轮次编号");
            SelectionRoundStatus selectedStatus = (SelectionRoundStatus) status.getSelectedItem();
            if (!CourseUiSupport.confirmHighImpact(this, "确认修改轮次状态",
                    "确定将轮次“" + id + "”设为“" + selectedStatus.getDisplayName() + "”吗？",
                    "轮次状态会决定学生是否能看到并参与本轮选课。")) {
                return;
            }
            request(service -> service.changeSelectionRoundStatus(session.getToken(), id, selectedStatus),
                    response -> showSuccess(response, "轮次状态已更新，请重新查询确认"));
        } catch (IllegalArgumentException invalid) {
            showStatus(invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void fillFieldsFromSelection() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= rounds.size()) {
            return;
        }
        SelectionRound round = rounds.get(row);
        roundId.setText(round.getRoundId());
        term.setText(round.getTerm());
        type.setSelectedItem(round.getType());
        startsAt.setText(format(round.getStartsAt()));
        endsAt.setText(format(round.getEndsAt()));
        status.setSelectedItem(round.getStatus());
    }

    private void request(Request request, Response response) {
        if (requestInProgress) {
            return;
        }
        int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateInteractiveState();
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) {
                    return request.run(service);
                }
            }

            @Override
            protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) {
                    return;
                }
                try {
                    response.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接选课服务器", VCampusTheme.DANGER);
                } finally {
                    requestInProgress = false;
                    updateInteractiveState();
                }
            }
        }.execute();
    }

    private boolean requireList(Message response, String target) {
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) {
            return true;
        }
        showFailure(response);
        return false;
    }

    private void showSuccess(Message response, String message) {
        if (response.getStatusCode() == StatusCode.OK) {
            showStatus(message, VCampusTheme.SUCCESS);
        } else {
            showFailure(response);
        }
    }

    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        String message = response.getPayload() instanceof String ? (String) response.getPayload() : fallback;
        showStatus(message, VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(statusHint, message, color);
    }

    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        refreshButton.setEnabled(interactive);
        createButton.setEnabled(interactive);
        updateTimeButton.setEnabled(interactive);
        changeStatusButton.setEnabled(interactive);
        table.setEnabled(interactive);
        for (JComponent input : inputs) {
            input.setEnabled(interactive);
        }
    }

    private void setColumnWidth(int index, int width) {
        table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(width));
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(java.awt.Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private JLabel sectionHint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(VCampusTheme.MUTED);
        return label;
    }

    private void styleAndTrackFields(JTextField... fields) {
        for (JTextField field : fields) {
            VCampusTheme.field(field);
            inputs.add(field);
        }
    }

    private static void addPrimary(JPanel parent, JButton button) {
        VCampusTheme.primaryButton(button);
        parent.add(button);
    }

    private static void addSecondary(JPanel parent, JButton button) {
        VCampusTheme.secondaryButton(button);
        parent.add(button);
    }

    private static void styleRoundCombo(JComboBox<SelectionRoundType> box) {
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object display = value instanceof SelectionRoundType
                        ? ((SelectionRoundType) value).getDisplayName() : value;
                return super.getListCellRendererComponent(list, display, index, isSelected,
                        cellHasFocus);
            }
        });
    }

    private static void styleRoundStatusCombo(JComboBox<SelectionRoundStatus> box) {
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                Object display = value instanceof SelectionRoundStatus
                        ? ((SelectionRoundStatus) value).getDisplayName() : value;
                return super.getListCellRendererComponent(list, display, index, isSelected,
                        cellHasFocus);
            }
        });
    }

    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private static LocalDateTime parseTime(JTextField field, String name) {
        try {
            return LocalDateTime.parse(text(field, name), TIME_FORMAT);
        } catch (DateTimeParseException invalid) {
            throw new IllegalArgumentException(name + "格式应为 yyyy-MM-dd HH:mm");
        }
    }

    private static String format(LocalDateTime value) {
        return TIME_FORMAT.format(value);
    }

    private interface Request {
        Message run(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface Response {
        void handle(Message response);
    }
}

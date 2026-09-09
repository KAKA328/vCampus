package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectableCourseOffering;
import cn.vcampus.course.SelectedCourseOffering;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.SwingWorker;
import javax.swing.ListSelectionModel;

/**
 * 学生选课界面：先选择轮次，再查看可选教学班并选课；“我的已选”中可退选。
 */
public final class CourseSelectionPanel extends JPanel {
    private static final int[] TABLE_COLUMN_WIDTHS = {
            86, 108, 170, 62, 126, 106, 220, 116, 88
    };

    private final String host;
    private final int port;
    private final Session session;
    private final JComboBox<String> roundBox = new JComboBox<String>();
    private final List<SelectionRound> rounds = new ArrayList<SelectionRound>();
    private final List<String> offeringIds = new ArrayList<String>();
    private final List<String> recordIds = new ArrayList<String>();
    private final BatchTableModel tableModel = new BatchTableModel(
            new Object[] { "类别", "课程编号", "课程名称", "学分", "教学班", "教师", "时间", "地点", "剩余名额" });
    private final JTable table = new JTable(tableModel);
    private final JLabel status = new JLabel();
    private final JLabel tableTitle = new JLabel();
    private final JLabel tableHint = new JLabel();
    private final JLabel actionHint = new JLabel();
    private final JButton loadRoundsButton = new JButton("刷新选课轮次");
    private final JButton refreshViewButton = new JButton("刷新当前列表");
    private final JToggleButton offeringsViewButton = new JToggleButton("可选教学班");
    private final JToggleButton selectedViewButton = new JToggleButton("我的已选课程");
    private final JButton primaryActionButton = new JButton();
    private boolean showingSelected;
    /** 当前网络请求尚未结束时，禁止再次提交选课相关操作。 */
    private boolean requestInProgress;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public CourseSelectionPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) throw new IllegalArgumentException("host and session must not be null");
        this.host = host.trim(); this.port = port; this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(16)));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);
        showStatus("请先加载选课轮次", VCampusTheme.MUTED);

        loadRoundsButton.addActionListener(e -> loadRounds());
        refreshViewButton.addActionListener(e -> refreshCurrentView());
        offeringsViewButton.addActionListener(e -> showOfferings());
        selectedViewButton.addActionListener(e -> showSelected());
        primaryActionButton.addActionListener(e -> {
            if (showingSelected) {
                drop();
            } else {
                select();
            }
        });
        roundBox.addActionListener(e -> roundChanged());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInteractiveState();
            }
        });
        configureViewSwitch();
        updateViewPresentation();
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadRounds);
    }

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(16)));
        panel.setOpaque(false);
        JPanel workspace = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        workspace.setOpaque(false);
        workspace.add(roundCard(), BorderLayout.NORTH);
        workspace.add(tablePanel(), BorderLayout.CENTER);
        panel.add(workspace, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        panel.setOpaque(false);
        JLabel title = new JLabel("选课系统");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("先选择选课轮次，再查看可选教学班或管理本人已选课程。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    /** 把轮次选择与列表切换分为两步，避免学生面对一组没有顺序的操作按钮。 */
    private JPanel roundCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(card);

        JPanel roundRow = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        roundRow.setOpaque(false);
        JLabel roundLabel = new JLabel("第 1 步：选择选课轮次");
        roundLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
        roundLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.field(roundBox);
        roundBox.setPreferredSize(UiMetrics.dimension(260, roundBox.getPreferredSize().height));
        VCampusTheme.secondaryButton(loadRoundsButton);
        roundRow.add(roundLabel);
        roundRow.add(roundBox);
        roundRow.add(loadRoundsButton);

        JPanel viewRow = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        viewRow.setOpaque(false);
        JLabel viewLabel = new JLabel("第 2 步：选择要办理的事项");
        viewLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
        viewLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        viewRow.add(viewLabel);
        viewRow.add(offeringsViewButton);
        viewRow.add(selectedViewButton);

        card.add(roundRow, BorderLayout.NORTH);
        card.add(viewRow, BorderLayout.SOUTH);
        return card;
    }

    private JPanel tablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);
        panel.setPreferredSize(UiMetrics.dimension(0, 420));
        panel.setMinimumSize(UiMetrics.dimension(0, 240));
        configureTable();
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        tableTitle.setFont(VCampusTheme.font(Font.BOLD, 17));
        tableTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        tableHint.setFont(VCampusTheme.font(Font.PLAIN, 13));
        tableHint.setForeground(VCampusTheme.MUTED);
        header.add(tableTitle, BorderLayout.NORTH);
        header.add(tableHint, BorderLayout.SOUTH);
        VCampusTheme.secondaryButton(refreshViewButton);
        JPanel actionBar = new JPanel(new BorderLayout(UiMetrics.px(12), 0));
        actionBar.setOpaque(false);
        actionHint.setForeground(VCampusTheme.MUTED);
        VCampusTheme.primaryButton(primaryActionButton);
        actionBar.add(actionHint, BorderLayout.CENTER);
        actionBar.add(primaryActionButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        panel.add(actionBar, BorderLayout.SOUTH);
        header.add(refreshViewButton, BorderLayout.EAST);
        return panel;
    }

    private void configureViewSwitch() {
        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(offeringsViewButton);
        viewGroup.add(selectedViewButton);
        offeringsViewButton.setSelected(true);
        styleViewSwitch();
    }

    private void styleViewSwitch() {
        if (offeringsViewButton.isSelected()) {
            VCampusTheme.primaryButton(offeringsViewButton);
            VCampusTheme.secondaryButton(selectedViewButton);
        } else {
            VCampusTheme.secondaryButton(offeringsViewButton);
            VCampusTheme.primaryButton(selectedViewButton);
        }
    }

    /** 窄窗口优先保留列内容，通过表格自身的横向滚动查看完整信息。 */
    private void configureTable() {
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < TABLE_COLUMN_WIDTHS.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(TABLE_COLUMN_WIDTHS[index]));
        }
    }

    private void loadRounds() {
        request(service -> service.availableRounds(session.getToken()), response -> {
            if (!ok(response) || !(response.getPayload() instanceof List<?>)) return;
            rounds.clear(); roundBox.removeAllItems();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectionRound) {
                SelectionRound round = (SelectionRound) item; rounds.add(round);
                roundBox.addItem(round.getType() == cn.vcampus.course.SelectionRoundType.INITIAL ? "首修轮次" : "重修轮次");
            }
            showingSelected = false;
            offeringIds.clear();
            recordIds.clear();
            tableModel.replaceRows(new ArrayList<Object[]>());
            updateViewPresentation();
            showStatus(rounds.isEmpty() ? "当前没有可用选课轮次" : "请选择一个选课轮次",
                    rounds.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void roundChanged() {
        if (roundBox.getSelectedIndex() >= 0 && !requestInProgress) {
            tableModel.replaceRows(new ArrayList<Object[]>());
            offeringIds.clear();
            recordIds.clear();
            showStatus("已切换选课轮次，请查看可选教学班", VCampusTheme.MUTED);
        }
        updateInteractiveState();
    }

    private void showOfferings() {
        showingSelected = false;
        styleViewSwitch();
        updateViewPresentation();
        loadOfferings();
    }

    private void showSelected() {
        showingSelected = true;
        styleViewSwitch();
        updateViewPresentation();
        loadSelected();
    }

    private void refreshCurrentView() {
        if (showingSelected) {
            loadSelected();
        } else {
            loadOfferings();
        }
    }

    private void loadOfferings() {
        int index = roundBox.getSelectedIndex();
        if (index < 0) { showStatus("请先选择选课轮次", VCampusTheme.DANGER); return; }
        final String roundId = rounds.get(index).getRoundId();
        request(service -> service.availableOfferings(session.getToken(), roundId), response -> {
            if (!ok(response) || !(response.getPayload() instanceof List<?>)) return;
            showingSelected = false; offeringIds.clear(); recordIds.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectableCourseOffering) {
                SelectableCourseOffering value = (SelectableCourseOffering) item;
                offeringIds.add(value.getOffering().getOfferingId());
                rows.add(new Object[] { value.getSelectionType().getDisplayName(), value.getCourse().getCourseId(), value.getCourse().getName(), value.getCourse().getCredits(), value.getOffering().getOfferingId(), value.getOffering().getTeacherId(), value.getOffering().getSchedule(), value.getOffering().getLocation(), value.getCapacityUsage().getRemainingCapacity() });
            }
            tableModel.replaceRows(rows);
            updateViewPresentation();
            showStatus(rows.isEmpty() ? "本轮暂时没有可选教学班" : "已显示可选教学班",
                    rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void loadSelected() {
        request(service -> service.selectedOfferings(session.getToken()), response -> {
            if (!ok(response) || !(response.getPayload() instanceof List<?>)) return;
            showingSelected = true; offeringIds.clear(); recordIds.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectedCourseOffering) {
                SelectedCourseOffering value = (SelectedCourseOffering) item;
                recordIds.add(value.getRecord().getRecordId());
                rows.add(new Object[] { value.getRecord().getSelectionType().getDisplayName(), value.getCourse().getCourseId(), value.getCourse().getName(), value.getCourse().getCredits(), value.getOffering().getOfferingId(), value.getOffering().getTeacherId(), value.getOffering().getSchedule(), value.getOffering().getLocation(), "-" });
            }
            tableModel.replaceRows(rows);
            updateViewPresentation();
            showStatus(rows.isEmpty() ? "当前没有有效选课记录" : "已显示当前有效选课记录",
                    rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void select() {
        int row = table.getSelectedRow(); int roundIndex = roundBox.getSelectedIndex();
        if (showingSelected || row < 0 || roundIndex < 0) {
            showStatus("请在本轮可选教学班中选择一行", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.select(session.getToken(), rounds.get(roundIndex).getRoundId(), offeringIds.get(row)), response -> {
            if (ok(response)) { showStatus("选课成功，请刷新列表", VCampusTheme.SUCCESS); }
        });
    }

    private void drop() {
        int row = table.getSelectedRow();
        if (!showingSelected || row < 0) {
            showStatus("请先进入“我的已选课程”并选择一行", VCampusTheme.DANGER);
            return;
        }
        String courseName = String.valueOf(tableModel.getValueAt(row, 2));
        if (!CourseUiSupport.confirmHighImpact(this, "确认退选",
                "确定退选课程“" + courseName + "”吗？",
                "该课程将不再属于你的当前有效选课记录。")) {
            return;
        }
        request(service -> service.drop(session.getToken(), recordIds.get(row)), response -> {
            if (ok(response)) { showStatus("退选成功，请刷新我的已选课程", VCampusTheme.SUCCESS); }
        });
    }

    private boolean ok(Message response) {
        if (response.getStatusCode() == StatusCode.OK) return true;
        showStatus(response.getPayload() instanceof String ? (String) response.getPayload()
                : "服务器未能完成操作：" + response.getStatusCode(), VCampusTheme.DANGER);
        return false;
    }

    private void request(Request request, Response response) {
        if (requestInProgress) {
            return;
        }
        final int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateInteractiveState();
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); } }
            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
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

    /** 根据登录角色与请求状态统一控制界面，避免重复提交或错选行。 */
    private void updateInteractiveState() {
        boolean interactive = session.getUser().getRole() == Role.STUDENT && !requestInProgress;
        int selectedRow = table.getSelectedRow();
        boolean hasRound = roundBox.getSelectedIndex() >= 0;
        boolean selectedOffering = !showingSelected && selectedRow >= 0
                && selectedRow < offeringIds.size();
        boolean selectedRecord = showingSelected && selectedRow >= 0
                && selectedRow < recordIds.size();
        loadRoundsButton.setEnabled(interactive);
        refreshViewButton.setEnabled(interactive && (showingSelected || hasRound));
        offeringsViewButton.setEnabled(interactive);
        selectedViewButton.setEnabled(interactive);
        primaryActionButton.setEnabled(interactive && (showingSelected ? selectedRecord : selectedOffering));
        roundBox.setEnabled(interactive && !rounds.isEmpty());
        table.setEnabled(interactive);
    }

    /** 视图切换时同步更新表格的语义，避免同一张表的“选择”与“退选”含义混淆。 */
    private void updateViewPresentation() {
        if (showingSelected) {
            tableTitle.setText("我的已选课程");
            tableHint.setText("这里只显示当前有效选课记录；选择一条记录后可办理退选。");
            actionHint.setText("选择一条已选课程后，才能办理退选。");
            primaryActionButton.setText("退选所选课程");
        } else {
            tableTitle.setText("本轮可选教学班");
            tableHint.setText("选择一个教学班后即可选课；课程容量以列表中的实时结果为准。");
            actionHint.setText("选择一条教学班后，才能提交选课申请。");
            primaryActionButton.setText("选择所选教学班");
        }
        updateInteractiveState();
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(status, message, color);
    }

    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

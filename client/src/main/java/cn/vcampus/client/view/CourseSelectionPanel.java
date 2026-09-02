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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;

/**
 * 学生选课界面：先选择轮次，再查看可选教学班并选课；“我的已选”中可退选。
 */
public final class CourseSelectionPanel extends JPanel {
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
    private final JLabel status = new JLabel("请先加载选课轮次");
    private final JButton loadRoundsButton = new JButton("加载选课轮次");
    private final JButton loadOfferingsButton = new JButton("查看本轮可选教学班");
    private final JButton selectedButton = new JButton("我的已选课程");
    private final JButton selectButton = new JButton("选择教学班");
    private final JButton dropButton = new JButton("退选所选记录");
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
        setLayout(new BorderLayout(8, 8));
        setOpaque(false);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        VCampusTheme.secondaryButton(loadRoundsButton);
        VCampusTheme.secondaryButton(loadOfferingsButton);
        VCampusTheme.secondaryButton(selectedButton);
        VCampusTheme.primaryButton(selectButton);
        VCampusTheme.secondaryButton(dropButton);
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        table.setGridColor(VCampusTheme.BORDER);
        status.setForeground(VCampusTheme.MUTED);
        top.add(loadRoundsButton); top.add(roundBox); top.add(loadOfferingsButton);
        top.add(selectedButton); top.add(selectButton); top.add(dropButton);
        add(top, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        loadRoundsButton.addActionListener(e -> loadRounds());
        loadOfferingsButton.addActionListener(e -> loadOfferings());
        selectedButton.addActionListener(e -> loadSelected());
        selectButton.addActionListener(e -> select());
        dropButton.addActionListener(e -> drop());
        updateInteractiveState();
    }

    private void loadRounds() {
        request(service -> service.availableRounds(session.getToken()), response -> {
            if (!ok(response) || !(response.getPayload() instanceof List<?>)) return;
            rounds.clear(); roundBox.removeAllItems();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectionRound) {
                SelectionRound round = (SelectionRound) item; rounds.add(round);
                roundBox.addItem(round.getType() == cn.vcampus.course.SelectionRoundType.INITIAL ? "首修轮次" : "重修轮次");
            }
            status.setText(rounds.isEmpty() ? "当前没有可用选课轮次" : "请选择一个选课轮次");
        });
    }

    private void loadOfferings() {
        int index = roundBox.getSelectedIndex();
        if (index < 0) { status.setText("请先选择选课轮次"); return; }
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
            status.setText("已显示可选教学班");
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
            status.setText("已显示当前有效选课记录");
        });
    }

    private void select() {
        int row = table.getSelectedRow(); int roundIndex = roundBox.getSelectedIndex();
        if (showingSelected || row < 0 || roundIndex < 0) { status.setText("请在本轮可选教学班中选择一行"); return; }
        request(service -> service.select(session.getToken(), rounds.get(roundIndex).getRoundId(), offeringIds.get(row)), response -> {
            if (ok(response)) { status.setText("选课成功，请刷新列表"); }
        });
    }

    private void drop() {
        int row = table.getSelectedRow();
        if (!showingSelected || row < 0) { status.setText("请先进入“我的已选课程”并选择一行"); return; }
        request(service -> service.drop(session.getToken(), recordIds.get(row)), response -> {
            if (ok(response)) { status.setText("退选成功，请刷新我的已选课程"); }
        });
    }

    private boolean ok(Message response) {
        if (response.getStatusCode() == StatusCode.OK) return true;
        status.setText(response.getPayload() instanceof String ? (String) response.getPayload() : "服务器未能完成操作：" + response.getStatusCode());
        return false;
    }

    private void request(Request request, Response response) {
        if (requestInProgress) {
            return;
        }
        final int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateInteractiveState();
        status.setText("正在请求服务器，请稍候…");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); } }
            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
                try {
                    response.handle(get());
                } catch (Exception failure) {
                    status.setText("无法连接选课服务器");
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
        loadRoundsButton.setEnabled(interactive);
        loadOfferingsButton.setEnabled(interactive);
        selectedButton.setEnabled(interactive);
        selectButton.setEnabled(interactive);
        dropButton.setEnabled(interactive);
        roundBox.setEnabled(interactive);
        table.setEnabled(interactive);
    }

    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

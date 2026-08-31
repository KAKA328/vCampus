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
import javax.swing.table.DefaultTableModel;

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
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] { "类别", "课程编号", "课程名称", "学分", "教学班", "教师", "时间", "地点", "剩余名额" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JLabel status = new JLabel("请先加载选课轮次");
    private boolean showingSelected;

    public CourseSelectionPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) throw new IllegalArgumentException("host and session must not be null");
        this.host = host.trim(); this.port = port; this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(8, 8));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadRounds = new JButton("加载选课轮次");
        JButton loadOfferings = new JButton("查看本轮可选教学班");
        JButton selected = new JButton("我的已选课程");
        JButton select = new JButton("选择教学班");
        JButton drop = new JButton("退选所选记录");
        top.add(loadRounds); top.add(roundBox); top.add(loadOfferings); top.add(selected); top.add(select); top.add(drop);
        add(top, BorderLayout.NORTH); add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        boolean student = session.getUser().getRole() == Role.STUDENT;
        loadRounds.setEnabled(student); loadOfferings.setEnabled(student); selected.setEnabled(student); select.setEnabled(student); drop.setEnabled(student);
        loadRounds.addActionListener(e -> loadRounds());
        loadOfferings.addActionListener(e -> loadOfferings());
        selected.addActionListener(e -> loadSelected());
        select.addActionListener(e -> select());
        drop.addActionListener(e -> drop());
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
            showingSelected = false; offeringIds.clear(); recordIds.clear(); tableModel.setRowCount(0);
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectableCourseOffering) {
                SelectableCourseOffering value = (SelectableCourseOffering) item;
                offeringIds.add(value.getOffering().getOfferingId());
                tableModel.addRow(new Object[] { value.getSelectionType().getDisplayName(), value.getCourse().getCourseId(), value.getCourse().getName(), value.getCourse().getCredits(), value.getOffering().getOfferingId(), value.getOffering().getTeacherId(), value.getOffering().getSchedule(), value.getOffering().getLocation(), value.getCapacityUsage().getRemainingCapacity() });
            }
            status.setText("已显示可选教学班");
        });
    }

    private void loadSelected() {
        request(service -> service.selectedOfferings(session.getToken()), response -> {
            if (!ok(response) || !(response.getPayload() instanceof List<?>)) return;
            showingSelected = true; offeringIds.clear(); recordIds.clear(); tableModel.setRowCount(0);
            for (Object item : (List<?>) response.getPayload()) if (item instanceof SelectedCourseOffering) {
                SelectedCourseOffering value = (SelectedCourseOffering) item;
                recordIds.add(value.getRecord().getRecordId());
                tableModel.addRow(new Object[] { value.getRecord().getSelectionType().getDisplayName(), value.getCourse().getCourseId(), value.getCourse().getName(), value.getCourse().getCredits(), value.getOffering().getOfferingId(), value.getOffering().getTeacherId(), value.getOffering().getSchedule(), value.getOffering().getLocation(), "-" });
            }
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
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); } }
            @Override protected void done() { try { response.handle(get()); } catch (Exception failure) { status.setText("无法连接选课服务器"); } }
        }.execute();
    }

    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

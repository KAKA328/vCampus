package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** 任课老师按学期查看本人负责教学班的入口页面。 */
final class TeacherTeachingPanel extends JPanel {
    private static final int[] OFFERING_COLUMN_WIDTHS = { 120, 180, 70, 160, 220, 120, 100 };
    private static final int[] ROSTER_COLUMN_WIDTHS = { 130, 120, 180, 130, 130 };

    private final String host;
    private final int port;
    private final Session session;
    private final JTextField term = new JTextField("2026-2027-1", 12);
    private final JButton refreshButton = new JButton("查询我的教学班");
    private final JButton viewRosterButton = new JButton("查看学生名单");
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "课程编号", "课程名称", "学分", "教学班", "上课时间", "地点", "教学班状态" });
    private final JTable table = new JTable(tableModel);
    private final BatchTableModel rosterModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "专业", "班级", "选课类别" });
    private final JTable rosterTable = new JTable(rosterModel);
    private final List<TeachingOffering> offerings = new ArrayList<TeachingOffering>();
    private final JLabel rosterTitle = new JLabel("学生名单");
    private final JLabel status = new JLabel();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;

    TeacherTeachingPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(16)));
        setOpaque(false);
        VCampusTheme.field(term);
        VCampusTheme.secondaryButton(refreshButton);
        VCampusTheme.primaryButton(viewRosterButton);
        refreshButton.addActionListener(e -> loadOfferings());
        viewRosterButton.addActionListener(e -> loadRoster());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInteractiveState();
            }
        });
        configureTable(table, OFFERING_COLUMN_WIDTHS);
        configureTable(rosterTable, ROSTER_COLUMN_WIDTHS);

        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);
        showStatus("输入学期后查询本人负责的教学班", VCampusTheme.MUTED);
        updateInteractiveState();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        panel.setOpaque(false);
        JLabel title = new JLabel("我的教学班");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("按学期查看本人教学班，并查看当前有效选课学生名单。 ");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        JPanel query = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(6)));
        VCampusTheme.panel(query);
        query.add(new JLabel("学期"));
        query.add(term);
        query.add(refreshButton);
        query.add(viewRosterButton);
        panel.add(query, BorderLayout.NORTH);
        panel.add(workspace(), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private JSplitPane workspace() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, offeringCard(), rosterCard());
        split.setBorder(null);
        split.setOpaque(false);
        split.setResizeWeight(0.46);
        split.setDividerSize(UiMetrics.px(10));
        split.setPreferredSize(UiMetrics.dimension(0, 620));
        split.setMinimumSize(UiMetrics.dimension(0, 420));
        return split;
    }

    private JPanel offeringCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JLabel title = new JLabel("本人教学班列表");
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        panel.add(title, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel rosterCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        rosterTitle.setFont(VCampusTheme.font(Font.BOLD, 16));
        rosterTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        panel.add(rosterTitle, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(rosterTable), BorderLayout.CENTER);
        return panel;
    }

    private static void configureTable(JTable target, int[] columnWidths) {
        VCampusTheme.table(target);
        target.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        target.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < columnWidths.length; index++) {
            target.getColumnModel().getColumn(index).setPreferredWidth(
                    UiMetrics.px(columnWidths[index]));
        }
    }

    private void loadOfferings() {
        String selectedTerm = term.getText() == null ? "" : term.getText().trim();
        if (selectedTerm.isEmpty()) {
            showStatus("学期不能为空", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.myTeachingOfferings(session.getToken(), selectedTerm), response -> {
            if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
                showFailure(response);
                return;
            }
            offerings.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof TeachingOffering) {
                    TeachingOffering value = (TeachingOffering) item;
                    offerings.add(value);
                    rows.add(new Object[] { value.getCourse().getCourseId(), value.getCourse().getName(),
                            Integer.valueOf(value.getCourse().getCredits()),
                            value.getOffering().getOfferingId(), value.getOffering().getSchedule(),
                            value.getOffering().getLocation(), value.getOffering().getStatus() });
                }
            }
            tableModel.replaceRows(rows);
            rosterModel.replaceRows(new ArrayList<Object[]>());
            rosterTitle.setText("学生名单");
            showStatus(rows.isEmpty() ? "该学期没有分配给你的教学班" : "已加载 " + rows.size()
                    + " 个本人教学班", rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void loadRoster() {
        TeachingOffering selected = selectedOffering();
        if (selected == null) {
            showStatus("请先选择一个教学班", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.teachingRoster(session.getToken(),
                selected.getOffering().getOfferingId()), response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof TeachingRoster)) {
                        showFailure(response);
                        return;
                    }
                    TeachingRoster roster = (TeachingRoster) response.getPayload();
                    List<Object[]> rows = new ArrayList<Object[]>();
                    for (TeachingRosterEntry student : roster.getStudents()) {
                        rows.add(new Object[] { student.getStudentId(), student.getStudentName(),
                                safe(student.getMajorName()), safe(student.getClassId()),
                                student.getSelectionType().getDisplayName() });
                    }
                    rosterModel.replaceRows(rows);
                    rosterTitle.setText("学生名单："
                            + roster.getTeachingOffering().getOffering().getOfferingId()
                            + "（" + rows.size() + " 人）");
                    showStatus(rows.isEmpty() ? "该教学班当前没有有效选课学生" : "已加载 "
                            + rows.size() + " 名学生", rows.isEmpty() ? VCampusTheme.MUTED
                                    : VCampusTheme.SUCCESS);
                });
    }

    private TeachingOffering selectedOffering() {
        int row = table.getSelectedRow();
        return row >= 0 && row < offerings.size() ? offerings.get(row) : null;
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

    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        String message = response.getPayload() instanceof String ? (String) response.getPayload() : fallback;
        showStatus(message, VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        VCampusTheme.statusPill(status, color);
    }

    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        refreshButton.setEnabled(interactive);
        viewRosterButton.setEnabled(interactive && selectedOffering() != null);
        term.setEnabled(interactive);
        table.setEnabled(interactive);
        rosterTable.setEnabled(interactive);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private interface Request {
        Message run(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface Response {
        void handle(Message response);
    }
}

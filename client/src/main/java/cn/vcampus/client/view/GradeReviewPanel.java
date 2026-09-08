package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionAuditRecord;
import cn.vcampus.course.TeachingGradeDraft;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** 教务端查看、通过或退回任课教师提交成绩单的工作区。 */
final class GradeReviewPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel submissionModel = new BatchTableModel(new Object[] {
            "提交单编号", "教学班", "任课教师", "提交状态", "创建时间", "最近更新" });
    private final BatchTableModel detailModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "专业", "班级", "选课类别", "成绩" });
    private final BatchTableModel auditModel = new BatchTableModel(new Object[] {
            "操作", "操作人", "操作时间", "意见" });
    private final JTable submissionTable = new JTable(submissionModel);
    private final JTable detailTable = new JTable(detailModel);
    private final JTable auditTable = new JTable(auditModel);
    private final List<GradeSubmission> submissions = new ArrayList<GradeSubmission>();
    private final JLabel status = new JLabel();
    private final JLabel detailHint = new JLabel("选择待审核成绩单后查看成绩明细。 ");
    private final JLabel selectionHint = new JLabel("请选择一份待审核成绩单，查看成绩快照后再作出处理。 ");
    private final JButton refreshButton = new JButton("刷新待审核列表");
    private final JButton detailButton = new JButton("查看成绩明细");
    private final JButton auditButton = new JButton("查看审核记录");
    private final JButton approveButton = new JButton("审核通过");
    private final JButton returnButton = new JButton("退回修改");
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;

    GradeReviewPanel(String host, int port, Session session) {
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
        configureTable(submissionTable, 220, 120, 120, 110, 150, 150);
        configureTable(detailTable, 120, 110, 170, 110, 100, 80);
        configureTable(auditTable, 110, 130, 150, 300);

        refreshButton.addActionListener(e -> loadPending());
        detailButton.addActionListener(e -> loadDetail());
        auditButton.addActionListener(e -> loadAudit());
        approveButton.addActionListener(e -> approve());
        returnButton.addActionListener(e -> returnForRevision());
        submissionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionHint();
                updateInteractiveState();
            }
        });

        add(workspace(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        showStatus("请先刷新待审核成绩单", VCampusTheme.MUTED);
        updateInteractiveState();
    }

    private JSplitPane workspace() {
        JPanel pending = pendingCard();
        JTabbedPane details = new JTabbedPane();
        VCampusTheme.tabs(details);
        details.addTab("成绩明细", detailCard());
        details.addTab("审核记录", card("成绩单流转记录", "查看本成绩单的提交、退回和审核历史。", auditTable));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pending, details);
        split.setBorder(null);
        split.setOpaque(false);
        split.setResizeWeight(0.42);
        split.setDividerSize(UiMetrics.px(10));
        split.setPreferredSize(UiMetrics.dimension(0, 520));
        split.setMinimumSize(UiMetrics.dimension(0, 360));
        return split;
    }

    /** 审核动作紧贴待审核列表，保持“选择、核验、处理”的操作顺序。 */
    private JPanel pendingCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        JLabel title = sectionTitle("第 1 步：选择待审核成绩单");
        JLabel hint = sectionHint("选择一行后，先查看成绩快照和审核记录，再决定通过或退回。 ");
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        VCampusTheme.secondaryButton(refreshButton);
        header.add(refreshButton, BorderLayout.EAST);

        JPanel actions = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        actions.setOpaque(false);
        selectionHint.setForeground(VCampusTheme.MUTED);
        JPanel buttons = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        buttons.setOpaque(false);
        VCampusTheme.secondaryButton(detailButton);
        VCampusTheme.secondaryButton(auditButton);
        VCampusTheme.primaryButton(approveButton);
        VCampusTheme.secondaryButton(returnButton);
        buttons.add(detailButton);
        buttons.add(auditButton);
        buttons.add(approveButton);
        buttons.add(returnButton);
        actions.add(selectionHint, BorderLayout.NORTH);
        actions.add(buttons, BorderLayout.SOUTH);

        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(submissionTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel detailCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        JLabel title = sectionTitle("第 2 步：核验成绩快照");
        detailHint.setFont(VCampusTheme.font(Font.PLAIN, 13));
        detailHint.setForeground(VCampusTheme.MUTED);
        header.add(title, BorderLayout.NORTH);
        header.add(detailHint, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(detailTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel card(String titleText, String hintText, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        header.add(sectionTitle(titleText), BorderLayout.NORTH);
        header.add(sectionHint(hintText), BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private static void configureTable(JTable table, int... widths) {
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(widths[index]));
        }
    }

    private void loadPending() {
        request(service -> service.pendingGradeSubmissions(session.getToken()), response -> {
            if (!requireList(response, "待审核成绩单")) {
                return;
            }
            submissions.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof GradeSubmission) {
                    GradeSubmission submission = (GradeSubmission) item;
                    submissions.add(submission);
                    rows.add(new Object[] { submission.getSubmissionId(), submission.getOfferingId(),
                            submission.getTeacherId(), submission.getStatus(),
                            format(submission.getCreatedAt()), format(submission.getUpdatedAt()) });
                }
            }
            submissionModel.replaceRows(rows);
            detailModel.replaceRows(new ArrayList<Object[]>());
            auditModel.replaceRows(new ArrayList<Object[]>());
            detailHint.setText("选择待审核成绩单后查看成绩明细。 ");
            updateSelectionHint();
            showStatus(rows.isEmpty() ? "当前没有待审核成绩单" : "已加载 " + rows.size()
                    + " 份待审核成绩单", rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void loadDetail() {
        GradeSubmission submission = selectedSubmission();
        if (submission == null) {
            showStatus("请先选择一份待审核成绩单", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.gradeReviewDetail(session.getToken(), submission.getSubmissionId()),
                response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof TeachingGradeDraft)) {
                        showFailure(response);
                        return;
                    }
                    TeachingGradeDraft draft = (TeachingGradeDraft) response.getPayload();
                    Map<String, GradeEntry> byStudentId = new LinkedHashMap<String, GradeEntry>();
                    for (GradeEntry entry : draft.getEntries()) {
                        byStudentId.put(entry.getStudentId(), entry);
                    }
                    List<Object[]> rows = new ArrayList<Object[]>();
                    for (TeachingRosterEntry student : draft.getRoster().getStudents()) {
                        GradeEntry entry = byStudentId.get(student.getStudentId());
                        rows.add(new Object[] { student.getStudentId(), student.getStudentName(),
                                safe(student.getMajorName()), safe(student.getClassId()),
                                student.getSelectionType().getDisplayName(),
                                entry == null ? "未录入" : Integer.valueOf(entry.getScore()) });
                    }
                    detailModel.replaceRows(rows);
                    String version = draft.getReviewVersionNo() == null ? "" : "（审核版本 "
                            + draft.getReviewVersionNo() + "）";
                    detailHint.setText("教学班：" + draft.getSubmission().getOfferingId() + version
                            + "，共 " + rows.size() + " 名有效选课学生。");
                    showStatus("已加载成绩快照", VCampusTheme.SUCCESS);
                });
    }

    private void loadAudit() {
        GradeSubmission submission = selectedSubmission();
        if (submission == null) {
            showStatus("请先选择一份待审核成绩单", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.gradeReviewAudit(session.getToken(), submission.getSubmissionId()),
                response -> {
                    if (!requireList(response, "审核记录")) {
                        return;
                    }
                    List<Object[]> rows = new ArrayList<Object[]>();
                    for (Object item : (List<?>) response.getPayload()) {
                        if (item instanceof GradeSubmissionAuditRecord) {
                            GradeSubmissionAuditRecord record = (GradeSubmissionAuditRecord) item;
                            rows.add(new Object[] { record.getAction(), record.getActorId(),
                                    format(record.getOccurredAt()), safe(record.getRemark()) });
                        }
                    }
                    auditModel.replaceRows(rows);
                    showStatus("已加载 " + rows.size() + " 条审核记录", VCampusTheme.SUCCESS);
                });
    }

    private void approve() {
        GradeSubmission submission = selectedSubmission();
        if (submission == null) {
            showStatus("请先选择一份待审核成绩单", VCampusTheme.DANGER);
            return;
        }
        if (!CourseUiSupport.confirmHighImpact(this, "确认审核通过",
                "确定通过教学班“" + submission.getOfferingId() + "”的成绩单吗？",
                "通过后将写入学生正式成绩，教师不能继续直接修改这份成绩单。")) {
            return;
        }
        request(service -> service.approveGradeSubmission(session.getToken(),
                submission.getSubmissionId(), null), response -> {
                    if (response.getStatusCode() != StatusCode.OK) {
                        showFailure(response);
                        return;
                    }
                    showStatus("成绩单已审核通过，正式成绩已发布", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(() -> loadPending());
                });
    }

    private void returnForRevision() {
        GradeSubmission submission = selectedSubmission();
        if (submission == null) {
            showStatus("请先选择一份待审核成绩单", VCampusTheme.DANGER);
            return;
        }
        String remark = JOptionPane.showInputDialog(this, "请填写退回原因：", "退回修改",
                JOptionPane.WARNING_MESSAGE);
        if (remark == null) {
            return;
        }
        if (remark.trim().isEmpty()) {
            showStatus("退回原因不能为空", VCampusTheme.DANGER);
            return;
        }
        if (!CourseUiSupport.confirmHighImpact(this, "确认退回成绩单",
                "确定退回教学班“" + submission.getOfferingId() + "”的成绩单吗？",
                "任课教师需要根据退回原因修改成绩后重新提交审核。")) {
            return;
        }
        request(service -> service.returnGradeSubmission(session.getToken(),
                submission.getSubmissionId(), remark.trim()), response -> {
                    if (response.getStatusCode() != StatusCode.OK) {
                        showFailure(response);
                        return;
                    }
                    showStatus("成绩单已退回任课教师修改", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(() -> loadPending());
                });
    }

    private GradeSubmission selectedSubmission() {
        int row = submissionTable.getSelectedRow();
        return row >= 0 && row < submissions.size() ? submissions.get(row) : null;
    }

    private void updateSelectionHint() {
        GradeSubmission submission = selectedSubmission();
        if (submission == null) {
            selectionHint.setText("请选择一份待审核成绩单，查看成绩快照后再作出处理。 ");
            return;
        }
        selectionHint.setText("已选择教学班“" + submission.getOfferingId()
                + "”。请先核验成绩快照，再选择审核通过或退回修改。 ");
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

    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        String message = response.getPayload() instanceof String ? (String) response.getPayload() : fallback;
        showStatus(message, VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(status, message, color);
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private JLabel sectionHint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(VCampusTheme.MUTED);
        return label;
    }

    private void updateInteractiveState() {
        boolean selectable = !requestInProgress && selectedSubmission() != null;
        refreshButton.setEnabled(!requestInProgress);
        detailButton.setEnabled(selectable);
        auditButton.setEnabled(selectable);
        approveButton.setEnabled(selectable);
        returnButton.setEnabled(selectable);
        submissionTable.setEnabled(!requestInProgress);
        detailTable.setEnabled(!requestInProgress);
        auditTable.setEnabled(!requestInProgress);
    }

    private static String format(LocalDateTime value) {
        return value == null ? "-" : TIME_FORMAT.format(value);
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

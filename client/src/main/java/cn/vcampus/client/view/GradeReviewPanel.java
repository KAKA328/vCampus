package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmission;
import cn.vcampus.course.GradeSubmissionAuditRecord;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.TeachingGradeDraft;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** 教务端成绩审核工作区：待审核列表、详情和已审核历史。 */
final class GradeReviewPanel extends JPanel {
    private static final String PENDING_CARD = "pending";
    private static final String DETAIL_CARD = "detail";
    private static final String HISTORY_CARD = "history";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel submissionModel = new BatchTableModel(new Object[] {
            "提交单编号", "教学班", "任课教师", "提交时间" });
    private final BatchTableModel historyModel = new BatchTableModel(new Object[] {
            "提交单编号", "教学班", "任课教师", "审核结果", "审核时间", "审核意见" });
    private final BatchTableModel detailModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "专业", "班级", "选课类别", "成绩" });
    private final BatchTableModel auditModel = new BatchTableModel(new Object[] {
            "操作", "操作人", "操作时间", "意见" });
    private final JTable submissionTable = new JTable(submissionModel);
    private final JTable historyTable = new JTable(historyModel);
    private final JTable detailTable = new JTable(detailModel);
    private final JTable auditTable = new JTable(auditModel);
    private final List<GradeSubmission> pendingSubmissions = new ArrayList<GradeSubmission>();
    private final List<GradeSubmission> historySubmissions = new ArrayList<GradeSubmission>();
    private final JPanel cards = new JPanel(new CardLayout());
    private final JLabel status = new JLabel();
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailHint = new JLabel();
    private final JButton refreshButton = new JButton("刷新待审核列表");
    private final JButton historyButton = new JButton("已审核成绩单");
    private final JButton pendingDetailButton = new JButton("查看详情");
    private final JButton refreshHistoryButton = new JButton("刷新历史");
    private final JButton backToPendingButton = new JButton("返回待审核列表");
    private final JButton historyDetailButton = new JButton("查看详情");
    private final JButton backButton = new JButton("返回列表");
    private final JButton approveButton = new JButton("审核通过");
    private final JButton returnButton = new JButton("退回修改");
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private GradeSubmission currentSubmission;
    private String returnCard = PENDING_CARD;
    private boolean requestInProgress;

    GradeReviewPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) throw new IllegalArgumentException("host and session must not be null");
        this.host = host.trim(); this.port = port; this.session = session; build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(12))); setOpaque(false);
        configureTable(submissionTable, 220, 150, 140, 170);
        configureTable(historyTable, 220, 150, 140, 110, 160, 280);
        configureTable(detailTable, 120, 110, 170, 110, 100, 80);
        configureTable(auditTable, 110, 130, 150, 300);
        refreshButton.addActionListener(e -> loadPending());
        historyButton.addActionListener(e -> showHistory());
        pendingDetailButton.addActionListener(e -> openSelectedPending());
        refreshHistoryButton.addActionListener(e -> loadHistory());
        backToPendingButton.addActionListener(e -> returnToPending());
        historyDetailButton.addActionListener(e -> openSelectedHistory());
        backButton.addActionListener(e -> showCard(returnCard));
        approveButton.addActionListener(e -> approve());
        returnButton.addActionListener(e -> returnForRevision());
        submissionTable.getSelectionModel().addListSelectionListener(e -> updateInteractiveState());
        historyTable.getSelectionModel().addListSelectionListener(e -> updateInteractiveState());
        cards.setOpaque(false);
        cards.add(pendingPage(), PENDING_CARD);
        cards.add(detailPage(), DETAIL_CARD);
        cards.add(historyPage(), HISTORY_CARD);
        add(cards, BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        showStatus("正在自动加载待审核成绩单", VCampusTheme.MUTED);
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadPending);
    }

    private JPanel pendingPage() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12))); page.setOpaque(false);
        JPanel toolbar = new JPanel(new BorderLayout(0, UiMetrics.px(8))); VCampusTheme.panel(toolbar);
        toolbar.add(sectionTitle("待审核成绩单"), BorderLayout.NORTH);
        JPanel controls = new JPanel(new BorderLayout(0, UiMetrics.px(4))); controls.setOpaque(false);
        controls.add(sectionHint("选择成绩单后进入详情，核验成绩快照与完整审核记录，再作出处理。"), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4))); buttons.setOpaque(false);
        addSecondary(buttons, refreshButton); addSecondary(buttons, historyButton); addPrimary(buttons, pendingDetailButton);
        controls.add(buttons, BorderLayout.CENTER); toolbar.add(controls, BorderLayout.CENTER);
        page.add(toolbar, BorderLayout.NORTH);
        page.add(card("待审核提交单", "仅展示等待教务处理的成绩单。", submissionTable), BorderLayout.CENTER);
        return page;
    }

    private JPanel historyPage() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12))); page.setOpaque(false);
        JPanel toolbar = new JPanel(new BorderLayout(0, UiMetrics.px(8))); VCampusTheme.panel(toolbar);
        toolbar.add(sectionTitle("已审核成绩单"), BorderLayout.NORTH);
        JPanel controls = new JPanel(new BorderLayout(0, UiMetrics.px(4))); controls.setOpaque(false);
        controls.add(sectionHint("保留审核通过与退回修改的历史结果，可进入详情查看当时的成绩版本和流转记录。"), BorderLayout.NORTH);
        JPanel buttons = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4))); buttons.setOpaque(false);
        addSecondary(buttons, backToPendingButton); addSecondary(buttons, refreshHistoryButton);
        addPrimary(buttons, historyDetailButton);
        controls.add(buttons, BorderLayout.CENTER); toolbar.add(controls, BorderLayout.CENTER);
        page.add(toolbar, BorderLayout.NORTH);
        page.add(card("审核历史", "选择一行后查看详情；历史成绩单不再提供审核操作。", historyTable), BorderLayout.CENTER);
        return page;
    }

    private JPanel detailPage() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12))); page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(8))); VCampusTheme.panel(header);
        detailTitle.setFont(VCampusTheme.font(Font.BOLD, 16)); detailTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        detailHint.setForeground(VCampusTheme.MUTED);
        JPanel text = new JPanel(new BorderLayout(0, UiMetrics.px(2))); text.setOpaque(false);
        text.add(detailTitle, BorderLayout.NORTH); text.add(detailHint, BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4))); buttons.setOpaque(false);
        addSecondary(buttons, backButton); addPrimary(buttons, approveButton); addSecondary(buttons, returnButton);
        header.add(text, BorderLayout.NORTH); header.add(buttons, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                card("成绩明细", "成绩来自教师提交的冻结版本。", detailTable),
                card("审核记录", "完整保留提交、退回和审核通过的流转记录。", auditTable));
        split.setBorder(null); split.setOpaque(false); split.setResizeWeight(0.58); split.setDividerSize(UiMetrics.px(10));
        page.add(header, BorderLayout.NORTH); page.add(split, BorderLayout.CENTER);
        return page;
    }

    private JPanel card(String titleText, String hintText, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10))); VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3))); header.setOpaque(false);
        header.add(sectionTitle(titleText), BorderLayout.NORTH); header.add(sectionHint(hintText), BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH); panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER); return panel;
    }

    private void loadPending() {
        request(service -> service.pendingGradeSubmissions(session.getToken()), response -> {
            if (!requireList(response, "待审核成绩单")) return;
            pendingSubmissions.clear(); List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof GradeSubmission) {
                GradeSubmission submission = (GradeSubmission) item; pendingSubmissions.add(submission);
                rows.add(new Object[] { submission.getSubmissionId(), submission.getOfferingId(), submission.getTeacherDisplayName(), format(submission.getUpdatedAt()) });
            }
            submissionModel.replaceRows(rows);
            showStatus(rows.isEmpty() ? "当前没有待审核成绩单" : "已加载 " + rows.size() + " 份待审核成绩单", rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void showHistory() { showCard(HISTORY_CARD); loadHistory(); }
    private void returnToPending() {
        showCard(PENDING_CARD);
        SwingUtilities.invokeLater(this::loadPending);
    }
    private void loadHistory() {
        request(service -> service.gradeReviewHistory(session.getToken()), response -> {
            if (!requireList(response, "已审核成绩单")) return;
            historySubmissions.clear(); List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof GradeSubmission) {
                GradeSubmission submission = (GradeSubmission) item; historySubmissions.add(submission);
                rows.add(new Object[] { submission.getSubmissionId(), submission.getOfferingId(), submission.getTeacherDisplayName(),
                        submissionStatusText(submission.getStatus()), format(submission.getReviewedAt()), safe(submission.getReviewRemark()) });
            }
            historyModel.replaceRows(rows);
            showStatus(rows.isEmpty() ? "当前没有已审核成绩单" : "已加载 " + rows.size() + " 份审核历史", rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void openSelectedPending() { openDetail(selected(submissionTable, pendingSubmissions), PENDING_CARD); }
    private void openSelectedHistory() { openDetail(selected(historyTable, historySubmissions), HISTORY_CARD); }
    private void openDetail(GradeSubmission submission, String source) {
        if (submission == null) { showStatus("请先选择一份成绩单", VCampusTheme.DANGER); return; }
        currentSubmission = submission; returnCard = source;
        request(service -> service.gradeReviewDetail(session.getToken(), submission.getSubmissionId()), response -> {
            if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof TeachingGradeDraft)) { showFailure(response); return; }
            renderDetail((TeachingGradeDraft) response.getPayload());
            showCard(DETAIL_CARD);
            SwingUtilities.invokeLater(this::loadAudit);
        });
    }

    private void renderDetail(TeachingGradeDraft draft) {
        Map<String, GradeEntry> entries = new LinkedHashMap<String, GradeEntry>();
        for (GradeEntry entry : draft.getEntries()) entries.put(entry.getStudentId(), entry);
        List<Object[]> rows = new ArrayList<Object[]>();
        for (TeachingRosterEntry student : draft.getRoster().getStudents()) {
            GradeEntry entry = entries.get(student.getStudentId());
            rows.add(new Object[] { student.getStudentId(), student.getStudentName(), safe(student.getMajorName()), safe(student.getClassId()),
                    student.getSelectionType().getDisplayName(), entry == null ? "未录入" : Integer.valueOf(entry.getScore()) });
        }
        detailModel.replaceRows(rows);
        GradeSubmission submission = draft.getSubmission(); currentSubmission = submission;
        String version = draft.getReviewVersionNo() == null ? "" : " · 审核版本 " + draft.getReviewVersionNo();
        detailTitle.setText("成绩单详情：" + submission.getOfferingId());
        detailHint.setText(submissionStatusText(submission.getStatus()) + version + " · " + rows.size() + " 名有效选课学生");
        updateInteractiveState();
    }

    private void loadAudit() {
        if (currentSubmission == null) return;
        String submissionId = currentSubmission.getSubmissionId();
        request(service -> service.gradeReviewAudit(session.getToken(), submissionId), response -> {
            if (!requireList(response, "审核记录")) return;
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) if (item instanceof GradeSubmissionAuditRecord) {
                GradeSubmissionAuditRecord record = (GradeSubmissionAuditRecord) item;
                rows.add(new Object[] { auditActionText(record.getAction().name()), record.getActorId(), format(record.getOccurredAt()), safe(record.getRemark()) });
            }
            auditModel.replaceRows(rows);
        });
    }

    private void approve() {
        if (!isCurrentPending()) return;
        if (!CourseUiSupport.confirmHighImpact(this, "确认审核通过", "确定通过教学班“" + currentSubmission.getOfferingId() + "”的成绩单吗？", "通过后将写入学生正式成绩，教师不能继续直接修改这份成绩单。")) return;
        request(service -> service.approveGradeSubmission(session.getToken(), currentSubmission.getSubmissionId(), null), response -> {
            if (response.getStatusCode() != StatusCode.OK) { showFailure(response); return; }
            showStatus("成绩单已审核通过，正式成绩已发布", VCampusTheme.SUCCESS);
            currentSubmission = null; showCard(PENDING_CARD); SwingUtilities.invokeLater(this::loadPending);
        });
    }

    private void returnForRevision() {
        if (!isCurrentPending()) return;
        String remark = CourseUiSupport.promptRequiredText(this, "退回修改", "请填写退回原因：");
        if (remark == null) return;
        if (!CourseUiSupport.confirmHighImpact(this, "确认退回成绩单", "确定退回教学班“" + currentSubmission.getOfferingId() + "”的成绩单吗？", "任课教师需要根据退回原因修改成绩后重新提交审核。")) return;
        request(service -> service.returnGradeSubmission(session.getToken(), currentSubmission.getSubmissionId(), remark.trim()), response -> {
            if (response.getStatusCode() != StatusCode.OK) { showFailure(response); return; }
            showStatus("成绩单已退回任课教师修改", VCampusTheme.SUCCESS);
            currentSubmission = null; showCard(PENDING_CARD); SwingUtilities.invokeLater(this::loadPending);
        });
    }

    private boolean isCurrentPending() {
        if (currentSubmission != null && currentSubmission.getStatus() == GradeSubmissionStatus.PENDING_REVIEW) return true;
        showStatus("仅待审核成绩单可执行审核操作", VCampusTheme.DANGER); return false;
    }
    private void showCard(String name) { ((CardLayout) cards.getLayout()).show(cards, name); updateInteractiveState(); }
    private static GradeSubmission selected(JTable table, List<GradeSubmission> source) { int row = table.getSelectedRow(); return row >= 0 && row < source.size() ? source.get(row) : null; }
    private void request(Request request, Response response) {
        if (requestInProgress) return;
        int requestId = requestLifecycle.begin(); requestInProgress = true; updateInteractiveState(); showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception { try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); } }
            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
                try { response.handle(get()); } catch (Exception failure) { showStatus("无法连接选课服务器", VCampusTheme.DANGER); }
                finally { requestInProgress = false; updateInteractiveState(); }
            }
        }.execute();
    }
    private boolean requireList(Message response, String target) { if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) return true; showFailure(response); return false; }
    private void showFailure(Message response) { String fallback = "服务器未能完成操作：" + response.getStatusCode(); showStatus(response.getPayload() instanceof String ? (String) response.getPayload() : fallback, VCampusTheme.DANGER); }
    private void showStatus(String message, Color color) { CourseUiSupport.showStatus(status, message, color); }
    private static void configureTable(JTable table, int... widths) { VCampusTheme.table(table); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); for (int index = 0; index < widths.length; index++) table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(widths[index])); }
    private JLabel sectionTitle(String text) { JLabel label = new JLabel(text); label.setFont(VCampusTheme.font(Font.BOLD, 16)); label.setForeground(VCampusTheme.PRIMARY_DARK); return label; }
    private JLabel sectionHint(String text) { JLabel label = new JLabel(text); label.setForeground(VCampusTheme.MUTED); return label; }
    private static void addPrimary(JPanel parent, JButton button) { VCampusTheme.primaryButton(button); parent.add(button); }
    private static void addSecondary(JPanel parent, JButton button) { VCampusTheme.secondaryButton(button); parent.add(button); }
    private static String submissionStatusText(GradeSubmissionStatus value) { return value == GradeSubmissionStatus.PENDING_REVIEW ? "待审核" : value == GradeSubmissionStatus.APPROVED ? "已通过" : value == GradeSubmissionStatus.RETURNED ? "已退回" : "草稿"; }
    private static String auditActionText(String action) { return "SUBMITTED".equals(action) ? "提交审核" : "APPROVED".equals(action) ? "审核通过" : "RETURNED".equals(action) ? "退回修改" : action; }
    private void updateInteractiveState() {
        boolean pendingSelected = !requestInProgress && selected(submissionTable, pendingSubmissions) != null;
        boolean historySelected = !requestInProgress && selected(historyTable, historySubmissions) != null;
        boolean pendingDetail = !requestInProgress && currentSubmission != null && currentSubmission.getStatus() == GradeSubmissionStatus.PENDING_REVIEW;
        refreshButton.setEnabled(!requestInProgress); historyButton.setEnabled(!requestInProgress); pendingDetailButton.setEnabled(pendingSelected);
        refreshHistoryButton.setEnabled(!requestInProgress); backToPendingButton.setEnabled(!requestInProgress);
        historyDetailButton.setEnabled(historySelected); backButton.setEnabled(!requestInProgress);
        approveButton.setEnabled(pendingDetail); returnButton.setEnabled(pendingDetail);
        submissionTable.setEnabled(!requestInProgress); historyTable.setEnabled(!requestInProgress); detailTable.setEnabled(!requestInProgress); auditTable.setEnabled(!requestInProgress);
    }
    private static String format(LocalDateTime value) { return value == null ? "-" : TIME_FORMAT.format(value); }
    private static String safe(String value) { return value == null || value.trim().isEmpty() ? "-" : value; }
    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

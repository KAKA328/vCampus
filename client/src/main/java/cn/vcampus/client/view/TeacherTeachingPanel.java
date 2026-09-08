package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeSubmissionStatus;
import cn.vcampus.course.TeachingOffering;
import cn.vcampus.course.TeachingGradeDraft;
import cn.vcampus.course.TeachingRoster;
import cn.vcampus.course.TeachingRosterEntry;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private static final int[] ROSTER_COLUMN_WIDTHS = { 130, 120, 180, 130, 130, 90 };

    private final String host;
    private final int port;
    private final Session session;
    private final JTextField term = new JTextField("2026-2027-1", 12);
    private final JButton refreshButton = new JButton("查询我的教学班");
    private final JButton viewRosterButton = new JButton("查看学生名单");
    private final JButton openDraftButton = new JButton("打开成绩草稿");
    private final JTextField score = new JTextField(5);
    private final JButton saveGradeButton = new JButton("保存所选学生成绩");
    private final JButton submitGradesButton = new JButton("提交教务审核");
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "课程编号", "课程名称", "学分", "教学班", "上课时间", "地点", "教学班状态" });
    private final JTable table = new JTable(tableModel);
    private final BatchTableModel rosterModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "专业", "班级", "选课类别", "成绩" });
    private final JTable rosterTable = new JTable(rosterModel);
    private final List<TeachingOffering> offerings = new ArrayList<TeachingOffering>();
    private final List<TeachingRosterEntry> rosterStudents = new ArrayList<TeachingRosterEntry>();
    private final JLabel rosterTitle = new JLabel("学生名单");
    private final JLabel status = new JLabel();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;
    private TeachingGradeDraft currentDraft;

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
        VCampusTheme.field(score);
        VCampusTheme.secondaryButton(refreshButton);
        VCampusTheme.primaryButton(viewRosterButton);
        VCampusTheme.secondaryButton(openDraftButton);
        VCampusTheme.primaryButton(saveGradeButton);
        VCampusTheme.primaryButton(submitGradesButton);
        refreshButton.addActionListener(e -> loadOfferings());
        viewRosterButton.addActionListener(e -> loadRoster());
        openDraftButton.addActionListener(e -> openDraft());
        saveGradeButton.addActionListener(e -> saveSelectedGrade());
        submitGradesButton.addActionListener(e -> submitGrades());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateInteractiveState();
            }
        });
        rosterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillScoreFromSelection();
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
        JLabel subtitle = new JLabel("查看本人教学班和名单，维护成绩草稿后提交教务审核。 ");
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
        query.add(openDraftButton);
        query.add(new JLabel("分数"));
        query.add(score);
        query.add(saveGradeButton);
        query.add(submitGradesButton);
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
            clearRosterAndDraft();
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
                    currentDraft = null;
                    renderRoster((TeachingRoster) response.getPayload(), null);
                    int count = rosterStudents.size();
                    showStatus(count == 0 ? "该教学班当前没有有效选课学生" : "已加载 "
                            + count + " 名学生", count == 0 ? VCampusTheme.MUTED
                                    : VCampusTheme.SUCCESS);
                });
    }

    private void openDraft() {
        TeachingOffering selected = selectedOffering();
        if (selected == null) {
            showStatus("请先选择一个教学班", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.openGradeDraft(session.getToken(),
                selected.getOffering().getOfferingId()), response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof TeachingGradeDraft)) {
                        showFailure(response);
                        return;
                    }
                    currentDraft = (TeachingGradeDraft) response.getPayload();
                    renderRoster(currentDraft.getRoster(), currentDraft);
                    GradeSubmissionStatus draftStatus = currentDraft.getSubmission().getStatus();
                    showStatus("已打开成绩草稿，当前状态：" + draftStatusText(draftStatus),
                            draftStatus == GradeSubmissionStatus.APPROVED ? VCampusTheme.MUTED
                                    : VCampusTheme.SUCCESS);
                });
    }

    private void saveSelectedGrade() {
        if (!canEditDraft()) {
            showStatus(currentDraft == null ? "请先打开成绩草稿" : "已通过的成绩单必须先由教务退回",
                    VCampusTheme.DANGER);
            return;
        }
        int row = rosterTable.getSelectedRow();
        if (row < 0 || row >= rosterStudents.size()) {
            showStatus("请先选择一名学生", VCampusTheme.DANGER);
            return;
        }
        int selectedScore;
        try {
            selectedScore = Integer.parseInt(score.getText().trim());
            if (selectedScore < 0 || selectedScore > 100) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException invalid) {
            showStatus("分数必须是 0 到 100 的整数", VCampusTheme.DANGER);
            return;
        }
        String studentId = rosterStudents.get(row).getStudentId();
        request(service -> service.saveGradeEntry(session.getToken(),
                currentDraft.getSubmission().getOfferingId(), studentId, selectedScore), response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof TeachingGradeDraft)) {
                        showFailure(response);
                        return;
                    }
                    currentDraft = (TeachingGradeDraft) response.getPayload();
                    renderRoster(currentDraft.getRoster(), currentDraft);
                    showStatus("已保存 " + studentId + " 的成绩；当前状态："
                            + draftStatusText(currentDraft.getSubmission().getStatus()),
                            VCampusTheme.SUCCESS);
                });
    }

    private void submitGrades() {
        if (!canEditDraft()) {
            showStatus(currentDraft == null ? "请先打开成绩草稿" : "已通过的成绩单必须先由教务退回",
                    VCampusTheme.DANGER);
            return;
        }
        int missing = missingGradeCount();
        if (missing > 0) {
            showStatus("还有 " + missing + " 名有效选课学生未录入成绩，不能提交审核", VCampusTheme.DANGER);
            return;
        }
        request(service -> service.submitGradesForReview(session.getToken(),
                currentDraft.getSubmission().getOfferingId()), response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof TeachingGradeDraft)) {
                        showFailure(response);
                        return;
                    }
                    currentDraft = (TeachingGradeDraft) response.getPayload();
                    renderRoster(currentDraft.getRoster(), currentDraft);
                    showStatus("成绩已提交教务审核。后续修改后需再次提交，教务将审核最新版本。",
                            VCampusTheme.SUCCESS);
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
        openDraftButton.setEnabled(interactive && selectedOffering() != null);
        saveGradeButton.setEnabled(interactive && canEditDraft()
                && rosterTable.getSelectedRow() >= 0);
        submitGradesButton.setEnabled(interactive && canEditDraft());
        term.setEnabled(interactive);
        table.setEnabled(interactive);
        rosterTable.setEnabled(interactive);
        score.setEnabled(interactive && canEditDraft());
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private void renderRoster(TeachingRoster roster, TeachingGradeDraft draft) {
        rosterStudents.clear();
        rosterStudents.addAll(roster.getStudents());
        Map<String, GradeEntry> entriesByStudent = new LinkedHashMap<String, GradeEntry>();
        if (draft != null) {
            for (GradeEntry entry : draft.getEntries()) {
                entriesByStudent.put(entry.getStudentId(), entry);
            }
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        for (TeachingRosterEntry student : rosterStudents) {
            GradeEntry entry = entriesByStudent.get(student.getStudentId());
            rows.add(new Object[] { student.getStudentId(), student.getStudentName(),
                    safe(student.getMajorName()), safe(student.getClassId()),
                    student.getSelectionType().getDisplayName(),
                    entry == null ? "未录入" : Integer.valueOf(entry.getScore()) });
        }
        rosterModel.replaceRows(rows);
        String draftStatus = draft == null ? "" : "，草稿状态："
                + draftStatusText(draft.getSubmission().getStatus());
        rosterTitle.setText("学生名单：" + roster.getTeachingOffering().getOffering().getOfferingId()
                + "（" + rows.size() + " 人" + draftStatus + "）");
        score.setText("");
    }

    private void clearRosterAndDraft() {
        currentDraft = null;
        rosterStudents.clear();
        rosterModel.replaceRows(new ArrayList<Object[]>());
        rosterTitle.setText("学生名单与成绩草稿");
        score.setText("");
    }

    private void fillScoreFromSelection() {
        if (currentDraft == null) {
            score.setText("");
            return;
        }
        int row = rosterTable.getSelectedRow();
        if (row < 0 || row >= rosterStudents.size()) {
            return;
        }
        String studentId = rosterStudents.get(row).getStudentId();
        for (GradeEntry entry : currentDraft.getEntries()) {
            if (studentId.equals(entry.getStudentId())) {
                score.setText(String.valueOf(entry.getScore()));
                return;
            }
        }
        score.setText("");
    }

    private boolean canEditDraft() {
        return currentDraft != null
                && currentDraft.getSubmission().getStatus() != GradeSubmissionStatus.APPROVED;
    }

    private int missingGradeCount() {
        if (currentDraft == null) {
            return 0;
        }
        Map<String, GradeEntry> entriesByStudent = new LinkedHashMap<String, GradeEntry>();
        for (GradeEntry entry : currentDraft.getEntries()) {
            entriesByStudent.put(entry.getStudentId(), entry);
        }
        int missing = 0;
        for (TeachingRosterEntry student : rosterStudents) {
            if (!entriesByStudent.containsKey(student.getStudentId())) {
                missing++;
            }
        }
        return missing;
    }

    private static String draftStatusText(GradeSubmissionStatus value) {
        if (value == GradeSubmissionStatus.DRAFT) {
            return "草稿";
        }
        if (value == GradeSubmissionStatus.PENDING_REVIEW) {
            return "待审核";
        }
        if (value == GradeSubmissionStatus.APPROVED) {
            return "已通过";
        }
        return "已退回修改";
    }

    private interface Request {
        Message run(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface Response {
        void handle(Message response);
    }
}

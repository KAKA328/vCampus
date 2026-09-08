package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.CourseGradeImportV2Command;
import cn.vcampus.course.GradeEntry;
import cn.vcampus.course.GradeImportResult;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

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
    private final JButton chooseGradeFileButton = new JButton("选择成绩文件");
    private final JButton importGradesButton = new JButton("导入文件成绩");
    private final JLabel selectedGradeFile = new JLabel("未选择成绩文件（支持 CSV、XLS、XLSX）");
    private final BatchTableModel tableModel = new BatchTableModel(new Object[] {
            "课程编号", "课程名称", "学分", "教学班", "上课时间", "地点", "教学班状态" });
    private final JTable table = new JTable(tableModel);
    private final BatchTableModel rosterModel = new BatchTableModel(new Object[] {
            "学号", "姓名", "专业", "班级", "选课类别", "成绩" });
    private final JTable rosterTable = new JTable(rosterModel);
    private final List<TeachingOffering> offerings = new ArrayList<TeachingOffering>();
    private final List<TeachingRosterEntry> rosterStudents = new ArrayList<TeachingRosterEntry>();
    private final JLabel rosterTitle = new JLabel("学生名单与成绩草稿");
    private final JLabel rosterHint = new JLabel("选择一个教学班后，可先查看名单或打开成绩草稿。 ");
    private final JLabel status = new JLabel();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;
    private TeachingGradeDraft currentDraft;
    private Path gradeImportFile;

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
        VCampusTheme.secondaryButton(chooseGradeFileButton);
        VCampusTheme.primaryButton(importGradesButton);
        refreshButton.addActionListener(e -> loadOfferings());
        viewRosterButton.addActionListener(e -> loadRoster());
        openDraftButton.addActionListener(e -> openDraft());
        saveGradeButton.addActionListener(e -> saveSelectedGrade());
        submitGradesButton.addActionListener(e -> submitGrades());
        chooseGradeFileButton.addActionListener(e -> chooseGradeFile());
        importGradesButton.addActionListener(e -> importGrades());
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
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(16)));
        panel.setOpaque(false);
        panel.add(termCard(), BorderLayout.NORTH);
        panel.add(workspace(), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    /** 第一步只处理学期与教学班列表，成绩操作在选中教学班后才出现。 */
    private JPanel termCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = new JLabel("第 1 步：查询本人教学班");
        title.setFont(VCampusTheme.font(Font.BOLD, 15));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel hint = new JLabel("选择学期后加载系统分配给你的教学班。 ");
        hint.setForeground(VCampusTheme.MUTED);
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        fields.setOpaque(false);
        fields.add(new JLabel("学期"));
        fields.add(term);
        fields.add(refreshButton);
        card.add(title, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
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
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        header.setOpaque(false);
        JLabel title = new JLabel("第 2 步：选择教学班");
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel hint = new JLabel("选择一行后，可查看学生名单，或打开该教学班的成绩草稿。 ");
        hint.setForeground(VCampusTheme.MUTED);
        JPanel titleBlock = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        titleBlock.setOpaque(false);
        titleBlock.add(title, BorderLayout.NORTH);
        titleBlock.add(hint, BorderLayout.SOUTH);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(2)));
        actions.setOpaque(false);
        actions.add(viewRosterButton);
        actions.add(openDraftButton);
        header.add(titleBlock, BorderLayout.NORTH);
        header.add(actions, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel rosterCard() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        rosterTitle.setFont(VCampusTheme.font(Font.BOLD, 16));
        rosterTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        rosterHint.setForeground(VCampusTheme.MUTED);
        header.add(rosterTitle, BorderLayout.NORTH);
        header.add(rosterHint, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(rosterTable), BorderLayout.CENTER);
        panel.add(gradeWorkflow(), BorderLayout.SOUTH);
        return panel;
    }

    /** 成绩动作紧贴名单，且按“手工录入、批量导入、提交审核”顺序展示。 */
    private JPanel gradeWorkflow() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        panel.setOpaque(false);
        JLabel title = new JLabel("第 3 步：维护并提交成绩");
        title.setFont(VCampusTheme.font(Font.BOLD, 15));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        actions.add(new JLabel("选中学生成绩"));
        actions.add(score);
        actions.add(saveGradeButton);
        actions.add(chooseGradeFileButton);
        actions.add(importGradesButton);
        actions.add(submitGradesButton);
        selectedGradeFile.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(actions, BorderLayout.CENTER);
        panel.add(selectedGradeFile, BorderLayout.SOUTH);
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
                    clearGradeImportFile();
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
        if (!CourseUiSupport.confirmHighImpact(this, "确认提交成绩审核",
                "确定将当前教学班成绩提交给教务审核吗？",
                "教务将以本次提交生成审核快照；后续修改成绩后需要重新提交。")) {
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

    /** 选择由服务器解析的成绩源文件；客户端只限制格式和大小，不自行修改成绩。 */
    private void chooseGradeFile() {
        if (!canEditDraft()) {
            showStatus(currentDraft == null ? "请先打开成绩草稿" : "已通过的成绩单必须先由教务退回",
                    VCampusTheme.DANGER);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择教学班成绩文件");
        chooser.setFileFilter(new FileNameExtensionFilter("成绩文件 (*.csv, *.xls, *.xlsx)",
                "csv", "xls", "xlsx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        try {
            long bytes = Files.size(selected);
            if (bytes < 1 || bytes > CourseGradeImportV2Command.MAX_FILE_BYTES) {
                showStatus("成绩文件大小必须在 1 字节到 5 MB 之间", VCampusTheme.DANGER);
                return;
            }
            gradeImportFile = selected;
            selectedGradeFile.setText(selected.getFileName() + "（" + bytes + " 字节，待导入）");
            showStatus("文件已选择。服务器会校验“学号、成绩”列及本教学班名单。", VCampusTheme.SUCCESS);
            updateInteractiveState();
        } catch (IOException failure) {
            showStatus("无法读取所选成绩文件", VCampusTheme.DANGER);
        }
    }

    private void importGrades() {
        if (!canEditDraft()) {
            showStatus(currentDraft == null ? "请先打开成绩草稿" : "已通过的成绩单必须先由教务退回",
                    VCampusTheme.DANGER);
            return;
        }
        if (gradeImportFile == null) {
            showStatus("请先选择 CSV、XLS 或 XLSX 成绩文件", VCampusTheme.DANGER);
            return;
        }
        final byte[] content;
        try {
            content = Files.readAllBytes(gradeImportFile);
        } catch (IOException failure) {
            showStatus("无法读取所选成绩文件，请重新选择", VCampusTheme.DANGER);
            return;
        }
        final String fileName = gradeImportFile.getFileName().toString();
        request(service -> service.importGrades(session.getToken(),
                currentDraft.getSubmission().getOfferingId(), fileName, content), response -> {
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof GradeImportResult)) {
                        showFailure(response);
                        return;
                    }
                    GradeImportResult result = (GradeImportResult) response.getPayload();
                    currentDraft = result.getDraft();
                    renderRoster(currentDraft.getRoster(), currentDraft);
                    showStatus("已从 “" + fileName + "” 导入 " + result.getImportedCount()
                            + " 条成绩。确认全班完整后可提交教务审核。", VCampusTheme.SUCCESS);
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
        CourseUiSupport.showStatus(status, message, color);
    }

    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        refreshButton.setEnabled(interactive);
        viewRosterButton.setEnabled(interactive && selectedOffering() != null);
        openDraftButton.setEnabled(interactive && selectedOffering() != null);
        saveGradeButton.setEnabled(interactive && canEditDraft()
                && rosterTable.getSelectedRow() >= 0);
        submitGradesButton.setEnabled(interactive && canEditDraft());
        chooseGradeFileButton.setEnabled(interactive && canEditDraft());
        importGradesButton.setEnabled(interactive && canEditDraft() && gradeImportFile != null);
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
        rosterHint.setText(draft == null
                ? "当前为名单查看模式；打开成绩草稿后，才能录入、导入或提交成绩。"
                : "已打开成绩草稿。每名学生都有成绩后，才可以提交教务审核。 ");
        score.setText("");
    }

    private void clearRosterAndDraft() {
        currentDraft = null;
        rosterStudents.clear();
        rosterModel.replaceRows(new ArrayList<Object[]>());
        rosterTitle.setText("学生名单与成绩草稿");
        rosterHint.setText("选择一个教学班后，可先查看名单或打开成绩草稿。 ");
        score.setText("");
        clearGradeImportFile();
    }

    private void clearGradeImportFile() {
        gradeImportFile = null;
        selectedGradeFile.setText("未选择成绩文件（支持 CSV、XLS、XLSX）");
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
        TeachingOffering selected = selectedOffering();
        return currentDraft != null && selected != null
                && currentDraft.getSubmission().getOfferingId().equals(selected.getOffering().getOfferingId())
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

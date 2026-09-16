package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.CreditFormat;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.AcademicAdminOverviewV1Command;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import cn.vcampus.student.AcademicAdminCommandV2;
import cn.vcampus.student.AcademicAssessment;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.CreditSummary;
import cn.vcampus.student.GraduationCreditRequirement;
import cn.vcampus.student.GraduationReviewOverview;
import cn.vcampus.student.StudentRecord;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

/** Academic-administration directory and single-student graduation workspace. */
final class AcademicAdministrationPanel extends JPanel {
    interface Loader { Message load(AcademicAdminCommandV2 command) throws Exception; }
    interface OverviewLoader { Message load(AcademicAdminOverviewV1Command command) throws Exception; }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Loader loader;
    private final OverviewLoader overviewLoader;
    private final String token;
    private final JTabbedPane operations = new JTabbedPane();

    private final JTextField filter = new JTextField(18);
    private final BatchTableModel directoryModel = new BatchTableModel(new Object[] {"信息"});
    private final JTable directoryTable = new JTable(directoryModel);
    private final JLabel directoryTitle = new JLabel("查询结果 · 尚未加载");
    private final JLabel directoryStatus = new JLabel("选择“全部学生”或“全部教师”。");
    private final JButton allStudents = new JButton("全部学生");
    private final JButton allTeachers = new JButton("全部教师");

    private final JTextField reviewFilter = new JTextField(14);
    private final BatchTableModel studentModel = new BatchTableModel(
            new Object[] {"学号", "姓名", "专业", "入学年", "状态"});
    private final JTable studentTable = new JTable(studentModel);
    private final JLabel studentCount = new JLabel("尚未加载学生");
    private final JButton refreshStudents = new JButton("刷新");

    private final JLabel studentTitle = new JLabel("请选择学生");
    private final JLabel studentMeta = new JLabel("从左侧名单选择一名学生后开始办理");
    private final JLabel earnedValue = metricValue();
    private final JLabel requiredValue = metricValue();
    private final JLabel shortfallValue = metricValue();
    private final JLabel retakeValue = metricValue();
    private final JLabel planValue = metricValue();
    private final JButton courseDetails = new JButton("查看课程明细");

    private final JTextField note = new JTextField(28);
    private final JButton review = new JButton("学分审查");
    private final JButton assessmentHistory = new JButton("历史审查");
    private final JLabel assessmentState = new JLabel("尚未加载审查状态");
    private final JLabel assessmentMeta = new JLabel("选择学生后显示最新审查快照");

    private final JCheckBox confirmed = new JCheckBox("已核查选修课程及其他毕业条件");
    private final JButton graduate = new JButton("确认毕业");
    private final JLabel graduationHint = new JLabel("请先选择学生并执行学分审查");
    private final JLabel reviewStatus = new JLabel("进入页面后将自动加载学生名单。");

    private List<StudentRecord> reviewStudents = Collections.emptyList();
    private StudentRecord selectedStudent;
    private GraduationReviewOverview overview;
    private boolean directoryBusy;
    private boolean studentListBusy;
    private boolean reviewBusy;
    private boolean writeBusy;
    private boolean reviewStudentsLoaded;
    private long directoryGeneration;
    private long studentListGeneration;
    private long overviewGeneration;
    private long writeGeneration;

    AcademicAdministrationPanel(String host, int port, Session session) {
        this(session.getToken(),
                command -> {
                    try (RemoteStudentService remote = new RemoteStudentService(host, port)) {
                        return remote.administer(command);
                    }
                },
                command -> {
                    try (RemoteStudentService remote = new RemoteStudentService(host, port)) {
                        return remote.academicOverview(command);
                    }
                });
    }

    AcademicAdministrationPanel(String token, Loader loader, OverviewLoader overviewLoader) {
        if (token == null || token.trim().isEmpty() || loader == null || overviewLoader == null) {
            throw new IllegalArgumentException("token and loaders are required");
        }
        this.token = token;
        this.loader = loader;
        this.overviewLoader = overviewLoader;
        build();
    }

    private void build() {
        setLayout(new BorderLayout());
        setOpaque(false);
        VCampusTheme.tabs(operations);
        operations.addTab("人员查询", directoryTab());
        operations.addTab("学分审查与毕业", reviewTab());
        operations.addChangeListener(event -> {
            if (operations.getSelectedIndex() == 1 && !reviewStudentsLoaded && !studentListBusy) {
                loadReviewStudents(null);
            }
        });
        add(operations, BorderLayout.CENTER);
        updateReviewControls();
    }

    private JComponent directoryTab() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        page.setOpaque(false);
        page.setBorder(VCampusTheme.padding(10, 4, 4, 4));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(8), 0));
        controls.setOpaque(false);
        VCampusTheme.secondaryButton(allStudents);
        VCampusTheme.secondaryButton(allTeachers);
        VCampusTheme.field(filter);
        controls.add(allStudents);
        controls.add(allTeachers);
        controls.add(new JLabel("搜索"));
        controls.add(filter);
        page.add(AcademicViewComponents.section("全员档案", controls), BorderLayout.NORTH);

        VCampusTheme.table(directoryTable);
        directoryTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        directoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = VCampusTheme.scrollPane(directoryTable);
        scroll.setPreferredSize(UiMetrics.dimension(0, 410));
        JPanel results = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        results.setOpaque(false);
        directoryTitle.setFont(VCampusTheme.font(Font.BOLD, 15));
        directoryTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        results.add(directoryTitle, BorderLayout.NORTH);
        results.add(scroll, BorderLayout.CENTER);
        page.add(results, BorderLayout.CENTER);
        page.add(statusBand(directoryStatus), BorderLayout.SOUTH);

        allStudents.addActionListener(event -> loadDirectory(Action.STUDENTS));
        allTeachers.addActionListener(event -> loadDirectory(Action.TEACHERS));
        filter.getDocument().addDocumentListener(documentListener(this::applyDirectoryFilter));
        return page;
    }

    private JComponent reviewTab() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        page.setOpaque(false);
        page.setBorder(VCampusTheme.padding(10, 4, 4, 4));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                studentSelector(), VCampusTheme.pageScroll(workflow()));
        split.setBorder(null);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        split.setResizeWeight(0.34d);
        split.setDividerLocation(UiMetrics.px(390));
        split.setPreferredSize(UiMetrics.dimension(1040, 610));
        page.add(split, BorderLayout.CENTER);
        page.add(statusBand(reviewStatus), BorderLayout.SOUTH);
        return page;
    }
    private JComponent studentSelector() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(panel);
        JPanel heading = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        heading.setOpaque(false);
        JLabel title = sectionTitle("选择学生");
        JPanel search = new JPanel(new BorderLayout(UiMetrics.px(6), 0));
        search.setOpaque(false);
        VCampusTheme.field(reviewFilter);
        VCampusTheme.secondaryButton(refreshStudents);
        search.add(reviewFilter, BorderLayout.CENTER);
        search.add(refreshStudents, BorderLayout.EAST);
        heading.add(title, BorderLayout.NORTH);
        heading.add(search, BorderLayout.CENTER);
        heading.add(studentCount, BorderLayout.SOUTH);
        studentCount.setForeground(VCampusTheme.MUTED);
        panel.add(heading, BorderLayout.NORTH);

        VCampusTheme.table(studentTable);
        studentTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        studentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {80, 70, 110, 60, 55};
        for (int i = 0; i < widths.length; i++) {
            studentTable.getColumnModel().getColumn(i).setPreferredWidth(UiMetrics.px(widths[i]));
        }
        AcademicViewComponents.sortable(studentTable);
        panel.add(VCampusTheme.scrollPane(studentTable), BorderLayout.CENTER);

        refreshStudents.addActionListener(event -> loadReviewStudents(
                selectedStudent == null ? null : selectedStudent.getStudentId()));
        reviewFilter.getDocument().addDocumentListener(documentListener(this::applyReviewFilter));
        studentTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !studentListBusy) selectReviewStudent();
        });
        return panel;
    }

    private JComponent workflow() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        VCampusTheme.panel(panel);
        panel.add(studentHeader());
        panel.add(Box.createVerticalStrut(UiMetrics.px(14)));
        panel.add(summaryBand());
        panel.add(Box.createVerticalStrut(UiMetrics.px(14)));
        panel.add(separator());
        panel.add(Box.createVerticalStrut(UiMetrics.px(14)));
        panel.add(reviewSection());
        panel.add(Box.createVerticalStrut(UiMetrics.px(14)));
        panel.add(separator());
        panel.add(Box.createVerticalStrut(UiMetrics.px(14)));
        panel.add(graduationSection());
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private JComponent studentHeader() {
        JPanel panel = new JPanel(new BorderLayout(UiMetrics.px(8), UiMetrics.px(4)));
        panel.setOpaque(false);
        studentTitle.setFont(VCampusTheme.font(Font.BOLD, 21));
        studentTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        studentMeta.setForeground(VCampusTheme.MUTED);
        VCampusTheme.secondaryButton(courseDetails);
        courseDetails.addActionListener(event -> loadDetail(Action.HISTORY));
        JPanel copy = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        copy.setOpaque(false);
        copy.add(studentTitle, BorderLayout.NORTH);
        copy.add(studentMeta, BorderLayout.SOUTH);
        panel.add(copy, BorderLayout.CENTER);
        panel.add(courseDetails, BorderLayout.EAST);
        capWidth(panel);
        return panel;
    }

    private JComponent summaryBand() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(true);
        panel.setBackground(VCampusTheme.SURFACE_ALT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.BORDER),
                VCampusTheme.padding(12, 12, 12, 12)));
        addMetric(panel, 0, "已获学分", earnedValue, 1.0d);
        addMetric(panel, 1, "要求学分", requiredValue, 1.0d);
        addMetric(panel, 2, "学分缺口", shortfallValue, 1.0d);
        addMetric(panel, 3, "待重修", retakeValue, 1.0d);
        addMetric(panel, 4, "适用方案", planValue, 1.8d);
        capWidth(panel);
        return panel;
    }

    private JComponent reviewSection() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        panel.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        heading.setOpaque(false);
        heading.add(sectionTitle("学分审查"), BorderLayout.NORTH);
        JLabel description = new JLabel("按当前成绩和已发布培养方案生成审查快照");
        description.setForeground(VCampusTheme.MUTED);
        heading.add(description, BorderLayout.SOUTH);
        panel.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, UiMetrics.px(9)));
        center.setOpaque(false);
        JPanel noteRow = new JPanel(new BorderLayout(UiMetrics.px(8), 0));
        noteRow.setOpaque(false);
        noteRow.add(new JLabel("审查说明（选填）"), BorderLayout.WEST);
        VCampusTheme.field(note);
        noteRow.add(note, BorderLayout.CENTER);
        center.add(noteRow, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        VCampusTheme.primaryButton(review);
        VCampusTheme.secondaryButton(assessmentHistory);
        actions.add(review);
        actions.add(assessmentHistory);
        center.add(actions, BorderLayout.CENTER);
        center.add(snapshotBand(), BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);

        review.addActionListener(event -> performReview());
        assessmentHistory.addActionListener(event -> loadDetail(Action.ASSESSMENTS));
        capWidth(panel);
        return panel;
    }

    private JComponent snapshotBand() {
        JPanel panel = new JPanel(new BorderLayout(UiMetrics.px(12), UiMetrics.px(3)));
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.BORDER),
                VCampusTheme.padding(10, 12, 10, 12)));
        assessmentState.setFont(VCampusTheme.font(Font.BOLD, 14));
        assessmentState.setForeground(VCampusTheme.MUTED);
        assessmentMeta.setForeground(VCampusTheme.MUTED);
        panel.add(assessmentState, BorderLayout.NORTH);
        panel.add(assessmentMeta, BorderLayout.CENTER);
        return panel;
    }

    private JComponent graduationSection() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(9)));
        panel.setOpaque(false);
        panel.add(sectionTitle("毕业确认"), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        body.setOpaque(false);
        confirmed.setOpaque(false);
        body.add(confirmed, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(12), 0));
        actions.setOpaque(false);
        VCampusTheme.primaryButton(graduate);
        graduationHint.setForeground(VCampusTheme.MUTED);
        actions.add(graduate);
        actions.add(graduationHint);
        body.add(actions, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        confirmed.addActionListener(event -> updateReviewControls());
        graduate.addActionListener(event -> confirmGraduation());
        capWidth(panel);
        return panel;
    }

    private void loadDirectory(Action action) {
        if (directoryBusy) return;
        final long current = ++directoryGeneration;
        directoryBusy = true;
        updateDirectoryControls();
        directoryModel.replaceRows(Collections.<Object[]>emptyList());
        directoryStatus.setText("正在加载…");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(command(action, null, null, "", false));
            }
            @Override protected void done() {
                if (current != directoryGeneration) return;
                directoryBusy = false;
                try { displayDirectory(action, get()); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    directoryStatus.setText("查询已中断，请重试。");
                } catch (Exception failure) {
                    directoryStatus.setText("查询失败，请检查服务器连接。");
                }
                updateDirectoryControls();
            }
        }.execute();
    }

    void displayDirectory(Action action, Message response) {
        filter.setText("");
        directoryModel.replaceRows(Collections.<Object[]>emptyList());
        if (!ok(response)) {
            directoryTitle.setText("查询结果 · 暂不可用");
            directoryStatus.setText(error(response));
            return;
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        if (action == Action.STUDENTS) {
            directoryModel.setColumnIdentifiers(new Object[] {"学号", "账号", "姓名", "性别", "院系",
                    "专业", "班级", "入学年", "学籍状态", "手机", "邮箱"});
            for (Object item : (List<?>) response.getPayload()) {
                StudentRecord row = (StudentRecord) item;
                rows.add(new Object[] {row.getStudentId(), row.getUserId(), row.getName(),
                        row.getGender(), row.getDepartmentName(), row.getMajorName(), row.getClassId(),
                        row.getEnrollmentYear(), row.getStatus(), row.getPhone(), row.getEmail()});
            }
            directoryTitle.setText("学生档案 · " + rows.size() + " 条");
        } else {
            directoryModel.setColumnIdentifiers(new Object[] {"工号", "账号", "姓名", "院系", "职称", "在职情况"});
            for (Object item : (List<?>) response.getPayload()) {
                TeacherProfile row = (TeacherProfile) item;
                rows.add(new Object[] {row.getTeacherId(), row.getUserId(), row.getTeacherName(),
                        row.getDepartmentName(), row.getTitle(), row.isActive() ? "在职" : "非在职"});
            }
            directoryTitle.setText("教师档案 · " + rows.size() + " 条");
        }
        directoryModel.replaceRows(rows);
        for (int i = 0; i < directoryTable.getColumnCount(); i++) {
            directoryTable.getColumnModel().getColumn(i).setPreferredWidth(UiMetrics.px(130));
        }
        AcademicViewComponents.sortable(directoryTable);
        directoryStatus.setText(rows.isEmpty() ? "暂无档案。" : "查询成功。");
    }

    private void loadReviewStudents(String preferredStudentId) {
        final long current = ++studentListGeneration;
        studentListBusy = true;
        reviewStudentsLoaded = false;
        selectedStudent = null;
        studentTable.clearSelection();
        clearOverview();
        studentCount.setText("正在加载学生…");
        updateReviewControls();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(command(Action.STUDENTS, null, null, "", false));
            }
            @Override protected void done() {
                if (current != studentListGeneration) return;
                studentListBusy = false;
                try { displayReviewStudents(get(), preferredStudentId); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reviewStatus.setText("学生名单加载已中断。");
                } catch (Exception failure) {
                    reviewStatus.setText("无法加载学生名单，请检查服务器连接。");
                }
                updateReviewControls();
            }
        }.execute();
    }

    void displayReviewStudents(Message response, String preferredStudentId) {
        reviewFilter.setText("");
        studentModel.replaceRows(Collections.<Object[]>emptyList());
        reviewStudents = Collections.emptyList();
        if (!ok(response)) {
            studentCount.setText("学生名单暂不可用");
            reviewStatus.setText(error(response));
            return;
        }
        List<StudentRecord> loaded = new ArrayList<StudentRecord>();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : (List<?>) response.getPayload()) {
            StudentRecord row = (StudentRecord) item;
            loaded.add(row);
            rows.add(new Object[] {row.getStudentId(), row.getName(), row.getMajorName(),
                    row.getEnrollmentYear(), row.getStatus()});
        }
        reviewStudents = Collections.unmodifiableList(loaded);
        studentModel.replaceRows(rows);
        reviewStudentsLoaded = true;
        studentCount.setText("共 " + rows.size() + " 名学生");
        reviewStatus.setText(rows.isEmpty() ? "暂无学生档案。" : "请从左侧选择办理学生。");
        if (!rows.isEmpty()) selectStudentRow(preferredStudentId);
    }

    private void selectStudentRow(String preferredStudentId) {
        int modelIndex = 0;
        if (preferredStudentId != null) {
            for (int i = 0; i < reviewStudents.size(); i++) {
                if (preferredStudentId.equals(reviewStudents.get(i).getStudentId())) {
                    modelIndex = i;
                    break;
                }
            }
        }
        int viewIndex = studentTable.convertRowIndexToView(modelIndex);
        if (viewIndex >= 0) studentTable.setRowSelectionInterval(viewIndex, viewIndex);
    }

    private void selectReviewStudent() {
        int viewIndex = studentTable.getSelectedRow();
        int modelIndex = viewIndex < 0 ? -1 : studentTable.convertRowIndexToModel(viewIndex);
        if (modelIndex < 0 || modelIndex >= reviewStudents.size()) {
            selectedStudent = null;
            clearOverview();
            return;
        }
        StudentRecord selected = reviewStudents.get(modelIndex);
        if (selectedStudent != null
                && selected.getStudentId().equals(selectedStudent.getStudentId())
                && overview != null) return;
        selectedStudent = selected;
        clearOverview();
        studentTitle.setText(selected.getStudentId() + "  " + selected.getName());
        studentMeta.setText(studentMeta(selected));
        loadOverview(selected.getStudentId());
    }

    private void loadOverview(String studentId) {
        final long current = ++overviewGeneration;
        reviewBusy = true;
        reviewStatus.setText("正在加载 " + studentId + " 的学业概览…");
        updateReviewControls();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return overviewLoader.load(new AcademicAdminOverviewV1Command(token, studentId));
            }
            @Override protected void done() {
                if (current != overviewGeneration) return;
                reviewBusy = false;
                try {
                    if (selectedStudent == null
                            || !studentId.equals(selectedStudent.getStudentId())) return;
                    displayOverview(get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reviewStatus.setText("概览加载已中断。");
                } catch (Exception failure) {
                    reviewStatus.setText("无法确认概览结果，请重试。");
                } finally {
                    updateReviewControls();
                }
            }
        }.execute();
    }

    void displayOverview(Message response) {
        overview = null;
        confirmed.setSelected(false);
        if (!ok(response)) {
            resetMetricValues();
            assessmentState.setText("暂无法进行学分审查");
            assessmentState.setForeground(VCampusTheme.DANGER);
            assessmentMeta.setText(error(response));
            reviewStatus.setText(error(response));
            updateReviewControls();
            return;
        }
        overview = (GraduationReviewOverview) response.getPayload();
        selectedStudent = overview.getStudent();
        studentTitle.setText(selectedStudent.getStudentId() + "  " + selectedStudent.getName());
        studentMeta.setText(studentMeta(selectedStudent));
        CreditSummary credits = overview.getCredits();
        GraduationCreditRequirement requirement = overview.getRequirement();
        BigDecimal shortfall = requirement.getRequiredCreditsDecimal()
                .subtract(credits.getEarnedCreditsDecimal()).max(BigDecimal.ZERO);
        earnedValue.setText(CreditFormat.display(credits.getEarnedCreditsDecimal()));
        requiredValue.setText(CreditFormat.display(requirement.getRequiredCreditsDecimal()));
        shortfallValue.setText(CreditFormat.display(shortfall));
        retakeValue.setText(String.valueOf(credits.getPendingRetakes()));
        planValue.setText(requirement.getPlanId());
        boolean creditsReady = shortfall.signum() == 0 && credits.getPendingRetakes() == 0;
        earnedValue.setForeground(creditsReady ? VCampusTheme.SUCCESS : VCampusTheme.PRIMARY_DARK);
        shortfallValue.setForeground(shortfall.signum() == 0
                ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
        retakeValue.setForeground(credits.getPendingRetakes() == 0
                ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
        updateAssessmentBand();
        reviewStatus.setText("已加载实时学分、培养方案要求和最新审查快照。");
        updateReviewControls();
    }

    private void updateAssessmentBand() {
        AcademicAssessment latest = overview == null ? null : overview.getLatestAssessment();
        if (latest == null) {
            assessmentState.setText("尚未执行学分审查");
            assessmentState.setForeground(VCampusTheme.MUTED);
            assessmentMeta.setText("点击“学分审查”生成当前数据快照");
            return;
        }
        String identity = "审查人 " + latest.getReviewedBy() + "  ·  "
                + TIME.format(latest.getReviewedAt().atZone(ZoneId.systemDefault()));
        if (latest.isGraduated()) {
            assessmentState.setText("已办理毕业");
            assessmentState.setForeground(VCampusTheme.SUCCESS);
            assessmentMeta.setText(identity + "  ·  办理人 " + latest.getGraduatedBy()
                    + (overview.isLatestAssessmentCurrent() ? "" : "  ·  当前数据已变化"));
        } else if (!overview.isLatestAssessmentCurrent()) {
            assessmentState.setText("最新审查已过期");
            assessmentState.setForeground(VCampusTheme.DANGER);
            assessmentMeta.setText(identity + "  ·  成绩、档案或培养方案已变化，请重新审查");
        } else if (latest.isCreditRequirementMet()) {
            assessmentState.setText("学分审查已达标");
            assessmentState.setForeground(VCampusTheme.SUCCESS);
            assessmentMeta.setText(identity + "  ·  快照 " + latest.getId());
        } else {
            assessmentState.setText("学分审查未达标");
            assessmentState.setForeground(VCampusTheme.DANGER);
            assessmentMeta.setText(identity + "  ·  缺口 "
                    + CreditFormat.display(latest.getShortfallDecimal())
                    + " 学分，待重修 " + latest.getCredits().getPendingRetakes() + " 门");
        }
    }

    private void performReview() {
        if (selectedStudent == null || reviewBusy) return;
        final String studentId = selectedStudent.getStudentId();
        final long current = ++writeGeneration;
        reviewBusy = true;
        writeBusy = true;
        reviewStatus.setText("正在生成学分审查快照…");
        updateReviewControls();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(command(Action.REVIEW, studentId, null, note.getText(), false));
            }
            @Override protected void done() {
                if (current != writeGeneration) return;
                reviewBusy = false;
                writeBusy = false;
                try {
                    if (selectedStudent == null
                            || !studentId.equals(selectedStudent.getStudentId())) {
                        reviewStatus.setText("审查请求已完成，请重新选择学生核对结果。");
                        return;
                    }
                    Message response = get();
                    if (ok(response)) {
                        note.setText("");
                        reviewStatus.setText("学分审查已保存，正在刷新概览…");
                        loadOverview(studentId);
                    } else {
                        reviewStatus.setText(error(response));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reviewStatus.setText("学分审查已中断，请查看历史审查后再重试。");
                } catch (Exception failure) {
                    reviewStatus.setText("未能确认审查结果，请查看历史审查后再重试。");
                } finally {
                    updateReviewControls();
                }
            }
        }.execute();
    }

    private void confirmGraduation() {
        AcademicAssessment latest = overview == null ? null : overview.getLatestAssessment();
        if (!graduate.isEnabled() || selectedStudent == null || latest == null) return;
        String message = "确认将学生 " + selectedStudent.getStudentId() + " " + selectedStudent.getName()
                + " 的学籍状态变更为“毕业”？\n"
                + "已获学分 " + CreditFormat.display(latest.getCredits().getEarnedCreditsDecimal())
                + "，要求学分 " + CreditFormat.display(latest.getRequiredCreditsDecimal())
                + "。\n此操作当前不提供撤销，请确认其他毕业条件已核查。";
        if (JOptionPane.showConfirmDialog(this, message, "教务毕业确认",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        performGraduation(latest.getId());
    }

    private void performGraduation(String assessmentId) {
        final String studentId = selectedStudent.getStudentId();
        final long current = ++writeGeneration;
        reviewBusy = true;
        writeBusy = true;
        reviewStatus.setText("正在办理毕业…");
        updateReviewControls();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(command(Action.GRADUATE, studentId, assessmentId,
                        note.getText(), confirmed.isSelected()));
            }
            @Override protected void done() {
                if (current != writeGeneration) return;
                reviewBusy = false;
                writeBusy = false;
                try {
                    if (selectedStudent == null
                            || !studentId.equals(selectedStudent.getStudentId())) {
                        reviewStatus.setText("毕业请求已完成，请刷新学生名单核对结果。");
                        return;
                    }
                    Message response = get();
                    if (ok(response)) {
                        note.setText("");
                        confirmed.setSelected(false);
                        reviewStatus.setText("毕业办理成功，正在刷新学生状态…");
                        loadReviewStudents(studentId);
                    } else {
                        reviewStatus.setText(error(response));
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reviewStatus.setText("办理已中断，请刷新学生后核对结果。");
                } catch (Exception failure) {
                    reviewStatus.setText("未能确认办理结果，请刷新学生后核对。");
                } finally {
                    updateReviewControls();
                }
            }
        }.execute();
    }

    private void loadDetail(Action action) {
        if (selectedStudent == null || reviewBusy) return;
        final String studentId = selectedStudent.getStudentId();
        reviewBusy = true;
        reviewStatus.setText(action == Action.HISTORY ? "正在加载课程明细…" : "正在加载历史审查…");
        updateReviewControls();
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(command(action, studentId, null, "", false));
            }
            @Override protected void done() {
                reviewBusy = false;
                try {
                    if (selectedStudent == null
                            || !studentId.equals(selectedStudent.getStudentId())) return;
                    Message response = get();
                    if (ok(response)) showDetailDialog(action, (List<?>) response.getPayload());
                    else reviewStatus.setText(error(response));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reviewStatus.setText("明细加载已中断。");
                } catch (Exception failure) {
                    reviewStatus.setText("无法加载明细，请重试。");
                } finally {
                    updateReviewControls();
                }
            }
        }.execute();
    }

    private void showDetailDialog(Action action, List<?> data) {
        BatchTableModel detailModel;
        List<Object[]> rows = new ArrayList<Object[]>();
        if (action == Action.HISTORY) {
            detailModel = new BatchTableModel(new Object[] {"课程号", "课程名", "学期", "尝试",
                    "类型", "成绩", "通过", "学分"});
            for (Object item : data) {
                CourseHistoryRecord row = (CourseHistoryRecord) item;
                rows.add(new Object[] {row.getCourseId(), row.getCourseName(), row.getSemester(),
                        row.getAttemptNo(), row.getAttemptType(), row.getScore(),
                        row.isPassed() ? "是" : "否",
                        CreditFormat.display(row.getEarnedCreditsDecimal())});
            }
        } else {
            detailModel = new BatchTableModel(new Object[] {"审查编号", "已获学分", "要求学分",
                    "缺口", "待重修", "结果", "审查人", "时间", "毕业办理"});
            for (Object item : data) {
                AcademicAssessment row = (AcademicAssessment) item;
                rows.add(new Object[] {row.getId(),
                        CreditFormat.display(row.getCredits().getEarnedCreditsDecimal()),
                        CreditFormat.display(row.getRequiredCreditsDecimal()),
                        CreditFormat.display(row.getShortfallDecimal()),
                        row.getCredits().getPendingRetakes(),
                        row.isCreditRequirementMet() ? "达标" : "未达标", row.getReviewedBy(),
                        row.getReviewedAt(), row.isGraduated() ? "已毕业" : "未办理"});
            }
        }
        detailModel.replaceRows(rows);
        JTable detail = new JTable(detailModel);
        VCampusTheme.table(detail);
        detail.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < detail.getColumnCount(); i++) {
            detail.getColumnModel().getColumn(i).setPreferredWidth(UiMetrics.px(125));
        }
        AcademicViewComponents.sortable(detail);
        JScrollPane scroll = VCampusTheme.scrollPane(detail);
        scroll.setPreferredSize(UiMetrics.dimension(900, 360));
        JOptionPane.showMessageDialog(this, scroll,
                action == Action.HISTORY ? "课程明细" : "历史审查",
                JOptionPane.PLAIN_MESSAGE);
        reviewStatus.setText(rows.isEmpty() ? "暂无明细记录。" : "明细已加载。");
    }

    private void updateDirectoryControls() {
        allStudents.setEnabled(!directoryBusy);
        allTeachers.setEnabled(!directoryBusy);
        filter.setEnabled(!directoryBusy);
        directoryTable.setEnabled(!directoryBusy);
    }

    private void updateReviewControls() {
        boolean selected = selectedStudent != null;
        boolean activeStudent = selected && "在读".equals(selectedStudent.getStatus());
        AcademicAssessment latest = overview == null ? null : overview.getLatestAssessment();
        boolean current = latest != null && overview.isLatestAssessmentCurrent();
        boolean ready = current && latest.isCreditRequirementMet() && !latest.isGraduated();

        reviewFilter.setEnabled(!studentListBusy && !reviewBusy && !writeBusy);
        refreshStudents.setEnabled(!studentListBusy && !reviewBusy);
        studentTable.setEnabled(!studentListBusy && !reviewBusy && !writeBusy);
        boolean interactive = !studentListBusy && !reviewBusy;
        note.setEnabled(selected && interactive);
        courseDetails.setEnabled(overview != null && interactive);
        assessmentHistory.setEnabled(selected && interactive);
        review.setEnabled(overview != null && activeStudent && interactive);
        confirmed.setEnabled(activeStudent && ready && interactive);
        graduate.setEnabled(activeStudent && ready && confirmed.isSelected() && interactive);

        if (latest != null && latest.isGraduated()) {
            graduationHint.setText("该学生已办理毕业");
        } else if (!activeStudent && selected) {
            graduationHint.setText("仅在读学生可办理毕业");
        } else if (!ready) {
            graduationHint.setText("学分审查达标后可确认毕业");
        } else {
            graduationHint.setText("确认后将学生学籍状态变更为“毕业”");
        }
    }

    private void clearOverview() {
        overviewGeneration++;
        if (!writeBusy) reviewBusy = false;
        overview = null;
        confirmed.setSelected(false);
        resetMetricValues();
        assessmentState.setText("尚未加载审查状态");
        assessmentState.setForeground(VCampusTheme.MUTED);
        assessmentMeta.setText("选择学生后显示最新审查快照");
        updateReviewControls();
    }

    private void resetMetricValues() {
        for (JLabel label : new JLabel[] {earnedValue, requiredValue, shortfallValue, retakeValue, planValue}) {
            label.setText("--");
            label.setForeground(VCampusTheme.PRIMARY_DARK);
        }
    }

    private void applyDirectoryFilter() {
        applyFilter(directoryTable, filter.getText());
        directoryTitle.setText("查询结果 · 显示 " + directoryTable.getRowCount()
                + " / " + directoryModel.getRowCount() + " 条");
    }

    private void applyReviewFilter() {
        applyFilter(studentTable, reviewFilter.getText());
        studentCount.setText("显示 " + studentTable.getRowCount() + " / "
                + studentModel.getRowCount() + " 名学生");
    }

    private static void applyFilter(JTable table, String value) {
        if (!(table.getRowSorter() instanceof TableRowSorter<?>)) return;
        TableRowSorter<?> sorter = (TableRowSorter<?>) table.getRowSorter();
        String text = value == null ? "" : value.trim();
        sorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
    }

    private AcademicAdminCommandV2 command(Action action, String studentId,
            String assessmentId, String commandNote, boolean otherRequirementsConfirmed) {
        return new AcademicAdminCommandV2(token, action, studentId, assessmentId,
                commandNote, otherRequirementsConfirmed);
    }

    private static boolean ok(Message response) {
        return response != null && response.getStatusCode() == StatusCode.OK;
    }

    private static String error(Message response) {
        if (response == null) return "无响应，请重试。";
        return response.getPayload() instanceof String
                ? String.valueOf(response.getPayload()) : response.getStatusCode() + "：操作未完成";
    }

    private static String studentMeta(StudentRecord student) {
        return safe(student.getMajorName()) + "  ·  " + student.getEnrollmentYear()
                + "级  ·  " + safe(student.getStatus());
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "未配置" : value.trim();
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("--");
        label.setFont(VCampusTheme.font(Font.BOLD, 22));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private static void addMetric(JPanel parent, int index, String title, JLabel value, double weight) {
        JPanel metric = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        metric.setOpaque(false);
        JLabel heading = new JLabel(title);
        heading.setForeground(VCampusTheme.MUTED);
        metric.add(heading, BorderLayout.NORTH);
        metric.add(value, BorderLayout.CENTER);
        if (index > 0) {
            metric.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, UiMetrics.px(1), 0, 0, VCampusTheme.BORDER),
                    VCampusTheme.padding(0, 14, 0, 8)));
        }
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = index;
        constraints.gridy = 0;
        constraints.weightx = weight;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, index == 0 ? 0 : UiMetrics.px(4), 0, UiMetrics.px(4));
        parent.add(metric, constraints);
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(Font.BOLD, 17));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private static JComponent separator() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(VCampusTheme.BORDER);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiMetrics.px(1)));
        line.setPreferredSize(UiMetrics.dimension(1, 1));
        return line;
    }

    private static JPanel statusBand(JLabel label) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(VCampusTheme.SURFACE_ALT);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(VCampusTheme.BORDER),
                VCampusTheme.padding(8, 12, 8, 12)));
        label.setForeground(VCampusTheme.MUTED);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private static DocumentListener documentListener(Runnable action) {
        return new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(DocumentEvent event) { action.run(); }
        };
    }

    private static void capWidth(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                component.getPreferredSize().height));
    }

    @Override public void removeNotify() {
        directoryGeneration++;
        studentListGeneration++;
        overviewGeneration++;
        writeGeneration++;
        directoryBusy = false;
        studentListBusy = false;
        reviewBusy = false;
        writeBusy = false;
        super.removeNotify();
    }
}

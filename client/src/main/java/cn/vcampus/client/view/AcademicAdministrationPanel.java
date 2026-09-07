package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.*;
import cn.vcampus.student.*;
import cn.vcampus.student.AcademicAdminCommandV1.Action;
import cn.vcampus.user.Session;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Academic administrators read the whole directory and record explicit graduation decisions. */
final class AcademicAdministrationPanel extends JPanel {
    interface Loader { Message load(AcademicAdminCommandV1 command) throws Exception; }
    private final Loader loader;
    private final String token;
    private final JTextField studentId = new JTextField(14);
    private final JTextField required = new JTextField(7);
    private final JTextField note = new JTextField(30);
    private final JCheckBox confirmed = new JCheckBox("已核查培养方案、必修/选修及其他毕业条件");
    private final JTextField filter = new JTextField(16);
    private final JLabel resultTitle = new JLabel("查询结果 · 尚未加载");
    private final JLabel status = new JLabel("先查询学生或教师目录；选中学生后可办理学分审查。");
    private final BatchTableModel model = new BatchTableModel(new Object[] {"信息"});
    private final JTable table = new JTable(model);
    private final List<JButton> buttons = new ArrayList<JButton>();
    private final JButton graduate = new JButton("确认毕业");
    private final JButton review = new JButton("保存学分审查");
    private final JLabel assessmentInfo = new JLabel("尚未加载该学生最新审查记录");
    private AcademicAssessment assessment;
    private List<StudentRecord> studentRows = Collections.emptyList();
    private boolean busy;
    private long generation;

    AcademicAdministrationPanel(String host, int port, Session session) {
        this(session.getToken(), command -> {
            try (RemoteStudentService remote = new RemoteStudentService(host, port)) {
                return remote.administer(command);
            }
        });
    }
    AcademicAdministrationPanel(String token, Loader loader) {
        this.token = token; this.loader = loader;
        setLayout(new BorderLayout(0, 10)); setOpaque(false);
        JPanel page = new ScrollablePagePanel(new BorderLayout(0, 14));
        JPanel controls = new JPanel(new BorderLayout(0, 12)); controls.setOpaque(false);
        JPanel target = row();
        target.add(new JLabel("办理学生")); target.add(studentId);
        VCampusTheme.field(studentId);
        studentId.setToolTipText("输入学号，或在全部学生中选择一行");
        addButton(target, "当前学分", Action.CREDITS);
        addButton(target, "课程历史", Action.HISTORY);
        controls.add(target, BorderLayout.NORTH);
        JTabbedPane operations = new JTabbedPane() {
            @Override public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Component selected = getSelectedComponent();
                if (selected != null) {
                    size.height = selected.getPreferredSize().height
                            + getFontMetrics(getFont()).getHeight() + 18;
                }
                return size;
            }
        };
        operations.addChangeListener(event -> { page.revalidate(); page.repaint(); });
        VCampusTheme.tabs(operations);
        JPanel directory = row();
        addButton(directory, "全部学生", Action.STUDENTS);
        addButton(directory, "全部教师", Action.TEACHERS);
        directory.add(new JLabel("筛选结果")); directory.add(filter);
        VCampusTheme.field(filter);
        filter.setToolTipText("在已加载结果中筛选学号、工号、姓名、院系等");
        operations.addTab("人员查询", AcademicViewComponents.section("全员档案", directory));
        JPanel reviewBody = new JPanel(new BorderLayout(0, 8)); reviewBody.setOpaque(false);
        JPanel assessmentRow = row();
        required.setColumns(5); note.setColumns(23);
        VCampusTheme.field(required); VCampusTheme.field(note);
        assessmentRow.add(new JLabel("要求学分")); assessmentRow.add(required);
        assessmentRow.add(new JLabel("审查说明（选填）")); assessmentRow.add(note);
        required.setToolTipText("填写适用培养方案要求的总学分");
        note.setToolTipText("可留空；如需备注，最多255字");
        reviewBody.add(assessmentRow, BorderLayout.NORTH);
        JPanel actions = row();
        review.addActionListener(event -> submit(Action.REVIEW));
        VCampusTheme.secondaryButton(review); buttons.add(review); actions.add(review);
        addButton(actions, "查看审查记录", Action.ASSESSMENTS);
        confirmed.setOpaque(false); actions.add(confirmed);
        VCampusTheme.primaryButton(graduate); graduate.setEnabled(false);
        graduate.addActionListener(event -> confirmGraduation()); actions.add(graduate);
        reviewBody.add(actions, BorderLayout.CENTER);
        operations.addTab("学分审查与毕业", AcademicViewComponents.section("保存审查 → 核查其他条件 → 确认毕业", reviewBody));
        controls.add(operations, BorderLayout.CENTER);
        assessmentInfo.setForeground(VCampusTheme.PRIMARY);
        controls.add(assessmentInfo, BorderLayout.SOUTH);
        page.add(controls, BorderLayout.NORTH);
        VCampusTheme.table(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = VCampusTheme.scrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(0, 300));
        JPanel results = new JPanel(new BorderLayout(0, 8)); results.setOpaque(false);
        resultTitle.setFont(VCampusTheme.font(Font.BOLD, 15));
        results.add(resultTitle, BorderLayout.NORTH);
        results.add(scroll, BorderLayout.CENTER);
        page.add(results, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 6));
        VCampusTheme.panel(footer);
        status.setForeground(VCampusTheme.MUTED);
        footer.add(status, BorderLayout.NORTH);
        JLabel hint = new JLabel("<html>点击表头可排序；人员查询可按关键词筛选。"
                + "<br>学分达标后仍须教务核查其他条件；确认毕业会保存办理记录并变更学籍状态。</html>");
        hint.setFont(VCampusTheme.font(Font.PLAIN, 12));
        hint.setForeground(VCampusTheme.MUTED); footer.add(hint, BorderLayout.SOUTH);
        page.add(footer, BorderLayout.SOUTH);
        add(VCampusTheme.pageScroll(page), BorderLayout.CENTER);
        filter.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        studentId.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { invalidateAssessment(); }
            public void removeUpdate(DocumentEvent e) { invalidateAssessment(); }
            public void changedUpdate(DocumentEvent e) { invalidateAssessment(); }
        });
        confirmed.addActionListener(event -> updateButtons());
        table.getSelectionModel().addListSelectionListener(event -> {
            int viewIndex = table.getSelectedRow();
            int index = viewIndex < 0 ? -1 : table.convertRowIndexToModel(viewIndex);
            if (!event.getValueIsAdjusting() && !busy && index >= 0 && index < studentRows.size()) {
                studentId.setText(studentRows.get(index).getStudentId());
            }
        });
    }
    private JPanel row() {
        JPanel row = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 8, 4));
        row.setOpaque(false); return row;
    }
    private void addButton(JPanel panel, String text, Action action) {
        JButton button = new JButton(text); VCampusTheme.secondaryButton(button);
        button.addActionListener(event -> submit(action)); buttons.add(button); panel.add(button);
    }
    private void invalidateAssessment() {
        assessment = null; confirmed.setSelected(false);
        assessmentInfo.setText("尚未加载该学生最新审查记录"); updateButtons();
    }
    private void updateButtons() {
        for (JButton button : buttons) button.setEnabled(!busy);
        studentId.setEnabled(!busy); required.setEnabled(!busy); note.setEnabled(!busy); filter.setEnabled(!busy);
        confirmed.setEnabled(!busy); table.setEnabled(!busy);
        graduate.setEnabled(!busy && assessment != null && assessment.isCreditRequirementMet()
                && !assessment.isGraduated() && confirmed.isSelected()
                && studentId.getText().trim().equals(assessment.getStudentId()));
    }
    private void confirmGraduation() {
        if (!graduate.isEnabled()) return;
        if (JOptionPane.showConfirmDialog(this,
                "确认将学生 " + assessment.getStudentId() + " 的学籍状态变更为“毕业”？\n"
                + "本次审查累计 " + assessment.getCredits().getEarnedCredits() + " 学分，要求 "
                + assessment.getRequiredCredits() + " 学分。\n请确认其他毕业条件已经人工核查。",
                "教务毕业确认", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION) {
            submit(Action.GRADUATE);
        }
    }
    void submit(Action action) {
        if (busy) return;
        final AcademicAdminCommandV1 command;
        try {
            command = new AcademicAdminCommandV1(token, action, studentId.getText(),
                    action == Action.REVIEW ? Integer.parseInt(required.getText().trim()) : 0,
                    assessment == null ? null : assessment.getId(), note.getText(), confirmed.isSelected());
        } catch (IllegalArgumentException invalid) {
            status.setText("输入有误：" + (invalid instanceof NumberFormatException
                    ? "要求学分须填写正整数。" : invalid.getMessage()));
            return;
        }
        final long current = ++generation;
        busy = true; updateButtons(); studentRows = Collections.emptyList();
        model.replaceRows(Collections.<Object[]>emptyList()); status.setText("正在办理…");
        new SwingWorker<Message, Void>() {
            protected Message doInBackground() throws Exception { return loader.load(command); }
            protected void done() {
                if (current != generation) return;
                busy = false;
                try { display(action, get()); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); invalidateAssessment(); status.setText("操作中断，请重新查询记录。");
                } catch (Exception failure) {
                    invalidateAssessment();
                    status.setText("未能确认操作结果。若刚才提交了审查或毕业，请先查询记录再重试。");
                }
                updateButtons();
            }
        }.execute();
    }
    void display(Action action, Message response) {
        studentRows = Collections.emptyList();
        filter.setText("");
        model.replaceRows(Collections.<Object[]>emptyList());
        if (response == null || response.getStatusCode() != StatusCode.OK) {
            invalidateAssessment();
            status.setText(response == null ? "无响应，请重试" : response.getStatusCode() + "："
                    + (response.getPayload() instanceof String ? response.getPayload() : "操作未完成"));
            resultTitle.setText("查询结果 · 暂不可用");
            return;
        }
        Object data = response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        String[] columns;
        if (action == Action.STUDENTS) {
            columns = new String[] {"学号", "账号", "姓名", "性别", "院系", "专业", "班级", "入学年", "学籍状态", "手机", "邮箱"};
            List<StudentRecord> loaded = new ArrayList<StudentRecord>();
            for (Object item : (List<?>) data) {
                StudentRecord r = (StudentRecord) item; loaded.add(r);
                rows.add(new Object[] {r.getStudentId(), r.getUserId(), r.getName(), r.getGender(),
                        r.getDepartmentName(), r.getMajorName(), r.getClassId(), r.getEnrollmentYear(),
                        r.getStatus(), r.getPhone(), r.getEmail()});
            }
            studentRows = loaded;
        } else if (action == Action.TEACHERS) {
            columns = new String[] {"工号", "账号", "姓名", "院系", "职称", "在职情况"};
            for (Object item : (List<?>) data) {
                TeacherProfile r = (TeacherProfile) item;
                rows.add(new Object[] {r.getTeacherId(), r.getUserId(), r.getTeacherName(),
                        r.getDepartmentName(), r.getTitle(), r.isActive() ? "在职" : "非在职"});
            }
        } else if (action == Action.HISTORY) {
            columns = new String[] {"课程号", "课程名", "学期", "尝试", "类型", "成绩", "通过", "学分"};
            for (Object item : (List<?>) data) {
                CourseHistoryRecord r = (CourseHistoryRecord) item;
                rows.add(new Object[] {r.getCourseId(), r.getCourseName(), r.getSemester(),
                        r.getAttemptNo(), r.getAttemptType(), r.getScore(), r.isPassed() ? "是" : "否", r.getEarnedCredits()});
            }
        } else if (action == Action.CREDITS) {
            CreditSummary r = (CreditSummary) data;
            columns = new String[] {"学号", "当前学分", "通过课程", "待重修", "历史重修"};
            rows.add(new Object[] {r.getStudentId(), r.getEarnedCredits(), r.getPassedCourses(),
                    r.getPendingRetakes(), r.getHistoricalRetakes()});
        } else {
            List<?> assessments = action == Action.ASSESSMENTS ? (List<?>) data : Collections.singletonList(data);
            columns = new String[] {"审查编号", "学号", "已获学分", "要求学分", "缺口", "待重修", "学分审查",
                    "审查人", "时间", "审查说明", "毕业办理", "办理人", "办理时间", "毕业说明"};
            for (Object item : assessments) {
                AcademicAssessment r = (AcademicAssessment) item;
                rows.add(new Object[] {r.getId(), r.getStudentId(), r.getCredits().getEarnedCredits(),
                        r.getRequiredCredits(), r.getShortfall(), r.getCredits().getPendingRetakes(),
                        r.isCreditRequirementMet() ? "达标" : "未达标", r.getReviewedBy(), r.getReviewedAt(),
                        r.getBasis(), r.isGraduated() ? "已毕业" : "未办理", r.getGraduatedBy(), r.getGraduatedAt(), r.getGraduationNote()});
            }
            assessment = assessments.isEmpty() ? null : (AcademicAssessment) assessments.get(0);
            confirmed.setSelected(false);
            assessmentInfo.setText(assessment == null ? "暂无审查记录" : "最新审查："
                    + assessment.getCredits().getEarnedCredits() + " / " + assessment.getRequiredCredits()
                    + " 学分；" + (assessment.isGraduated() ? "已办理毕业" : assessment.isCreditRequirementMet()
                    ? "学分达标，待教务核查其他条件" : "学分审查未达标"));
        }
        model.setColumnIdentifiers(columns);
        model.replaceRows(rows);
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(145);
        AcademicViewComponents.sortable(table);
        resultTitle.setText(resultName(action) + " · " + rows.size() + " 条");
        status.setText(action == Action.GRADUATE ? "毕业办理成功，学籍状态与核查记录已保存。"
                : rows.isEmpty() ? "暂无记录。" : "查询 / 保存成功，共 " + rows.size() + " 条。");
        updateButtons();
    }
    private void applyFilter() {
        if (table.getRowSorter() instanceof javax.swing.table.TableRowSorter<?>) {
            javax.swing.table.TableRowSorter<?> sorter = (javax.swing.table.TableRowSorter<?>) table.getRowSorter();
            String text = filter.getText().trim();
            sorter.setRowFilter(text.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
            resultTitle.setText("查询结果 · 显示 " + table.getRowCount() + " / " + model.getRowCount() + " 条");
        }
    }
    private static String resultName(Action action) {
        switch (action) {
            case STUDENTS: return "学生档案";
            case TEACHERS: return "教师档案";
            case HISTORY: return "课程历史";
            case CREDITS: return "当前学分";
            default: return "学分审查记录";
        }
    }
    @Override public void removeNotify() {
        generation++; busy = false; invalidateAssessment();
        model.replaceRows(Collections.<Object[]>emptyList());
        super.removeNotify();
    }
}

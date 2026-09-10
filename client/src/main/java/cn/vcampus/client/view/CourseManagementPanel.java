package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseStatus;
import cn.vcampus.student.TeacherProfile;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** 教务管理员维护课程、教学班、选课轮次、培养方案和成绩审核的工作台。 */
public final class CourseManagementPanel extends JPanel {
    private static final int[] COURSE_COLUMN_WIDTHS = { 140, 250, 80, 110 };
    private static final int[] OFFERING_COLUMN_WIDTHS = {
            145, 125, 125, 125, 220, 120, 95, 95, 115, 105
    };

    private final String host;
    private final int port;
    private final Session session;
    private final JLabel status = new JLabel();
    private final CardLayout managementLayout = new CardLayout();
    private final ScrollablePagePanel managementPages = new ScrollablePagePanel(managementLayout);
    private final BatchTableModel courseModel = new BatchTableModel(
            new Object[] { "课程编号", "课程名称", "学分", "状态" });
    private final BatchTableModel offeringModel = new BatchTableModel(
            new Object[] { "教学班编号", "课程编号", "学期", "教师", "时间", "地点", "必修容量", "选修容量", "跨专业容量", "状态" });
    private final JTable courseTable = new JTable(courseModel);
    private final JTable offeringTable = new JTable(offeringModel);

    private final JComboBox<String> term = new JComboBox<String>(
            new String[] { "2026-2027-1", "2025-2026-2", "2025-2026-1" });
    private final Map<String, CourseOffering> offeringsById =
            new LinkedHashMap<String, CourseOffering>();

    private final List<JButton> actions = new ArrayList<JButton>();
    private boolean requestInProgress;

    public CourseManagementPanel(String host, int port, Session session) {
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
        managementPages.setOpaque(false);
        managementPages.add(landingPage(), "landing");
        managementPages.add(managementPage("课程目录管理", catalogPanel()), "catalog");
        managementPages.add(managementPage("教学班管理", offeringPanel()), "offering");
        managementPages.add(managementPage("选课轮次管理",
                new SelectionRoundManagementPanel(host, port, session)), "round");
        managementPages.add(managementPage("培养方案管理",
                new TrainingPlanManagementPanel(host, port, session)), "plan");
        managementPages.add(managementPage("成绩审核", new GradeReviewPanel(host, port, session)),
                "review");
        term.addActionListener(e -> { if (!requestInProgress) loadOfferings(); });
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(managementPages), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        configureTable(courseTable, COURSE_COLUMN_WIDTHS);
        configureTable(offeringTable, OFFERING_COLUMN_WIDTHS);
        managementLayout.show(managementPages, "landing");
        showStatus("请选择要办理的管理事项", VCampusTheme.MUTED);
    }

    private JPanel landingPage() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(14)));
        panel.setOpaque(false);
        JPanel card = new JPanel();
        card.setLayout(new javax.swing.BoxLayout(card, javax.swing.BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.add(sectionTitle("请选择要办理的管理事项"));
        card.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(8)));
        card.add(entryCard("课程目录管理", "维护课程基本信息及启用状态。", "catalog", this::loadCourses));
        card.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        card.add(entryCard("教学班管理", "按学期维护教学班、任课教师、时间地点和容量。", "offering", this::loadOfferings));
        card.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        card.add(entryCard("选课轮次管理", "维护首修、重修轮次及开放时间。", "round", null));
        card.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        card.add(entryCard("培养方案管理", "维护各专业入学年份对应的培养方案和课程要求。", "plan", null));
        card.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        card.add(entryCard("成绩审核", "处理教师提交的成绩单并查看审核记录。", "review", null));
        panel.add(card, BorderLayout.NORTH);
        return panel;
    }

    private JPanel entryCard(String titleText, String hintText, String page, Runnable onEnter) {
        JPanel card = new JPanel(new BorderLayout(UiMetrics.px(16), 0));
        VCampusTheme.panel(card);
        JPanel text = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        text.setOpaque(false);
        text.add(sectionTitle(titleText), BorderLayout.NORTH);
        text.add(sectionHint(hintText), BorderLayout.SOUTH);
        JButton enter = new JButton("进入管理");
        VCampusTheme.primaryButton(enter);
        enter.addActionListener(e -> {
            managementLayout.show(managementPages, page);
            if (onEnter != null) onEnter.run();
        });
        card.add(text, BorderLayout.CENTER);
        card.add(enter, BorderLayout.EAST);
        return card;
    }

    private JPanel managementPage(String titleText, JPanel content) {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        page.setOpaque(false);
        JLabel title = sectionTitle(titleText);
        JButton back = new JButton("返回管理事项");
        VCampusTheme.secondaryButton(back);
        back.addActionListener(e -> managementLayout.show(managementPages, "landing"));
        JPanel heading = new JPanel(new BorderLayout(0, 0));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.WEST);
        heading.add(back, BorderLayout.EAST);
        page.add(heading, BorderLayout.NORTH);
        page.add(content, BorderLayout.CENTER);
        return page;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        panel.setOpaque(false);
        JLabel title = new JLabel("选课管理");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("维护课程目录、教学班、选课轮次和培养方案，并审核教师提交的成绩。 ");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        JPanel form = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(form);
        JLabel title = sectionTitle("课程目录操作");
        JLabel hint = sectionHint("新增或编辑课程均在弹窗中完成，避免列表页的输入状态互相干扰。 ");
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        actions.add(actionButton("新增课程", true, e -> openCreateCourse()));
        actions.add(actionButton("编辑所选课程", false, e -> openEditCourse()));
        actions.add(actionButton("启用/停用所选课程", false, e -> toggleCourseStatus()));
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        form.add(header, BorderLayout.NORTH);
        form.add(actions, BorderLayout.CENTER);
        panel.add(form, BorderLayout.NORTH);
        panel.add(tableCard("课程目录", "刷新后选择一门课程，即可在上方维护它的基本信息。",
                courseTable, actionButton("刷新课程目录", false, e -> loadCourses())), BorderLayout.CENTER);
        return panel;
    }

    private JPanel offeringPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        JPanel controls = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        controls.setOpaque(false);
        controls.add(offeringQueryCard(), BorderLayout.NORTH);
        controls.add(offeringFormCard(), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(tableCard("教学班列表", "选择一行后，可在上方维护任课教师、地点或容量。",
                offeringTable, actionButton("刷新当前学期教学班", false, e -> loadOfferings())),
                BorderLayout.CENTER);
        return panel;
    }

    /** 教学班按“查询学期、填写信息、维护选中项”三个层次组织。 */
    private JPanel offeringQueryCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("第 1 步：查询教学班");
        JLabel hint = sectionHint("先填写学期并刷新列表，再选择教学班进行维护。 ");
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        fields.setOpaque(false);
        VCampusTheme.field(term);
        fields.add(new JLabel("学期"));
        fields.add(term);
        fields.add(actionButton("刷新教学班", false, e -> loadOfferings()));
        card.add(title, BorderLayout.NORTH);
        card.add(fields, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    private JPanel offeringFormCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("第 2 步：新增或编辑教学班");
        JLabel hint = sectionHint("弹窗内可搜索并选择在职教师，同时维护容量和结构化排课。 ");
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        actions.add(actionButton("新增教学班", true, e -> openCreateOffering()));
        actions.add(actionButton("编辑所选教学班", false, e -> openEditOffering()));
        actions.add(actionButton("开放/关闭所选教学班", false, e -> toggleOfferingStatus()));
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    private JPanel tableCard(String titleText, String hintText, JTable table, JButton refreshButton) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(panel);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        JPanel text = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        text.setOpaque(false);
        text.add(sectionTitle(titleText), BorderLayout.NORTH);
        text.add(sectionHint(hintText), BorderLayout.SOUTH);
        header.add(text, BorderLayout.CENTER);
        header.add(refreshButton, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return panel;
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

    private JButton actionButton(String text, boolean primary,
            java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        if (primary) {
            VCampusTheme.primaryButton(button);
        } else {
            VCampusTheme.secondaryButton(button);
        }
        button.addActionListener(listener);
        actions.add(button);
        return button;
    }

    private void configureTable(JTable table, int[] columnWidths) {
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < columnWidths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(columnWidths[index]));
        }
    }

    private void loadCourses() {
        request(CourseManagementCommand.listCourses(session.getToken()), response -> {
            if (!requireList(response)) return;
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof Course) {
                    Course course = (Course) item;
                    rows.add(new Object[] { course.getCourseId(), course.getName(), course.getCredits(), course.getStatus() });
                }
            }
            courseModel.replaceRows(rows);
            showStatus(rows.isEmpty() ? "当前没有课程目录记录" : "已加载 " + rows.size() + " 门课程",
                    rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void openCreateCourse() {
        Course value = CourseEditorDialog.create(this);
        if (value == null) return;
        request(CourseManagementCommand.createCourse(session.getToken(), value), response -> showSuccess(response,
                "课程已新增，请刷新课程目录"));
    }

    private void openEditCourse() {
        int row = courseTable.getSelectedRow();
        if (row < 0) {
            showStatus("请先选择一门课程", VCampusTheme.DANGER);
            return;
        }
        Course initial = new Course(String.valueOf(courseModel.getValueAt(row, 0)),
                String.valueOf(courseModel.getValueAt(row, 1)),
                ((Number) courseModel.getValueAt(row, 2)).intValue());
        Course value = CourseEditorDialog.edit(this, initial);
        if (value == null) return;
        request(CourseManagementCommand.updateCourseDetails(session.getToken(), value.getCourseId(),
                value.getName(), value.getCredits()), response -> showSuccess(response,
                        "课程信息已更新，请刷新课程目录"));
    }

    private void toggleCourseStatus() {
        int row = courseTable.getSelectedRow();
        if (row < 0) {
            showStatus("请先选择一门课程", VCampusTheme.DANGER);
            return;
        }
        CourseStatus current = (CourseStatus) courseModel.getValueAt(row, 3);
        CourseStatus target = current == CourseStatus.ACTIVE ? CourseStatus.DISABLED : CourseStatus.ACTIVE;
        String courseName = String.valueOf(courseModel.getValueAt(row, 1));
        if (!CourseUiSupport.confirmHighImpact(this, "确认修改课程状态",
                "确定将课程“" + courseName + "”设为“"
                        + (target == CourseStatus.ACTIVE ? "启用" : "停用") + "”吗？",
                "课程状态会影响后续教学班创建和选课业务。")) {
            return;
        }
        request(CourseManagementCommand.changeCourseStatus(session.getToken(),
                String.valueOf(courseModel.getValueAt(row, 0)), target), response -> showSuccess(response,
                        "课程状态已更新，请刷新课程目录"));
    }

    private void loadOfferings() {
        try {
            request(CourseManagementCommand.listOfferingsByTerm(session.getToken(), selectedTerm()), response -> {
                if (!requireList(response)) return;
                List<Object[]> rows = new ArrayList<Object[]>();
                offeringsById.clear();
                for (Object item : (List<?>) response.getPayload()) {
                    if (item instanceof CourseOffering) {
                        CourseOffering value = (CourseOffering) item;
                        offeringsById.put(value.getOfferingId(), value);
                        rows.add(new Object[] { value.getOfferingId(), value.getCourseId(), value.getTerm(),
                                value.getTeacherId(), value.getSchedule(), value.getLocation(),
                                value.getRequiredCapacity(), value.getElectiveCapacity(),
                                value.getCrossMajorCapacity(), value.getStatus() });
                    }
                }
                offeringModel.replaceRows(rows);
                showStatus(rows.isEmpty() ? "该学期没有教学班" : "已加载 " + rows.size() + " 个教学班",
                        rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
            });
        } catch (IllegalArgumentException invalid) {
            showStatus("学期不能为空", VCampusTheme.DANGER);
        }
    }

    private void openCreateOffering() {
        loadActiveTeachers(teachers -> {
            CourseOffering value = CourseOfferingEditorDialog.create(this, selectedTerm(), teachers);
            if (value == null) return;
            request(CourseManagementCommand.createOffering(session.getToken(), value), response -> showSuccess(response,
                    "教学班已新增，请刷新教学班列表"));
        });
    }

    private void openEditOffering() {
        int row = offeringTable.getSelectedRow();
        if (row < 0) {
            showStatus("请先选择一个教学班", VCampusTheme.DANGER);
            return;
        }
        CourseOffering initial = offeringsById.get(String.valueOf(offeringModel.getValueAt(row, 0)));
        if (initial == null) {
            showStatus("教学班信息已过期，请刷新当前学期列表", VCampusTheme.DANGER);
            return;
        }
        loadActiveTeachers(teachers -> {
            CourseOffering value = CourseOfferingEditorDialog.edit(this, initial, teachers);
            if (value == null) return;
            request(CourseManagementCommand.updateOfferingDetails(session.getToken(), value),
                    response -> showSuccess(response, "教学班已更新，请刷新教学班列表"));
        });
    }

    private void toggleOfferingStatus() {
        int row = offeringTable.getSelectedRow();
        if (row < 0) {
            showStatus("请先选择一个教学班", VCampusTheme.DANGER);
            return;
        }
        CourseOfferingStatus current = (CourseOfferingStatus) offeringModel.getValueAt(row, 9);
        CourseOfferingStatus target = current == CourseOfferingStatus.OPEN
                ? CourseOfferingStatus.CLOSED : CourseOfferingStatus.OPEN;
        String currentOfferingId = String.valueOf(offeringModel.getValueAt(row, 0));
        if (!CourseUiSupport.confirmHighImpact(this, "确认修改教学班状态",
                "确定将教学班“" + currentOfferingId + "”设为“"
                        + (target == CourseOfferingStatus.OPEN ? "开放" : "关闭") + "”吗？",
                "开放或关闭会直接影响学生能否在选课轮次中选择该教学班。")) {
            return;
        }
        request(CourseManagementCommand.changeOfferingStatus(session.getToken(),
                String.valueOf(offeringModel.getValueAt(row, 0)), target), response -> showSuccess(response,
                        "教学班状态已更新，请刷新教学班列表"));
    }

    /** 在弹窗打开前查询服务端已过滤的在职教师目录，避免客户端自行判断教师状态。 */
    private void loadActiveTeachers(TeacherHandler handler) {
        if (requestInProgress) return;
        requestInProgress = true;
        setInteractive(false);
        showStatus("正在加载在职教师，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) {
                    return service.activeTeachers(session.getToken());
                }
            }
            @Override protected void done() {
                boolean handedOff = false;
                try {
                    Message response = get();
                    if (response.getStatusCode() != StatusCode.OK
                            || !(response.getPayload() instanceof List<?>)) {
                        showFailure(response);
                        return;
                    }
                    List<TeacherProfile> teachers = new ArrayList<TeacherProfile>();
                    for (Object item : (List<?>) response.getPayload()) {
                        if (item instanceof TeacherProfile) teachers.add((TeacherProfile) item);
                    }
                    if (teachers.isEmpty()) {
                        showStatus("当前没有可分配的在职教师", VCampusTheme.DANGER);
                        return;
                    }
                    requestInProgress = false;
                    setInteractive(true);
                    handedOff = true;
                    handler.handle(teachers);
                } catch (Exception failure) {
                    showStatus("无法加载在职教师目录", VCampusTheme.DANGER);
                } finally {
                    if (!handedOff) {
                        requestInProgress = false;
                        setInteractive(true);
                    }
                }
            }
        }.execute();
    }

    private void request(CourseManagementCommand command, ResponseHandler handler) {
        if (requestInProgress) return;
        requestInProgress = true;
        setInteractive(false);
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) { return service.manage(command); }
            }
            @Override protected void done() {
                try { handler.handle(get()); }
                catch (Exception failure) { showStatus("无法连接选课服务器", VCampusTheme.DANGER); }
                finally { requestInProgress = false; setInteractive(true); }
            }
        }.execute();
    }

    private boolean requireList(Message response) {
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) return true;
        showFailure(response); return false;
    }

    private void showSuccess(Message response, String message) {
        if (response.getStatusCode() != StatusCode.OK) { showFailure(response); return; }
        showStatus(message, VCampusTheme.SUCCESS);
    }

    private void showFailure(Message response) {
        showStatus(response.getPayload() instanceof String ? (String) response.getPayload()
                : "服务器未能完成操作：" + response.getStatusCode(), VCampusTheme.DANGER);
    }

    private void setInteractive(boolean interactive) {
        for (JButton action : actions) action.setEnabled(interactive);
        term.setEnabled(interactive);
        courseTable.setEnabled(interactive); offeringTable.setEnabled(interactive);
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(status, message, color);
    }

    private String selectedTerm() {
        Object selected = term.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }
    private interface ResponseHandler { void handle(Message response); }
    private interface TeacherHandler { void handle(List<TeacherProfile> teachers); }
}

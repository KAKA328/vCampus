package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseStatus;
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
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
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
    private final BatchTableModel courseModel = new BatchTableModel(
            new Object[] { "课程编号", "课程名称", "学分", "状态" });
    private final BatchTableModel offeringModel = new BatchTableModel(
            new Object[] { "教学班编号", "课程编号", "学期", "教师", "时间", "地点", "必修容量", "选修容量", "跨专业容量", "状态" });
    private final JTable courseTable = new JTable(courseModel);
    private final JTable offeringTable = new JTable(offeringModel);

    private final JTextField courseId = new JTextField(10);
    private final JTextField courseName = new JTextField(12);
    private final JTextField courseCredits = new JTextField(4);
    private final JTextField term = new JTextField("2026-2027-1", 10);
    private final JTextField offeringId = new JTextField(12);
    private final JTextField offeringCourseId = new JTextField(10);
    private final JTextField teacherId = new JTextField(8);
    private final JTextField schedule = new JTextField(10);
    private final JTextField location = new JTextField(7);
    private final JTextField requiredCapacity = new JTextField(4);
    private final JTextField electiveCapacity = new JTextField(4);
    private final JTextField crossMajorCapacity = new JTextField(4);

    private final List<JButton> actions = new ArrayList<JButton>();
    private final List<JTextField> inputFields = new ArrayList<JTextField>();
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
        JTabbedPane tabs = new JTabbedPane();
        VCampusTheme.tabs(tabs);
        tabs.addTab("课程目录", catalogPanel());
        tabs.addTab("教学班", offeringPanel());
        tabs.addTab("选课轮次", new SelectionRoundManagementPanel(host, port, session));
        tabs.addTab("培养方案", new TrainingPlanManagementPanel(host, port, session));
        tabs.addTab("成绩审核", new GradeReviewPanel(host, port, session));
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body(tabs)), BorderLayout.CENTER);
        configureTable(courseTable, COURSE_COLUMN_WIDTHS);
        configureTable(offeringTable, OFFERING_COLUMN_WIDTHS);
        showStatus("请先刷新课程目录或教学班列表", VCampusTheme.MUTED);
    }

    private JPanel body(JTabbedPane tabs) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        tabs.setPreferredSize(UiMetrics.dimension(0, 520));
        tabs.setMinimumSize(UiMetrics.dimension(0, 340));
        panel.add(tabs, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
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
        JPanel form = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        VCampusTheme.panel(form);
        JLabel title = sectionTitle("课程信息");
        JLabel hint = sectionHint("填写后可新增课程；从下方列表选择一行，会自动带入信息以便修改。 ");
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(6)));
        fields.setOpaque(false);
        styleAndTrackFields(courseId, courseName, courseCredits);
        fields.add(new JLabel("课程编号")); fields.add(courseId);
        fields.add(new JLabel("课程名称")); fields.add(courseName);
        fields.add(new JLabel("学分")); fields.add(courseCredits);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        actions.add(actionButton("新增课程", true, e -> createCourse()));
        actions.add(actionButton("保存课程修改", false, e -> updateCourse()));
        actions.add(actionButton("启用/停用所选课程", false, e -> toggleCourseStatus()));
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        content.setOpaque(false);
        content.add(fields, BorderLayout.NORTH);
        content.add(actions, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        form.add(header, BorderLayout.NORTH);
        form.add(content, BorderLayout.CENTER);
        courseTable.getSelectionModel().addListSelectionListener(e -> fillCourseFields());
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
        offeringTable.getSelectionModel().addListSelectionListener(e -> fillOfferingFields());
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
        styleAndTrackFields(term);
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
        JLabel title = sectionTitle("第 2 步：新建或维护教学班");
        JLabel hint = sectionHint("上课时间在创建时确定；后续仅可修改任课教师、地点和各类容量。 ");
        JPanel identity = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        identity.setOpaque(false);
        styleAndTrackFields(offeringId, offeringCourseId, teacherId, schedule, location,
                requiredCapacity, electiveCapacity, crossMajorCapacity);
        identity.add(new JLabel("教学班编号")); identity.add(offeringId);
        identity.add(new JLabel("课程编号")); identity.add(offeringCourseId);
        identity.add(new JLabel("任课教师")); identity.add(teacherId);
        identity.add(new JLabel("上课时间")); identity.add(schedule);
        identity.add(new JLabel("上课地点")); identity.add(location);
        JPanel capacity = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10),
                UiMetrics.px(4)));
        capacity.setOpaque(false);
        capacity.add(new JLabel("必修容量")); capacity.add(requiredCapacity);
        capacity.add(new JLabel("选修容量")); capacity.add(electiveCapacity);
        capacity.add(new JLabel("跨专业容量")); capacity.add(crossMajorCapacity);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        actions.add(actionButton("新增教学班", true, e -> createOffering()));
        actions.add(actionButton("保存教师与地点", false, e -> updateOfferingTeachingInfo()));
        actions.add(actionButton("保存容量", false, e -> updateOfferingCapacities()));
        actions.add(actionButton("开放/关闭所选教学班", false, e -> toggleOfferingStatus()));
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        content.setOpaque(false);
        content.add(identity, BorderLayout.NORTH);
        content.add(capacity, BorderLayout.CENTER);
        content.add(actions, BorderLayout.SOUTH);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
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

    private void styleAndTrackFields(JTextField... fields) {
        for (JTextField field : fields) {
            VCampusTheme.field(field);
            if (!inputFields.contains(field)) {
                inputFields.add(field);
            }
        }
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

    private void createCourse() {
        try {
            request(CourseManagementCommand.createCourse(session.getToken(), new Course(text(courseId),
                    text(courseName), positive(courseCredits, "学分"))), response -> showSuccess(response,
                            "课程已新增，请刷新课程目录"));
        } catch (IllegalArgumentException invalid) {
            showStatus("课程信息填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void updateCourse() {
        try {
            request(CourseManagementCommand.updateCourseDetails(session.getToken(), text(courseId),
                    text(courseName), positive(courseCredits, "学分")), response -> showSuccess(response,
                            "课程信息已更新，请刷新课程目录"));
        } catch (IllegalArgumentException invalid) {
            showStatus("课程信息填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
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
            request(CourseManagementCommand.listOfferingsByTerm(session.getToken(), text(term)), response -> {
                if (!requireList(response)) return;
                List<Object[]> rows = new ArrayList<Object[]>();
                for (Object item : (List<?>) response.getPayload()) {
                    if (item instanceof CourseOffering) {
                        CourseOffering value = (CourseOffering) item;
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

    private void createOffering() {
        try {
            CourseOffering value = new CourseOffering(text(offeringId), text(offeringCourseId), text(term),
                    text(teacherId), text(schedule), text(location), nonNegative(requiredCapacity, "必修容量"),
                    nonNegative(electiveCapacity, "选修容量"), nonNegative(crossMajorCapacity, "跨专业容量"),
                    CourseOfferingStatus.DRAFT);
            request(CourseManagementCommand.createOffering(session.getToken(), value), response -> showSuccess(response,
                    "教学班已新增，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) {
            showStatus("教学班信息填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    private void updateOfferingCapacities() {
        try {
            request(CourseManagementCommand.changeOfferingCapacities(session.getToken(), text(offeringId),
                    nonNegative(requiredCapacity, "必修容量"), nonNegative(electiveCapacity, "选修容量"),
                    nonNegative(crossMajorCapacity, "跨专业容量")), response -> showSuccess(response,
                            "教学班容量已更新，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) {
            showStatus("容量填写不正确：" + invalid.getMessage(), VCampusTheme.DANGER);
        }
    }

    /** 仅提交教师和地点；上课时间由教学班创建时确定，本次维护不会修改它。 */
    private void updateOfferingTeachingInfo() {
        try {
            request(CourseManagementCommand.updateOfferingTeachingInfo(session.getToken(),
                    text(offeringId), text(teacherId), text(location)), response -> showSuccess(response,
                            "任课老师和上课地点已更新，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) {
            showStatus("任课老师和地点不能为空", VCampusTheme.DANGER);
        }
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

    private void fillCourseFields() {
        int row = courseTable.getSelectedRow();
        if (row < 0) return;
        courseId.setText(String.valueOf(courseModel.getValueAt(row, 0)));
        courseName.setText(String.valueOf(courseModel.getValueAt(row, 1)));
        courseCredits.setText(String.valueOf(courseModel.getValueAt(row, 2)));
    }

    private void fillOfferingFields() {
        int row = offeringTable.getSelectedRow();
        if (row < 0) return;
        offeringId.setText(String.valueOf(offeringModel.getValueAt(row, 0)));
        offeringCourseId.setText(String.valueOf(offeringModel.getValueAt(row, 1)));
        term.setText(String.valueOf(offeringModel.getValueAt(row, 2)));
        teacherId.setText(String.valueOf(offeringModel.getValueAt(row, 3)));
        schedule.setText(String.valueOf(offeringModel.getValueAt(row, 4)));
        location.setText(String.valueOf(offeringModel.getValueAt(row, 5)));
        requiredCapacity.setText(String.valueOf(offeringModel.getValueAt(row, 6)));
        electiveCapacity.setText(String.valueOf(offeringModel.getValueAt(row, 7)));
        crossMajorCapacity.setText(String.valueOf(offeringModel.getValueAt(row, 8)));
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
        for (JTextField field : inputFields) field.setEnabled(interactive);
        courseTable.setEnabled(interactive); offeringTable.setEnabled(interactive);
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(status, message, color);
    }

    private static String text(JTextField field) { return field.getText().trim(); }
    private static int positive(JTextField field, String name) { int value = nonNegative(field, name); if (value <= 0) throw new IllegalArgumentException(name + "必须大于 0"); return value; }
    private static int nonNegative(JTextField field, String name) { try { int value = Integer.parseInt(text(field)); if (value < 0) throw new IllegalArgumentException(name + "不能小于 0"); return value; } catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + "必须是整数"); } }

    private interface ResponseHandler { void handle(Message response); }
}

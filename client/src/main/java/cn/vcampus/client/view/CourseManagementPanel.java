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
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

/** 教务管理员维护课程目录和具体教学班的页面。 */
public final class CourseManagementPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JLabel status = new JLabel("请先刷新课程目录或教学班列表");
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
        setLayout(new BorderLayout(8, 8));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("课程目录", catalogPanel());
        tabs.addTab("教学班", offeringPanel());
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        configureTable(courseTable);
        configureTable(offeringTable);
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("课程编号")); form.add(courseId);
        form.add(new JLabel("名称")); form.add(courseName);
        form.add(new JLabel("学分")); form.add(courseCredits);
        form.add(actionButton("刷新目录", e -> loadCourses()));
        form.add(actionButton("新增课程", e -> createCourse()));
        form.add(actionButton("更新课程", e -> updateCourse()));
        form.add(actionButton("启用/停用", e -> toggleCourseStatus()));
        courseTable.getSelectionModel().addListSelectionListener(e -> fillCourseFields());
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(courseTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel offeringPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("学期")); form.add(term);
        form.add(new JLabel("教学班")); form.add(offeringId);
        form.add(new JLabel("课程")); form.add(offeringCourseId);
        form.add(new JLabel("教师")); form.add(teacherId);
        form.add(new JLabel("时间")); form.add(schedule);
        form.add(new JLabel("地点")); form.add(location);
        form.add(new JLabel("必修")); form.add(requiredCapacity);
        form.add(new JLabel("选修")); form.add(electiveCapacity);
        form.add(new JLabel("跨专业")); form.add(crossMajorCapacity);
        form.add(actionButton("刷新教学班", e -> loadOfferings()));
        form.add(actionButton("新增教学班", e -> createOffering()));
        form.add(actionButton("更新教师/地点", e -> updateOfferingTeachingInfo()));
        form.add(actionButton("更新容量", e -> updateOfferingCapacities()));
        form.add(actionButton("开放/关闭", e -> toggleOfferingStatus()));
        offeringTable.getSelectionModel().addListSelectionListener(e -> fillOfferingFields());
        panel.add(form, BorderLayout.NORTH);
        panel.add(new JScrollPane(offeringTable), BorderLayout.CENTER);
        return panel;
    }

    private JButton actionButton(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.addActionListener(listener);
        actions.add(button);
        return button;
    }

    private void configureTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(25);
        table.getTableHeader().setReorderingAllowed(false);
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
            status.setText("已加载 " + rows.size() + " 门课程");
        });
    }

    private void createCourse() {
        try {
            request(CourseManagementCommand.createCourse(session.getToken(), new Course(text(courseId),
                    text(courseName), positive(courseCredits, "学分"))), response -> showSuccess(response,
                            "课程已新增，请刷新课程目录"));
        } catch (IllegalArgumentException invalid) { status.setText("课程信息填写不正确：" + invalid.getMessage()); }
    }

    private void updateCourse() {
        try {
            request(CourseManagementCommand.updateCourseDetails(session.getToken(), text(courseId),
                    text(courseName), positive(courseCredits, "学分")), response -> showSuccess(response,
                            "课程信息已更新，请刷新课程目录"));
        } catch (IllegalArgumentException invalid) { status.setText("课程信息填写不正确：" + invalid.getMessage()); }
    }

    private void toggleCourseStatus() {
        int row = courseTable.getSelectedRow();
        if (row < 0) { status.setText("请先选择一门课程"); return; }
        CourseStatus current = (CourseStatus) courseModel.getValueAt(row, 3);
        CourseStatus target = current == CourseStatus.ACTIVE ? CourseStatus.DISABLED : CourseStatus.ACTIVE;
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
                status.setText("已加载 " + rows.size() + " 个教学班");
            });
        } catch (IllegalArgumentException invalid) { status.setText("学期不能为空"); }
    }

    private void createOffering() {
        try {
            CourseOffering value = new CourseOffering(text(offeringId), text(offeringCourseId), text(term),
                    text(teacherId), text(schedule), text(location), nonNegative(requiredCapacity, "必修容量"),
                    nonNegative(electiveCapacity, "选修容量"), nonNegative(crossMajorCapacity, "跨专业容量"),
                    CourseOfferingStatus.DRAFT);
            request(CourseManagementCommand.createOffering(session.getToken(), value), response -> showSuccess(response,
                    "教学班已新增，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) { status.setText("教学班信息填写不正确：" + invalid.getMessage()); }
    }

    private void updateOfferingCapacities() {
        try {
            request(CourseManagementCommand.changeOfferingCapacities(session.getToken(), text(offeringId),
                    nonNegative(requiredCapacity, "必修容量"), nonNegative(electiveCapacity, "选修容量"),
                    nonNegative(crossMajorCapacity, "跨专业容量")), response -> showSuccess(response,
                            "教学班容量已更新，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) { status.setText("容量填写不正确：" + invalid.getMessage()); }
    }

    /** 仅提交教师和地点；上课时间由教学班创建时确定，本次维护不会修改它。 */
    private void updateOfferingTeachingInfo() {
        try {
            request(CourseManagementCommand.updateOfferingTeachingInfo(session.getToken(),
                    text(offeringId), text(teacherId), text(location)), response -> showSuccess(response,
                            "任课老师和上课地点已更新，请刷新教学班列表"));
        } catch (IllegalArgumentException invalid) {
            status.setText("任课老师和地点不能为空");
        }
    }

    private void toggleOfferingStatus() {
        int row = offeringTable.getSelectedRow();
        if (row < 0) { status.setText("请先选择一个教学班"); return; }
        CourseOfferingStatus current = (CourseOfferingStatus) offeringModel.getValueAt(row, 9);
        CourseOfferingStatus target = current == CourseOfferingStatus.OPEN
                ? CourseOfferingStatus.CLOSED : CourseOfferingStatus.OPEN;
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
        status.setText("正在请求服务器，请稍候…");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) { return service.manage(command); }
            }
            @Override protected void done() {
                try { handler.handle(get()); }
                catch (Exception failure) { status.setText("无法连接选课服务器"); }
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
        status.setText(message);
    }

    private void showFailure(Message response) {
        status.setText(response.getPayload() instanceof String ? (String) response.getPayload()
                : "服务器未能完成操作：" + response.getStatusCode());
    }

    private void setInteractive(boolean interactive) {
        for (JButton action : actions) action.setEnabled(interactive);
        courseTable.setEnabled(interactive); offeringTable.setEnabled(interactive);
    }

    private static String text(JTextField field) { return field.getText().trim(); }
    private static int positive(JTextField field, String name) { int value = nonNegative(field, name); if (value <= 0) throw new IllegalArgumentException(name + "必须大于 0"); return value; }
    private static int nonNegative(JTextField field, String name) { try { int value = Integer.parseInt(text(field)); if (value < 0) throw new IllegalArgumentException(name + "不能小于 0"); return value; } catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + "必须是整数"); } }

    private interface ResponseHandler { void handle(Message response); }
}

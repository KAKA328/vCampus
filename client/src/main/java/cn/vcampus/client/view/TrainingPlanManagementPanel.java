package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanStatus;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/** 教务端维护专业、入学年份对应培养方案及其课程要求的页面。 */
final class TrainingPlanManagementPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel planModel = new BatchTableModel(new Object[] {
            "培养方案编号", "专业", "入学年份", "课程数", "状态" });
    private final BatchTableModel courseModel = new BatchTableModel(new Object[] {
            "课程编号", "建议学期", "类别", "允许跨专业" });
    private final JTable planTable = new JTable(planModel);
    private final JTable courseTable = new JTable(courseModel);
    private final List<TrainingPlan> plans = new ArrayList<TrainingPlan>();
    private final JButton refreshButton = new JButton("刷新培养方案");
    private final JButton createButton = new JButton("新建方案");
    private final JButton editPlanButton = new JButton("编辑所选方案");
    private final JButton saveCourseButton = new JButton("新增/编辑课程要求");
    private final JButton removeCourseButton = new JButton("移除课程要求");
    private final JButton changeStatusButton = new JButton("变更方案状态");
    private final JLabel statusHint = new JLabel();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private boolean requestInProgress;

    TrainingPlanManagementPanel(String host, int port, Session session) {
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
        refreshButton.addActionListener(e -> loadPlans());
        createButton.addActionListener(e -> createPlan());
        editPlanButton.addActionListener(e -> editPlan());
        saveCourseButton.addActionListener(e -> saveCourse());
        removeCourseButton.addActionListener(e -> removeCourse());
        changeStatusButton.addActionListener(e -> changeStatus());
        planTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillPlanFields();
            }
        });

        add(controls(), BorderLayout.NORTH);
        add(workspace(), BorderLayout.CENTER);
        add(statusHint, BorderLayout.SOUTH);
        showStatus("正在自动加载培养方案", VCampusTheme.MUTED);
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadPlans);
    }

    private JPanel controls() {
        JPanel controls = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        controls.setOpaque(false);
        controls.add(planFormCard(), BorderLayout.NORTH);
        controls.add(courseFormCard(), BorderLayout.CENTER);
        return controls;
    }

    /** 先确定培养方案，再维护它包含的课程要求，避免课程被写入错误方案。 */
    private JPanel planFormCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("培养方案操作");
        JLabel hint = sectionHint("新建和编辑均在弹窗中完成；新建时同时填写首条课程要求。 ");
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        addSecondary(actions, refreshButton);
        addPrimary(actions, createButton);
        addSecondary(actions, editPlanButton);
        addSecondary(actions, changeStatusButton);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    private JPanel courseFormCard() {
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = sectionTitle("维护方案课程要求");
        JLabel hint = sectionHint("选择培养方案和课程要求后，以弹窗新增或编辑课程要求。 ");
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        actions.setOpaque(false);
        addPrimary(actions, saveCourseButton);
        addSecondary(actions, removeCourseButton);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        header.setOpaque(false);
        header.add(title, BorderLayout.NORTH);
        header.add(hint, BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(actions, BorderLayout.CENTER);
        return card;
    }

    private JSplitPane workspace() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, planCard(), courseCard());
        split.setBorder(null);
        split.setOpaque(false);
        split.setResizeWeight(0.43);
        split.setDividerSize(UiMetrics.px(10));
        split.setPreferredSize(UiMetrics.dimension(0, 520));
        split.setMinimumSize(UiMetrics.dimension(0, 360));
        return split;
    }

    private JPanel planCard() {
        JPanel panel = card("培养方案列表", "选择一行后，课程要求会显示在下方。", planTable);
        configureTable(planTable, 200, 180, 100, 80, 100);
        return panel;
    }

    private JPanel courseCard() {
        JPanel panel = card("当前方案的课程要求", "选择一行后，可在上方修改或移除该课程要求。", courseTable);
        configureTable(courseTable, 160, 110, 140, 140);
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

    private void loadPlans() {
        request(service -> service.listTrainingPlans(session.getToken()), response -> {
            if (!requireList(response, "培养方案")) {
                return;
            }
            plans.clear();
            List<Object[]> planRows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof TrainingPlan) {
                    TrainingPlan plan = (TrainingPlan) item;
                    plans.add(plan);
                    planRows.add(new Object[] { plan.getPlanId(), plan.getMajorName(),
                            Integer.valueOf(plan.getEnrollmentYear()),
                            Integer.valueOf(plan.getCourses().size()), planStatusText(plan.getStatus()) });
                }
            }
            planModel.replaceRows(planRows);
            courseModel.replaceRows(new ArrayList<Object[]>());
            showStatus(planRows.isEmpty() ? "当前没有已维护的培养方案" : "已加载 "
                    + planRows.size() + " 份培养方案",
                    planRows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void createPlan() {
        TrainingPlan plan = TrainingPlanEditorDialog.create(this);
        if (plan == null) return;
        request(service -> service.createTrainingPlan(session.getToken(), plan),
                response -> showSuccess(response, "培养方案已新建，请刷新列表确认"));
    }

    private void editPlan() {
        TrainingPlan selected = selectedPlan();
        if (selected == null) {
            showStatus("请先选择一份培养方案", VCampusTheme.DANGER);
            return;
        }
        TrainingPlan changed = TrainingPlanEditorDialog.edit(this, selected);
        if (changed == null) return;
        request(service -> service.updateTrainingPlanBasicInfo(session.getToken(), changed),
                response -> showSuccess(response, "培养方案基本信息已更新，请刷新列表确认"));
    }

    private void saveCourse() {
        TrainingPlan selectedPlan = selectedPlan();
        if (selectedPlan == null) {
            showStatus("请先选择一份培养方案", VCampusTheme.DANGER);
            return;
        }
        TrainingPlanCourse selectedCourse = selectedCourse();
        TrainingPlanCourse course = TrainingPlanEditorDialog.editCourse(this, selectedCourse);
        if (course == null) return;
        request(service -> service.saveTrainingPlanCourse(session.getToken(), selectedPlan.getPlanId(), course),
                response -> showSuccess(response, "课程要求已保存，请刷新列表确认"));
    }

    private void removeCourse() {
        TrainingPlan selectedPlan = selectedPlan();
        TrainingPlanCourse selectedCourse = selectedCourse();
        if (selectedPlan == null || selectedCourse == null) {
            showStatus("请先选择培养方案及要移除的课程要求", VCampusTheme.DANGER);
            return;
        }
        if (!CourseUiSupport.confirmHighImpact(this, "确认移除课程要求",
                "确定从培养方案“" + selectedPlan.getPlanId() + "”中移除课程“"
                        + selectedCourse.getCourseId() + "”吗？",
                "移除后，该课程将不再作为此方案学生的课程要求。")) return;
        request(service -> service.removeTrainingPlanCourse(session.getToken(), selectedPlan.getPlanId(),
                selectedCourse.getCourseId()), response -> showSuccess(response,
                "课程要求已移除，请刷新列表确认"));
    }

    private void changeStatus() {
        TrainingPlan selected = selectedPlan();
        if (selected == null) { showStatus("请先选择一份培养方案", VCampusTheme.DANGER); return; }
        JComboBox<TrainingPlanStatus> statuses = new JComboBox<TrainingPlanStatus>(TrainingPlanStatus.values());
        statuses.setSelectedItem(selected.getStatus()); VCampusTheme.roundedField(statuses);
        if (JOptionPane.showConfirmDialog(this, statuses, "变更方案状态", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        TrainingPlanStatus target = (TrainingPlanStatus) statuses.getSelectedItem();
        if (!CourseUiSupport.confirmHighImpact(this, "确认修改方案状态",
                "确定将培养方案“" + selected.getPlanId() + "”设为“" + planStatusText(target) + "”吗？",
                "发布后会影响对应专业和入学年份学生可见的课程要求。")) return;
        request(service -> service.changeTrainingPlanStatus(session.getToken(), selected.getPlanId(), target),
                response -> showSuccess(response, "培养方案状态已更新，请刷新列表确认"));
    }

    private TrainingPlan selectedPlan() {
        int row = planTable.getSelectedRow();
        return row >= 0 && row < plans.size() ? plans.get(row) : null;
    }

    private TrainingPlanCourse selectedCourse() {
        TrainingPlan plan = selectedPlan();
        int row = courseTable.getSelectedRow();
        return plan == null || row < 0 || row >= plan.getCourses().size()
                ? null : plan.getCourses().get(row);
    }

    private void fillPlanFields() {
        int row = planTable.getSelectedRow();
        if (row < 0 || row >= plans.size()) {
            return;
        }
        TrainingPlan plan = plans.get(row);
        List<Object[]> courseRows = new ArrayList<Object[]>();
        for (TrainingPlanCourse course : plan.getCourses()) {
            courseRows.add(new Object[] { course.getCourseId(),
                    Integer.valueOf(course.getRecommendedTerm()),
                    course.getSelectionType().getDisplayName(),
                    course.isCrossMajorAllowed() ? "是" : "否" });
        }
        courseModel.replaceRows(courseRows);
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

    private void showSuccess(Message response, String message) {
        if (response.getStatusCode() == StatusCode.OK) {
            showStatus(message, VCampusTheme.SUCCESS);
        } else {
            showFailure(response);
        }
    }

    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        String message = response.getPayload() instanceof String ? (String) response.getPayload() : fallback;
        showStatus(message, VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(statusHint, message, color);
    }

    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        refreshButton.setEnabled(interactive);
        createButton.setEnabled(interactive);
        editPlanButton.setEnabled(interactive);
        saveCourseButton.setEnabled(interactive);
        removeCourseButton.setEnabled(interactive);
        changeStatusButton.setEnabled(interactive);
        planTable.setEnabled(interactive);
        courseTable.setEnabled(interactive);
    }

    private static void configureTable(JTable table, int... widths) {
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(widths[index]));
        }
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(java.awt.Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private JLabel sectionHint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(VCampusTheme.MUTED);
        return label;
    }

    private static void addPrimary(JPanel parent, JButton button) {
        VCampusTheme.primaryButton(button);
        parent.add(button);
    }

    private static void addSecondary(JPanel parent, JButton button) {
        VCampusTheme.secondaryButton(button);
        parent.add(button);
    }

    private static String planStatusText(TrainingPlanStatus value) {
        if (value == TrainingPlanStatus.DRAFT) {
            return "草稿";
        }
        if (value == TrainingPlanStatus.PUBLISHED) {
            return "已发布";
        }
        return "已归档";
    }

    private interface Request {
        Message run(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface Response {
        void handle(Message response);
    }
}

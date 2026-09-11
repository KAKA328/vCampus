package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.Course;
import cn.vcampus.course.CourseManagementCommand;
import cn.vcampus.course.CourseStatus;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import cn.vcampus.course.TrainingPlanStatus;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** 教务端以“方案列表—方案详情—方案课程维护”维护培养方案。 */
final class TrainingPlanManagementPanel extends JPanel {
    private static final String LIST_CARD = "list";
    private static final String DETAIL_CARD = "detail";

    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel planModel = new BatchTableModel(new Object[] {
            "培养方案编号", "专业", "入学年份", "课程数", "状态" });
    private final BatchTableModel courseModel = new BatchTableModel(new Object[] {
            "课程编号", "建议学期", "课程类别", "允许跨专业" });
    private final JTable planTable = new JTable(planModel);
    private final JTable courseTable = new JTable(courseModel);
    private final List<TrainingPlan> plans = new ArrayList<TrainingPlan>();
    private final JButton refreshButton = new JButton("刷新培养方案");
    private final JButton createButton = new JButton("新建方案");
    private final JButton viewDetailButton = new JButton("查看方案详情");
    private final JButton editPlanButton = new JButton("编辑方案信息");
    private final JButton saveCourseButton = new JButton("新增/编辑课程要求");
    private final JButton removeCourseButton = new JButton("移除课程要求");
    private final JButton changeStatusButton = new JButton("变更方案状态");
    private final JButton backButton = new JButton("返回方案列表");
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailSummary = new JLabel();
    private final JLabel statusHint = new JLabel();
    private final JPanel cards = new JPanel(new CardLayout());
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private TrainingPlan currentPlan;
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
        viewDetailButton.addActionListener(e -> showSelectedPlanDetail());
        backButton.addActionListener(e -> showList());
        editPlanButton.addActionListener(e -> editPlan());
        saveCourseButton.addActionListener(e -> saveCourse());
        removeCourseButton.addActionListener(e -> removeCourse());
        changeStatusButton.addActionListener(e -> changeStatus());
        cards.setOpaque(false);
        cards.add(listPage(), LIST_CARD);
        cards.add(detailPage(), DETAIL_CARD);
        add(cards, BorderLayout.CENTER);
        add(statusHint, BorderLayout.SOUTH);
        showStatus("正在自动加载培养方案", VCampusTheme.MUTED);
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadPlans);
    }

    private JPanel listPage() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        page.setOpaque(false);
        JPanel actions = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(actions);
        actions.add(sectionTitle("培养方案列表"), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        content.setOpaque(false);
        JLabel hint = sectionHint("先定位培养方案，再进入详情维护其课程要求。 ");
        JPanel buttons = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        buttons.setOpaque(false);
        addSecondary(buttons, refreshButton);
        addPrimary(buttons, createButton);
        addSecondary(buttons, viewDetailButton);
        content.add(hint, BorderLayout.NORTH);
        content.add(buttons, BorderLayout.CENTER);
        actions.add(content, BorderLayout.CENTER);
        page.add(actions, BorderLayout.NORTH);
        page.add(card("已维护的培养方案", "选择一行后进入方案详情。", planTable), BorderLayout.CENTER);
        configureTable(planTable, 200, 180, 100, 80, 100);
        return page;
    }

    private JPanel detailPage() {
        JPanel page = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        page.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(header);
        JPanel title = new JPanel(new BorderLayout(0, UiMetrics.px(2)));
        title.setOpaque(false);
        detailTitle.setFont(VCampusTheme.font(java.awt.Font.BOLD, 16));
        detailTitle.setForeground(VCampusTheme.PRIMARY_DARK);
        detailSummary.setForeground(VCampusTheme.MUTED);
        title.add(detailTitle, BorderLayout.NORTH);
        title.add(detailSummary, BorderLayout.SOUTH);
        JPanel planActions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        planActions.setOpaque(false);
        addSecondary(planActions, backButton);
        addSecondary(planActions, editPlanButton);
        addSecondary(planActions, changeStatusButton);
        header.add(title, BorderLayout.NORTH);
        header.add(planActions, BorderLayout.CENTER);
        page.add(header, BorderLayout.NORTH);

        JPanel courses = card("方案课程维护", "课程、建议学期和类别均使用受控输入。", courseTable);
        configureTable(courseTable, 180, 110, 120, 140);
        JPanel courseActions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        courseActions.setOpaque(false);
        addPrimary(courseActions, saveCourseButton);
        addSecondary(courseActions, removeCourseButton);
        courses.add(courseActions, BorderLayout.SOUTH);
        page.add(courses, BorderLayout.CENTER);
        return page;
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
            if (!requireList(response)) return;
            plans.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof TrainingPlan) {
                    TrainingPlan plan = (TrainingPlan) item;
                    plans.add(plan);
                    rows.add(new Object[] { plan.getPlanId(), plan.getMajorName(),
                            Integer.valueOf(plan.getEnrollmentYear()), Integer.valueOf(plan.getCourses().size()),
                            planStatusText(plan.getStatus()) });
                }
            }
            planModel.replaceRows(rows);
            showStatus(rows.isEmpty() ? "当前没有已维护的培养方案" : "已加载 " + rows.size() + " 份培养方案",
                    rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void createPlan() {
        TrainingPlan plan = TrainingPlanEditorDialog.create(this);
        if (plan == null) return;
        request(service -> service.createTrainingPlan(session.getToken(), plan),
                response -> showSuccessThenReload(response, "培养方案已新建，可进入详情维护课程"));
    }

    private void showSelectedPlanDetail() {
        int row = planTable.getSelectedRow();
        if (row < 0 || row >= plans.size()) {
            showStatus("请先选择一份培养方案", VCampusTheme.DANGER);
            return;
        }
        currentPlan = plans.get(row);
        refreshDetail();
        ((CardLayout) cards.getLayout()).show(cards, DETAIL_CARD);
    }

    private void showList() {
        currentPlan = null;
        ((CardLayout) cards.getLayout()).show(cards, LIST_CARD);
    }

    private void refreshDetail() {
        if (currentPlan == null) return;
        detailTitle.setText("方案详情：" + currentPlan.getPlanId());
        detailSummary.setText(currentPlan.getMajorName() + " · " + currentPlan.getEnrollmentYear() + " 级 · "
                + planStatusText(currentPlan.getStatus()) + " · " + currentPlan.getCourses().size() + " 门课程");
        List<Object[]> rows = new ArrayList<Object[]>();
        for (TrainingPlanCourse course : currentPlan.getCourses()) {
            rows.add(new Object[] { course.getCourseId(), Integer.valueOf(course.getRecommendedTerm()),
                    categoryText(course), course.isCrossMajorAllowed() ? "是" : "否" });
        }
        courseModel.replaceRows(rows);
    }

    private void editPlan() {
        if (currentPlan == null) return;
        TrainingPlan changed = TrainingPlanEditorDialog.edit(this, currentPlan);
        if (changed == null) return;
        request(service -> service.updateTrainingPlanBasicInfo(session.getToken(), changed),
                response -> showSuccessThenReload(response, "培养方案基本信息已更新"));
    }

    private void saveCourse() {
        if (currentPlan == null) return;
        request(service -> service.manage(CourseManagementCommand.listCourses(session.getToken())), response -> {
            if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
                showFailure(response);
                return;
            }
            List<Course> courses = new ArrayList<Course>();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof Course && ((Course) item).getStatus() == CourseStatus.ACTIVE) courses.add((Course) item);
            }
            if (courses.isEmpty()) {
                showStatus("当前没有可用于培养方案的启用课程", VCampusTheme.DANGER);
                return;
            }
            TrainingPlan plan = currentPlan;
            TrainingPlanCourse selected = selectedCourse();
            SwingUtilities.invokeLater(() -> openCourseEditor(plan, selected, courses));
        });
    }

    private void openCourseEditor(TrainingPlan plan, TrainingPlanCourse selected, List<Course> courses) {
        if (currentPlan == null || !currentPlan.getPlanId().equals(plan.getPlanId())) return;
        TrainingPlanCourse changed = TrainingPlanEditorDialog.editCourse(this, selected, courses);
        if (changed == null) return;
        request(service -> service.saveTrainingPlanCourse(session.getToken(), plan.getPlanId(), changed),
                response -> showSuccessThenReload(response, "课程要求已保存"));
    }

    private void removeCourse() {
        TrainingPlanCourse selected = selectedCourse();
        if (currentPlan == null || selected == null) {
            showStatus("请先选择要移除的课程要求", VCampusTheme.DANGER);
            return;
        }
        if (!CourseUiSupport.confirmHighImpact(this, "确认移除课程要求",
                "确定从培养方案“" + currentPlan.getPlanId() + "”中移除课程“" + selected.getCourseId() + "”吗？",
                "移除后，该课程不再作为此方案学生的课程要求。")) return;
        request(service -> service.removeTrainingPlanCourse(session.getToken(), currentPlan.getPlanId(), selected.getCourseId()),
                response -> showSuccessThenReload(response, "课程要求已移除"));
    }

    private void changeStatus() {
        if (currentPlan == null) return;
        TrainingPlanStatus target = nextStatus(currentPlan.getStatus());
        if (target == null) {
            showStatus("已归档方案不可再变更状态", VCampusTheme.DANGER);
            return;
        }
        if (!CourseUiSupport.confirmHighImpact(this, "确认修改方案状态",
                "确定将培养方案“" + currentPlan.getPlanId() + "”设为“" + planStatusText(target) + "”吗？",
                "发布后会影响对应专业和入学年份学生可见的课程要求。")) return;
        request(service -> service.changeTrainingPlanStatus(session.getToken(), currentPlan.getPlanId(), target),
                response -> showSuccessThenReload(response, "培养方案状态已更新"));
    }

    private TrainingPlanCourse selectedCourse() {
        int row = courseTable.getSelectedRow();
        return currentPlan == null || row < 0 || row >= currentPlan.getCourses().size() ? null
                : currentPlan.getCourses().get(row);
    }

    private void showSuccessThenReload(Message response, String message) {
        if (response.getStatusCode() != StatusCode.OK) { showFailure(response); return; }
        showStatus(message, VCampusTheme.SUCCESS);
        String planId = currentPlan == null ? null : currentPlan.getPlanId();
        SwingUtilities.invokeLater(() -> reloadPlansAndKeepDetail(planId));
    }

    private void reloadPlansAndKeepDetail(String planId) {
        request(service -> service.listTrainingPlans(session.getToken()), response -> {
            if (!requireList(response)) return;
            plans.clear();
            List<Object[]> rows = new ArrayList<Object[]>();
            TrainingPlan refreshed = null;
            for (Object item : (List<?>) response.getPayload()) if (item instanceof TrainingPlan) {
                TrainingPlan plan = (TrainingPlan) item;
                plans.add(plan);
                rows.add(new Object[] { plan.getPlanId(), plan.getMajorName(), Integer.valueOf(plan.getEnrollmentYear()),
                        Integer.valueOf(plan.getCourses().size()), planStatusText(plan.getStatus()) });
                if (plan.getPlanId().equals(planId)) refreshed = plan;
            }
            planModel.replaceRows(rows);
            currentPlan = refreshed;
            if (refreshed != null) refreshDetail(); else showList();
        });
    }

    private void request(Request request, Response response) {
        if (requestInProgress) return;
        int requestId = requestLifecycle.begin(); requestInProgress = true; updateInteractiveState();
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) { return request.run(service); }
            }
            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
                try { response.handle(get()); }
                catch (Exception failure) { showStatus("无法连接选课服务器", VCampusTheme.DANGER); }
                finally { requestInProgress = false; updateInteractiveState(); }
            }
        }.execute();
    }

    private boolean requireList(Message response) {
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) return true;
        showFailure(response); return false;
    }
    private void showFailure(Message response) {
        String fallback = "服务器未能完成操作：" + response.getStatusCode();
        showStatus(response.getPayload() instanceof String ? (String) response.getPayload() : fallback, VCampusTheme.DANGER);
    }
    private void showStatus(String message, Color color) { CourseUiSupport.showStatus(statusHint, message, color); }
    private void updateInteractiveState() {
        boolean interactive = !requestInProgress;
        refreshButton.setEnabled(interactive); createButton.setEnabled(interactive); viewDetailButton.setEnabled(interactive);
        backButton.setEnabled(interactive); editPlanButton.setEnabled(interactive); saveCourseButton.setEnabled(interactive);
        removeCourseButton.setEnabled(interactive); changeStatusButton.setEnabled(interactive);
        planTable.setEnabled(interactive); courseTable.setEnabled(interactive);
    }
    private static void configureTable(JTable table, int... widths) {
        VCampusTheme.table(table); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < widths.length; index++) table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(widths[index]));
    }
    private JLabel sectionTitle(String text) { JLabel label = new JLabel(text); label.setFont(VCampusTheme.font(java.awt.Font.BOLD, 16)); label.setForeground(VCampusTheme.PRIMARY_DARK); return label; }
    private JLabel sectionHint(String text) { JLabel label = new JLabel(text); label.setForeground(VCampusTheme.MUTED); return label; }
    private static void addPrimary(JPanel parent, JButton button) { VCampusTheme.primaryButton(button); parent.add(button); }
    private static void addSecondary(JPanel parent, JButton button) { VCampusTheme.secondaryButton(button); parent.add(button); }
    private static String planStatusText(TrainingPlanStatus value) {
        return value == TrainingPlanStatus.DRAFT ? "草稿" : value == TrainingPlanStatus.PUBLISHED ? "已发布" : "已归档";
    }
    private static TrainingPlanStatus nextStatus(TrainingPlanStatus value) {
        return value == TrainingPlanStatus.DRAFT ? TrainingPlanStatus.PUBLISHED
                : value == TrainingPlanStatus.PUBLISHED ? TrainingPlanStatus.ARCHIVED : null;
    }
    private static String categoryText(TrainingPlanCourse course) {
        return course.getSelectionType() == cn.vcampus.course.SelectionType.REQUIRED ? "必修" : "选修";
    }
    private interface Request { Message run(RemoteCourseService service) throws IOException, ClassNotFoundException; }
    private interface Response { void handle(Message response); }
}

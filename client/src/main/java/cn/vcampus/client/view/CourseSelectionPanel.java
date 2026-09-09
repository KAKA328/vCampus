package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteCourseService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.course.SelectableCourseOffering;
import cn.vcampus.course.SelectedCourseOffering;
import cn.vcampus.course.SelectionRound;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** 学生按“轮次、课程、教学班”逐层完成选课，并可随时查看或退选已选课程。 */
public final class CourseSelectionPanel extends JPanel {
    private static final String ROUND_PAGE = "round";
    private static final String COURSE_PAGE = "course";
    private static final String OFFERING_PAGE = "offering";
    private static final String SELECTED_PAGE = "selected";
    private static final int[] COURSE_COLUMN_WIDTHS = { 150, 260, 90, 130 };
    private static final int[] SELECTED_COLUMN_WIDTHS = { 130, 220, 90, 130, 190, 130 };

    private final String host;
    private final int port;
    private final Session session;
    private final CardLayout pageLayout = new CardLayout();
    private final ScrollablePagePanel pages = new ScrollablePagePanel(pageLayout);
    private final JPanel roundCards = new JPanel(new BorderLayout());
    private final JPanel offeringCards = new JPanel(new BorderLayout());
    private final JLabel pageSubtitle = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel selectedRoundLabel = new JLabel();
    private final JLabel selectedCourseLabel = new JLabel();
    private final BatchTableModel courseModel = new BatchTableModel(
            new Object[] { "课程编号", "课程名称", "学分", "选课类别" });
    private final BatchTableModel selectedModel = new BatchTableModel(
            new Object[] { "课程编号", "课程名称", "学分", "教学班", "上课时间", "地点" });
    private final JTable courseTable = new JTable(courseModel);
    private final JTable selectedTable = new JTable(selectedModel);
    private final JButton courseDetailButton = new JButton("查看所选课程的教学班");
    private final JButton selectedCoursesButton = new JButton("我的已选课程");
    private final JButton backToRoundsButton = new JButton("返回选课轮次");
    private final JButton backToCoursesButton = new JButton("返回课程列表");
    private final JButton backToCoursesFromSelectedButton = new JButton("返回课程列表");
    private final JButton dropButton = new JButton("退选所选课程");
    private final List<SelectionRound> rounds = new ArrayList<SelectionRound>();
    private final List<CourseChoice> courseChoices = new ArrayList<CourseChoice>();
    private final List<SelectableCourseOffering> currentRoundOfferings =
            new ArrayList<SelectableCourseOffering>();
    private final List<SelectedCourseOffering> selectedOfferings =
            new ArrayList<SelectedCourseOffering>();
    private final List<JButton> roundEnterButtons = new ArrayList<JButton>();
    private final Map<JButton, Boolean> offeringActionAvailability =
            new LinkedHashMap<JButton, Boolean>();
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();
    private SelectionRound selectedRound;
    private CourseChoice selectedCourse;
    /** 当前网络请求尚未结束时，禁止再次提交选课相关操作。 */
    private boolean requestInProgress;

    public CourseSelectionPanel(String host, int port, Session session) {
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
        configureTable(courseTable, COURSE_COLUMN_WIDTHS);
        configureTable(selectedTable, SELECTED_COLUMN_WIDTHS);
        courseTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateInteractiveState();
        });
        selectedTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateInteractiveState();
        });
        courseDetailButton.addActionListener(e -> openCourseDetail());
        selectedCoursesButton.addActionListener(e -> showSelectedPage());
        backToRoundsButton.addActionListener(e -> showRoundPage());
        backToCoursesButton.addActionListener(e -> showCoursePage());
        backToCoursesFromSelectedButton.addActionListener(e -> showCoursePage());
        dropButton.addActionListener(e -> dropSelectedCourse());
        VCampusTheme.primaryButton(courseDetailButton);
        VCampusTheme.secondaryButton(selectedCoursesButton);
        VCampusTheme.secondaryButton(backToRoundsButton);
        VCampusTheme.secondaryButton(backToCoursesButton);
        VCampusTheme.secondaryButton(backToCoursesFromSelectedButton);
        VCampusTheme.primaryButton(dropButton);

        pages.setOpaque(false);
        pages.add(roundPage(), ROUND_PAGE);
        pages.add(coursePage(), COURSE_PAGE);
        pages.add(offeringPage(), OFFERING_PAGE);
        pages.add(selectedPage(), SELECTED_PAGE);
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(pages), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        pageLayout.show(pages, ROUND_PAGE);
        setSubtitle("请选择一个当前开放的选课轮次，再进入课程列表。 ");
        showStatus("正在自动加载选课轮次", VCampusTheme.MUTED);
        updateInteractiveState();
        CourseUiSupport.loadOnFirstShow(this, this::loadRounds);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        panel.setOpaque(false);
        JLabel title = new JLabel("选课系统");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        pageSubtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(pageSubtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent roundPage() {
        JPanel content = scrollPage();
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(card);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        header.add(sectionTitle("请选择选课轮次"), BorderLayout.NORTH);
        header.add(sectionHint("进入轮次后即可查看可选课程；首修与重修轮次都会在开放时显示。 "),
                BorderLayout.SOUTH);
        card.add(header, BorderLayout.NORTH);
        card.add(roundCards, BorderLayout.CENTER);
        content.add(card, BorderLayout.NORTH);
        return content;
    }

    private JComponent coursePage() {
        JPanel content = scrollPage();
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(card);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(4)));
        header.setOpaque(false);
        selectedRoundLabel.setFont(VCampusTheme.font(Font.BOLD, 17));
        selectedRoundLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        header.add(selectedRoundLabel, BorderLayout.NORTH);
        header.add(sectionHint("选择一门课程后查看该课程全部教学班与剩余容量。 "),
                BorderLayout.SOUTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        actions.add(backToRoundsButton);
        actions.add(selectedCoursesButton);
        card.add(header, BorderLayout.NORTH);
        card.add(VCampusTheme.scrollPane(courseTable), BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(UiMetrics.px(12), 0));
        footer.setOpaque(false);
        footer.add(sectionHint("课程列表会在返回此页时自动刷新。 "), BorderLayout.CENTER);
        footer.add(courseDetailButton, BorderLayout.EAST);
        card.add(footer, BorderLayout.SOUTH);
        content.add(actions, BorderLayout.NORTH);
        content.add(card, BorderLayout.CENTER);
        return content;
    }

    private JComponent offeringPage() {
        JPanel content = scrollPage();
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        header.setOpaque(false);
        selectedCourseLabel.setFont(VCampusTheme.font(Font.BOLD, 18));
        selectedCourseLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        header.add(selectedCourseLabel, BorderLayout.NORTH);
        header.add(sectionHint("每个教学班均显示当前选课类别对应的剩余容量；选课成功后会自动刷新。 "),
                BorderLayout.SOUTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(backToCoursesButton);
        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        top.setOpaque(false);
        top.add(actions, BorderLayout.NORTH);
        top.add(header, BorderLayout.SOUTH);
        content.add(top, BorderLayout.NORTH);
        content.add(offeringCards, BorderLayout.CENTER);
        return content;
    }

    private JComponent selectedPage() {
        JPanel content = scrollPage();
        JPanel card = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(card);
        JPanel header = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        header.setOpaque(false);
        header.add(sectionTitle("我的已选课程"), BorderLayout.NORTH);
        header.add(sectionHint("这里只显示当前有效的选课记录；退选后会立即更新课程列表。 "),
                BorderLayout.SOUTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        actions.add(backToCoursesFromSelectedButton);
        actions.add(dropButton);
        card.add(header, BorderLayout.NORTH);
        card.add(VCampusTheme.scrollPane(selectedTable), BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        content.add(card, BorderLayout.NORTH);
        return content;
    }

    private static JPanel scrollPage() {
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(16)));
        content.setOpaque(false);
        return content;
    }

    private void loadRounds() {
        request(service -> service.availableRounds(session.getToken()), response -> {
            if (!requireList(response, "选课轮次")) return;
            rounds.clear();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof SelectionRound) rounds.add((SelectionRound) item);
            }
            renderRoundCards();
            showStatus(rounds.isEmpty() ? "当前没有开放的选课轮次" : "请选择一个选课轮次",
                    rounds.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        });
    }

    private void renderRoundCards() {
        roundCards.removeAll();
        roundEnterButtons.clear();
        JPanel list = new JPanel();
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setOpaque(false);
        if (rounds.isEmpty()) list.add(sectionHint("暂时没有处于开放时间内的选课轮次。 "));
        for (SelectionRound round : rounds) {
            list.add(roundCard(round));
            list.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        }
        roundCards.add(list, BorderLayout.NORTH);
        roundCards.revalidate();
        roundCards.repaint();
        updateInteractiveState();
    }

    private JPanel roundCard(SelectionRound round) {
        JPanel card = new JPanel(new BorderLayout(UiMetrics.px(14), 0));
        VCampusTheme.panel(card);
        JLabel title = new JLabel(round.getType().getDisplayName());
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JPanel text = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(sectionHint("学期：" + round.getTerm() + "　开放中"), BorderLayout.SOUTH);
        JButton enter = new JButton("进入" + round.getType().getDisplayName());
        VCampusTheme.primaryButton(enter);
        enter.addActionListener(e -> enterRound(round));
        roundEnterButtons.add(enter);
        card.add(text, BorderLayout.CENTER);
        card.add(enter, BorderLayout.EAST);
        return card;
    }

    private void enterRound(SelectionRound round) {
        selectedRound = round;
        selectedCourse = null;
        setSubtitle("当前轮次：" + round.getType().getDisplayName() + "（" + round.getTerm()
                + "），请选择一门课程查看教学班。 ");
        pageLayout.show(pages, COURSE_PAGE);
        loadCourseList();
    }

    private void showRoundPage() {
        selectedRound = null;
        selectedCourse = null;
        setSubtitle("请选择一个当前开放的选课轮次，再进入课程列表。 ");
        pageLayout.show(pages, ROUND_PAGE);
        loadRounds();
    }

    private void showCoursePage() {
        if (selectedRound == null) {
            showRoundPage();
            return;
        }
        setSubtitle("当前轮次：" + selectedRound.getType().getDisplayName() + "（"
                + selectedRound.getTerm() + "），请选择一门课程查看教学班。 ");
        pageLayout.show(pages, COURSE_PAGE);
        loadCourseList();
    }

    private void loadCourseList() {
        if (selectedRound != null) fetchRoundOfferings(() -> fetchSelectedOfferings(this::renderCourseList));
    }

    private void fetchRoundOfferings(final Runnable afterLoaded) {
        if (selectedRound == null) return;
        final String roundId = selectedRound.getRoundId();
        request(service -> service.availableOfferings(session.getToken(), roundId), response -> {
            if (!requireList(response, "可选课程")) return;
            currentRoundOfferings.clear();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof SelectableCourseOffering) {
                    currentRoundOfferings.add((SelectableCourseOffering) item);
                }
            }
            SwingUtilities.invokeLater(afterLoaded);
        });
    }

    private void fetchSelectedOfferings(final Runnable afterLoaded) {
        request(service -> service.selectedOfferings(session.getToken()), response -> {
            if (!requireList(response, "已选课程")) return;
            selectedOfferings.clear();
            for (Object item : (List<?>) response.getPayload()) {
                if (item instanceof SelectedCourseOffering) {
                    selectedOfferings.add((SelectedCourseOffering) item);
                }
            }
            afterLoaded.run();
        });
    }

    private void renderCourseList() {
        courseChoices.clear();
        Map<String, CourseChoice> choices = new LinkedHashMap<String, CourseChoice>();
        for (SelectableCourseOffering offering : currentRoundOfferings) {
            String courseId = offering.getCourse().getCourseId();
            CourseChoice choice = choices.get(courseId);
            if (choice == null) {
                choice = new CourseChoice(offering);
                choices.put(courseId, choice);
                courseChoices.add(choice);
            }
            choice.offerings.add(offering);
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        for (CourseChoice choice : courseChoices) {
            SelectableCourseOffering first = choice.firstOffering;
            rows.add(new Object[] { first.getCourse().getCourseId(), first.getCourse().getName(),
                    Integer.valueOf(first.getCourse().getCredits()),
                    first.getSelectionType().getDisplayName() });
        }
        courseModel.replaceRows(rows);
        selectedRoundLabel.setText(selectedRound.getType().getDisplayName() + " · "
                + selectedRound.getTerm() + " · 可选课程");
        showStatus(rows.isEmpty() ? "本轮暂时没有可选课程" : "已加载 " + rows.size() + " 门可选课程",
                rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        updateInteractiveState();
    }

    private void openCourseDetail() {
        int row = courseTable.getSelectedRow();
        if (row < 0 || row >= courseChoices.size()) {
            showStatus("请先选择一门课程", VCampusTheme.DANGER);
            return;
        }
        selectedCourse = courseChoices.get(row);
        setSubtitle("正在查看课程“" + selectedCourse.firstOffering.getCourse().getName()
                + "”的全部教学班。 ");
        pageLayout.show(pages, OFFERING_PAGE);
        refreshOfferingDetail();
    }

    private void refreshOfferingDetail() {
        if (selectedCourse == null) return;
        final String courseId = selectedCourse.firstOffering.getCourse().getCourseId();
        fetchRoundOfferings(() -> fetchSelectedOfferings(() -> {
            renderCourseListData();
            selectedCourse = findCourseChoice(courseId);
            renderOfferingCards();
        }));
    }

    private void renderCourseListData() {
        courseChoices.clear();
        Map<String, CourseChoice> choices = new LinkedHashMap<String, CourseChoice>();
        for (SelectableCourseOffering offering : currentRoundOfferings) {
            String courseId = offering.getCourse().getCourseId();
            CourseChoice choice = choices.get(courseId);
            if (choice == null) {
                choice = new CourseChoice(offering);
                choices.put(courseId, choice);
                courseChoices.add(choice);
            }
            choice.offerings.add(offering);
        }
    }

    private CourseChoice findCourseChoice(String courseId) {
        for (CourseChoice choice : courseChoices) {
            if (courseId.equals(choice.firstOffering.getCourse().getCourseId())) return choice;
        }
        return null;
    }

    private void renderOfferingCards() {
        offeringCards.removeAll();
        offeringActionAvailability.clear();
        if (selectedCourse == null) {
            offeringCards.revalidate();
            offeringCards.repaint();
            showStatus("该课程已不在当前可选列表中", VCampusTheme.MUTED);
            return;
        }
        SelectableCourseOffering first = selectedCourse.firstOffering;
        selectedCourseLabel.setText(first.getCourse().getCourseId() + " · "
                + first.getCourse().getName() + " · 教学班");
        JPanel list = new JPanel();
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        list.setOpaque(false);
        for (SelectableCourseOffering offering : selectedCourse.offerings) {
            list.add(offeringCard(offering));
            list.add(javax.swing.Box.createVerticalStrut(UiMetrics.px(10)));
        }
        offeringCards.add(list, BorderLayout.NORTH);
        offeringCards.revalidate();
        offeringCards.repaint();
        showStatus("已加载 " + selectedCourse.offerings.size() + " 个教学班", VCampusTheme.SUCCESS);
        updateInteractiveState();
    }

    private JPanel offeringCard(SelectableCourseOffering value) {
        JPanel card = new JPanel(new BorderLayout(UiMetrics.px(16), UiMetrics.px(8)));
        VCampusTheme.panel(card);
        JLabel title = new JLabel("教学班 " + value.getOffering().getOfferingId());
        title.setFont(VCampusTheme.font(Font.BOLD, 16));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel detail = new JLabel("<html>任课教师：" + escape(value.getOffering().getTeacherId())
                + "<br/>上课时间：" + escape(value.getOffering().getSchedule())
                + "<br/>上课地点：" + escape(value.getOffering().getLocation())
                + "<br/>" + escape(value.getSelectionType().getDisplayName()) + "容量："
                + value.getCapacityUsage().getRemainingCapacity() + " / "
                + value.getCapacityUsage().getTotalCapacity() + "</html>");
        detail.setForeground(VCampusTheme.TEXT);
        JPanel text = new JPanel(new BorderLayout(0, UiMetrics.px(5)));
        text.setOpaque(false);
        text.add(title, BorderLayout.NORTH);
        text.add(detail, BorderLayout.CENTER);
        JButton select = new JButton();
        boolean courseAlreadySelected = selectedOfferingForCourse(value.getCourse().getCourseId()) != null;
        boolean thisOfferingSelected = selectedOfferingForId(value.getOffering().getOfferingId()) != null;
        boolean available = !courseAlreadySelected && !value.getCapacityUsage().isFull();
        if (thisOfferingSelected) select.setText("已选择");
        else if (courseAlreadySelected) select.setText("不可选择");
        else if (value.getCapacityUsage().isFull()) select.setText("名额已满");
        else select.setText("选择此教学班");
        if (available) {
            VCampusTheme.primaryButton(select);
            select.addActionListener(e -> selectOffering(value));
        } else {
            VCampusTheme.secondaryButton(select);
        }
        offeringActionAvailability.put(select, Boolean.valueOf(available));
        card.add(text, BorderLayout.CENTER);
        card.add(select, BorderLayout.EAST);
        return card;
    }

    private void selectOffering(SelectableCourseOffering offering) {
        if (selectedRound == null) {
            showRoundPage();
            return;
        }
        request(service -> service.select(session.getToken(), selectedRound.getRoundId(),
                offering.getOffering().getOfferingId()), response -> {
                    if (!ok(response)) return;
                    showStatus("选课成功，正在更新教学班容量和已选状态", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::refreshOfferingDetail);
                });
    }

    private void showSelectedPage() {
        if (selectedRound == null) {
            showRoundPage();
            return;
        }
        setSubtitle("当前轮次：" + selectedRound.getType().getDisplayName() + "（"
                + selectedRound.getTerm() + "），查看当前有效选课记录。 ");
        pageLayout.show(pages, SELECTED_PAGE);
        fetchSelectedOfferings(this::renderSelectedCourses);
    }

    private void renderSelectedCourses() {
        List<Object[]> rows = new ArrayList<Object[]>();
        for (SelectedCourseOffering value : selectedOfferings) {
            rows.add(new Object[] { value.getCourse().getCourseId(), value.getCourse().getName(),
                    Integer.valueOf(value.getCourse().getCredits()), value.getOffering().getOfferingId(),
                    value.getOffering().getSchedule(), value.getOffering().getLocation() });
        }
        selectedModel.replaceRows(rows);
        showStatus(rows.isEmpty() ? "当前没有有效选课记录" : "已加载 " + rows.size() + " 条已选课程",
                rows.isEmpty() ? VCampusTheme.MUTED : VCampusTheme.SUCCESS);
        updateInteractiveState();
    }

    private void dropSelectedCourse() {
        int row = selectedTable.getSelectedRow();
        if (row < 0 || row >= selectedOfferings.size()) {
            showStatus("请先选择一条已选课程", VCampusTheme.DANGER);
            return;
        }
        SelectedCourseOffering target = selectedOfferings.get(row);
        if (!CourseUiSupport.confirmHighImpact(this, "确认退选",
                "确定退选课程“" + target.getCourse().getName() + "”吗？",
                "该课程将不再属于你的当前有效选课记录。")) return;
        request(service -> service.drop(session.getToken(), target.getRecord().getRecordId()), response -> {
            if (!ok(response)) return;
            showStatus("退选成功，正在更新已选课程", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::showSelectedPage);
        });
    }

    private SelectedCourseOffering selectedOfferingForCourse(String courseId) {
        for (SelectedCourseOffering offering : selectedOfferings) {
            if (courseId.equals(offering.getCourse().getCourseId())) return offering;
        }
        return null;
    }

    private SelectedCourseOffering selectedOfferingForId(String offeringId) {
        for (SelectedCourseOffering offering : selectedOfferings) {
            if (offeringId.equals(offering.getOffering().getOfferingId())) return offering;
        }
        return null;
    }

    private void request(Request request, Response response) {
        if (requestInProgress) return;
        final int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateInteractiveState();
        showStatus("正在请求服务器，请稍候…", VCampusTheme.MUTED);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteCourseService service = new RemoteCourseService(host, port)) {
                    return request.run(service);
                }
            }

            @Override protected void done() {
                if (!requestLifecycle.isCurrent(requestId)) return;
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
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof List<?>) return true;
        showFailure(response, target);
        return false;
    }

    private boolean ok(Message response) {
        if (response.getStatusCode() == StatusCode.OK) return true;
        showFailure(response, "操作");
        return false;
    }

    private void showFailure(Message response, String target) {
        String fallback = "服务器未能完成" + target + "：" + response.getStatusCode();
        showStatus(response.getPayload() instanceof String ? (String) response.getPayload() : fallback,
                VCampusTheme.DANGER);
    }

    /** 根据当前页、选中行和请求状态统一禁用无效操作，避免重复提交或跳过操作层级。 */
    private void updateInteractiveState() {
        boolean interactive = session.getUser().getRole() == Role.STUDENT && !requestInProgress;
        for (JButton enter : roundEnterButtons) enter.setEnabled(interactive);
        backToRoundsButton.setEnabled(interactive);
        selectedCoursesButton.setEnabled(interactive && selectedRound != null);
        courseTable.setEnabled(interactive);
        courseDetailButton.setEnabled(interactive && courseTable.getSelectedRow() >= 0
                && courseTable.getSelectedRow() < courseChoices.size());
        backToCoursesButton.setEnabled(interactive);
        backToCoursesFromSelectedButton.setEnabled(interactive);
        selectedTable.setEnabled(interactive);
        dropButton.setEnabled(interactive && selectedTable.getSelectedRow() >= 0
                && selectedTable.getSelectedRow() < selectedOfferings.size());
        for (Map.Entry<JButton, Boolean> entry : offeringActionAvailability.entrySet()) {
            entry.getKey().setEnabled(interactive && entry.getValue().booleanValue());
        }
    }

    private static void configureTable(JTable table, int[] widths) {
        VCampusTheme.table(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int index = 0; index < widths.length; index++) {
            table.getColumnModel().getColumn(index).setPreferredWidth(UiMetrics.px(widths[index]));
        }
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private static JLabel sectionHint(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(VCampusTheme.MUTED);
        return label;
    }

    private void setSubtitle(String text) { pageSubtitle.setText(text); }

    private void showStatus(String message, Color color) {
        CourseUiSupport.showStatus(status, message, color);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static final class CourseChoice {
        private final SelectableCourseOffering firstOffering;
        private final List<SelectableCourseOffering> offerings = new ArrayList<SelectableCourseOffering>();

        private CourseChoice(SelectableCourseOffering firstOffering) {
            this.firstOffering = firstOffering;
        }
    }

    private interface Request {
        Message run(RemoteCourseService service) throws IOException, ClassNotFoundException;
    }

    private interface Response {
        void handle(Message response);
    }
}

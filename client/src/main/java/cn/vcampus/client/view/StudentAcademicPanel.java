package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStudentService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.student.CourseHistoryRecord;
import cn.vcampus.student.CreditSummary;
import cn.vcampus.student.StudentAcademicQueryV1Command.QueryType;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;

/** Read-only self history and pending retakes; requests never contain a student id. */
final class StudentAcademicPanel extends JPanel {
    interface QueryLoader {
        Message load(QueryType type) throws Exception;
    }

    private final QueryLoader loader;
    private final JComboBox<String> query = new JComboBox<String>(
            new String[] {"课程历史", "待重修课程", "当前学分"});
    private final JButton refresh = new JButton("查询 / 刷新");
    private final JLabel status = new JLabel("选择查询内容后点击查询。");
    private final BatchTableModel model = new BatchTableModel(new Object[] {
            "课程编号", "课程名称", "学期", "尝试次数", "修读类型", "成绩", "是否通过", "本次获得学分"});
    private final JTable table = new JTable(model);
    private final JPanel content = new JPanel(new java.awt.CardLayout());
    private final JLabel earned = new JLabel("—");
    private final JLabel passed = new JLabel("—");
    private final JLabel pending = new JLabel("—");
    private final JLabel retakes = new JLabel("—");
    private final JLabel creditOwner = new JLabel("查询后显示本人学分概况");
    private long requestVersion;

    StudentAcademicPanel(String host, int port, Session session) {
        this(type -> {
            try (RemoteStudentService service = new RemoteStudentService(host, port)) {
                return service.academicQuery(session.getToken(), type);
            }
        });
    }

    StudentAcademicPanel(QueryLoader loader) {
        if (loader == null) throw new IllegalArgumentException("loader is required");
        this.loader = loader;
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);
        JPanel toolbar = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 6));
        VCampusTheme.panel(toolbar);
        toolbar.add(new JLabel("查询内容"));
        toolbar.add(query);
        VCampusTheme.primaryButton(refresh);
        toolbar.add(refresh);
        add(toolbar, BorderLayout.NORTH);

        VCampusTheme.table(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {110, 170, 135, 85, 90, 70, 85, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane scroll = VCampusTheme.scrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(0, 340));
        content.setOpaque(false);
        content.add(scroll, "history");
        JPanel overview = new ScrollablePagePanel(new BorderLayout(0, 16));
        ResponsiveCardRowPanel cards = new ResponsiveCardRowPanel(155, 12);
        cards.add(AcademicViewComponents.metric("累计获得学分", earned, "通过课程去重累计"));
        cards.add(AcademicViewComponents.metric("已通过课程", passed, "同一课程计一次"));
        cards.add(AcademicViewComponents.metric("当前待重修", pending, "仍未通过的课程"));
        cards.add(AcademicViewComponents.metric("历史重修课程", retakes, "曾有重修尝试"));
        overview.add(cards, BorderLayout.NORTH);
        JPanel explanation = new JPanel(new BorderLayout(0, 10));
        explanation.setOpaque(false);
        explanation.add(creditOwner, BorderLayout.NORTH);
        JLabel rule = new JLabel("<html>同一课程多次通过，按最高通过学分计入一次。"
                + "<br>当前学分反映学业进度；毕业资格由教务核查并确认。</html>");
        rule.setForeground(VCampusTheme.MUTED);
        explanation.add(rule, BorderLayout.CENTER);
        overview.add(AcademicViewComponents.section("统计口径", explanation), BorderLayout.CENTER);
        content.add(VCampusTheme.pageScroll(overview), "credits");
        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        JLabel hint = new JLabel("<html>待重修：同一课程尚无通过记录；曾挂科后通过的课程不再列入。"
                + "<br>课程历史展示每次修读学分；当前学分展示通过课程去重后的累计值。毕业资格以教务审核为准。</html>");
        hint.setForeground(VCampusTheme.MUTED);
        footer.add(hint, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        refresh.addActionListener(event -> reload());
        query.addActionListener(event -> reload());
    }

    void reload() {
        final long version = ++requestVersion;
        final QueryType type = QueryType.values()[query.getSelectedIndex()];
        clearSummary();
        ((java.awt.CardLayout) content.getLayout()).show(content, type == QueryType.CREDITS ? "credits" : "history");
        model.replaceRows(Collections.<Object[]>emptyList());
        if (type == QueryType.CREDITS) {
            model.setColumnIdentifiers(new Object[] {"学号", "当前累计学分", "通过课程", "待重修课程", "历史重修课程"});
        } else {
            model.setColumnIdentifiers(new Object[] {"课程编号", "课程名称", "学期", "尝试次数",
                    "修读类型", "成绩", "是否通过", "本次获得学分"});
        }
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setPreferredWidth(140);
        AcademicViewComponents.sortable(table);
        refresh.setEnabled(false);
        status.setText("正在查询…");
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                return loader.load(type);
            }

            @Override protected void done() {
                if (version != requestVersion) return;
                refresh.setEnabled(true);
                try {
                    Message response = get();
                    if (response == null || response.getStatusCode() != StatusCode.OK) {
                        status.setText(errorText(response == null ? null : response.getStatusCode()));
                        return;
                    }
                    if (type == QueryType.CREDITS) {
                        if (!(response.getPayload() instanceof CreditSummary)) throw new IllegalArgumentException("invalid credits");
                        CreditSummary summary = (CreditSummary) response.getPayload();
                        earned.setText(String.valueOf(summary.getEarnedCredits()));
                        passed.setText(String.valueOf(summary.getPassedCourses()));
                        pending.setText(String.valueOf(summary.getPendingRetakes()));
                        retakes.setText(String.valueOf(summary.getHistoricalRetakes()));
                        pending.setForeground(summary.getPendingRetakes() == 0 ? VCampusTheme.PRIMARY : VCampusTheme.DANGER);
                        creditOwner.setText("本人学号：" + summary.getStudentId());
                        model.replaceRows(Collections.singletonList(new Object[] {summary.getStudentId(),
                                summary.getEarnedCredits(), summary.getPassedCourses(),
                                summary.getPendingRetakes(), summary.getHistoricalRetakes()}));
                        status.setText("当前累计学分按通过课程去重统计；同一课程取最高通过学分。");
                        return;
                    }
                    List<Object[]> rows = rows(response.getPayload());
                    model.replaceRows(rows);
                    status.setText(rows.isEmpty()
                            ? (type == QueryType.HISTORY ? "暂无课程成绩记录。" : "当前没有待重修课程。")
                            : (type == QueryType.HISTORY ? "课程历史：" : "待重修课程：") + rows.size() + " 条");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    status.setText("查询已中断，请重试。");
                } catch (Exception failure) {
                    status.setText("查询失败，请检查连接后重试。");
                }
            }
        }.execute();
    }

    @Override public void removeNotify() {
        clearSummary();
        requestVersion++;
        refresh.setEnabled(true);
        model.replaceRows(Collections.<Object[]>emptyList());
        status.setText("选择查询内容后点击查询。");
        super.removeNotify();
    }

    private void clearSummary() {
        for (JLabel label : new JLabel[] {earned, passed, pending, retakes}) label.setText("—");
        creditOwner.setText("查询后显示本人学分概况");
    }

    private static List<Object[]> rows(Object payload) {
        if (!(payload instanceof List<?>)) throw new IllegalArgumentException("invalid history response");
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : (List<?>) payload) {
            if (!(item instanceof CourseHistoryRecord)) {
                throw new IllegalArgumentException("invalid history row");
            }
            CourseHistoryRecord record = (CourseHistoryRecord) item;
            rows.add(new Object[] {record.getCourseId(), record.getCourseName(), record.getSemester(),
                    record.getAttemptNo(), record.getAttemptType(), record.getScore(),
                    record.isPassed() ? "通过" : "未通过", record.getEarnedCredits()});
        }
        return rows;
    }

    private static String errorText(StatusCode code) {
        if (code == StatusCode.UNAUTHORIZED) return "登录已失效，请重新登录。";
        if (code == StatusCode.FORBIDDEN) return "当前账号无权查询学生本人成绩。";
        if (code == StatusCode.NOT_FOUND) return "未找到绑定的学生档案，请联系学籍管理员。";
        if (code == StatusCode.SERVER_ERROR) return "成绩数据暂不可用，请联系教务核查后重试。";
        return "查询失败，请重试；如持续失败，请确认客户端与服务器版本一致。";
    }
}

package cn.vcampus.client.view;

import cn.vcampus.course.CourseMeeting;
import cn.vcampus.course.CourseSchedule;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/** 教务端维护教学班多条上课时间的结构化排课编辑器。 */
final class CourseScheduleEditorDialog extends JDialog {
    private final List<CourseMeeting> meetings = new ArrayList<CourseMeeting>();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] { "起始周", "结束周", "星期", "起始节", "结束节", "地点" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable meetingTable = new JTable(model);
    private final JTextField startWeek = new JTextField("1", 3);
    private final JTextField endWeek = new JTextField("20", 3);
    private final JComboBox<DayOfWeek> dayOfWeek = new JComboBox<DayOfWeek>(DayOfWeek.values());
    private final JTextField startPeriod = new JTextField(3);
    private final JTextField endPeriod = new JTextField(3);
    private final JTextField location = new JTextField(10);
    private final JLabel error = new JLabel(" ");
    private CourseSchedule result;

    private CourseScheduleEditorDialog(Component owner, CourseSchedule initial,
            String defaultLocation) {
        super(ownerWindow(owner), "编辑结构化排课", Dialog.ModalityType.APPLICATION_MODAL);
        if (initial != null) meetings.addAll(initial.getMeetings());
        location.setText(defaultLocation == null ? "" : defaultLocation.trim());
        build();
        refreshRows();
    }

    static CourseSchedule edit(Component owner, CourseSchedule initial, String defaultLocation) {
        CourseScheduleEditorDialog dialog = new CourseScheduleEditorDialog(owner, initial,
                defaultLocation);
        dialog.setVisible(true);
        return dialog.result;
    }

    static String format(CourseSchedule schedule) {
        if (schedule == null || schedule.isEmpty()) return "";
        List<CourseMeeting> ordered = new ArrayList<CourseMeeting>(schedule.getMeetings());
        Collections.sort(ordered, new Comparator<CourseMeeting>() {
            @Override public int compare(CourseMeeting first, CourseMeeting second) {
                int compared = first.getStartWeek() - second.getStartWeek();
                if (compared != 0) return compared;
                compared = first.getEndWeek() - second.getEndWeek();
                if (compared != 0) return compared;
                compared = first.getDayOfWeek().getValue() - second.getDayOfWeek().getValue();
                if (compared != 0) return compared;
                compared = first.getStartPeriod() - second.getStartPeriod();
                return compared != 0 ? compared : first.getEndPeriod() - second.getEndPeriod();
            }
        });
        Map<String, List<CourseMeeting>> grouped = new LinkedHashMap<String, List<CourseMeeting>>();
        for (CourseMeeting meeting : ordered) {
            String key = meeting.getStartWeek() + ":" + meeting.getEndWeek();
            List<CourseMeeting> sameWeeks = grouped.get(key);
            if (sameWeeks == null) {
                sameWeeks = new ArrayList<CourseMeeting>();
                grouped.put(key, sameWeeks);
            }
            sameWeeks.add(meeting);
        }
        StringBuilder text = new StringBuilder();
        for (List<CourseMeeting> sameWeeks : grouped.values()) {
            if (text.length() > 0) text.append("；");
            CourseMeeting first = sameWeeks.get(0);
            text.append(first.getStartWeek()).append("-").append(first.getEndWeek()).append("周");
            for (CourseMeeting meeting : sameWeeks) {
                text.append("，").append(dayLabel(meeting.getDayOfWeek()))
                        .append(meeting.getStartPeriod()).append("-")
                        .append(meeting.getEndPeriod()).append("节");
            }
        }
        return text.toString();
    }

    private void build() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));

        JLabel hint = new JLabel("逐条填写教学周、星期、节次与地点；保存后将直接用于选课冲突检测。 ");
        hint.setForeground(VCampusTheme.MUTED);
        content.add(hint, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        center.setOpaque(false);
        JPanel fields = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8),
                UiMetrics.px(4)));
        fields.setOpaque(false);
        style(startWeek, 80); style(endWeek, 80); style(dayOfWeek, 110);
        style(startPeriod, 80); style(endPeriod, 80); style(location, 220);
        fields.add(new JLabel("起始周")); fields.add(startWeek);
        fields.add(new JLabel("结束周")); fields.add(endWeek);
        fields.add(new JLabel("星期")); fields.add(dayOfWeek);
        fields.add(new JLabel("起始节")); fields.add(startPeriod);
        fields.add(new JLabel("结束节")); fields.add(endPeriod);
        fields.add(new JLabel("地点")); fields.add(location);
        JButton add = new JButton("添加时段");
        VCampusTheme.primaryButton(add);
        add.addActionListener(e -> addMeeting());
        fields.add(add);

        VCampusTheme.table(meetingTable);
        meetingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        meetingTable.getColumnModel().getColumn(5).setPreferredWidth(UiMetrics.px(130));
        JPanel tableActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        tableActions.setOpaque(false);
        JButton remove = new JButton("移除所选时段");
        VCampusTheme.secondaryButton(remove);
        remove.addActionListener(e -> removeSelected());
        tableActions.add(remove);
        center.add(fields, BorderLayout.NORTH);
        center.add(VCampusTheme.scrollPane(meetingTable), BorderLayout.CENTER);
        center.add(tableActions, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        bottom.setOpaque(false);
        error.setForeground(VCampusTheme.DANGER);
        bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton("保存排课");
        VCampusTheme.secondaryButton(cancel);
        VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dispose());
        confirm.addActionListener(e -> confirm());
        actions.add(cancel); actions.add(confirm);
        bottom.add(actions, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);

        CourseFormDialogSupport.showScrollableForm(this, content, 840, 560, 700, 440);
        getRootPane().setDefaultButton(confirm);
    }

    private void addMeeting() {
        try {
            CourseMeeting meeting = new CourseMeeting((DayOfWeek) dayOfWeek.getSelectedItem(),
                    positive(startPeriod, "起始节"), positive(endPeriod, "结束节"),
                    positive(startWeek, "起始周"), positive(endWeek, "结束周"), text(location, "地点"));
            List<CourseMeeting> candidate = new ArrayList<CourseMeeting>(meetings);
            candidate.add(meeting);
            new CourseSchedule(candidate);
            meetings.add(meeting);
            refreshRows();
            error.setText(" ");
        } catch (IllegalArgumentException invalid) {
            error.setText("无法添加：" + invalid.getMessage());
        }
    }

    private void removeSelected() {
        int row = meetingTable.getSelectedRow();
        if (row < 0) {
            error.setText("请先选择要移除的一条上课时段。 ");
            return;
        }
        meetings.remove(row);
        refreshRows();
        error.setText(" ");
    }

    private void confirm() {
        if (meetings.isEmpty()) {
            error.setText("请至少添加一条上课时段。 ");
            return;
        }
        try {
            result = new CourseSchedule(meetings);
            dispose();
        } catch (IllegalArgumentException invalid) {
            error.setText("排课无效：" + invalid.getMessage());
        }
    }

    private void refreshRows() {
        model.setRowCount(0);
        for (CourseMeeting meeting : meetings) {
            model.addRow(new Object[] { meeting.getStartWeek(), meeting.getEndWeek(),
                    dayLabel(meeting.getDayOfWeek()), meeting.getStartPeriod(),
                    meeting.getEndPeriod(), meeting.getLocation() });
        }
    }

    private static Window ownerWindow(Component owner) {
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }

    private static void style(javax.swing.JComponent component, int minimumWidth) {
        CourseFormDialogSupport.styleField(component, minimumWidth);
    }

    private static int positive(JTextField field, String name) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + "必须是正整数");
        }
    }

    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    private static String dayLabel(DayOfWeek day) {
        switch (day) {
            case MONDAY: return "星期一";
            case TUESDAY: return "星期二";
            case WEDNESDAY: return "星期三";
            case THURSDAY: return "星期四";
            case FRIDAY: return "星期五";
            case SATURDAY: return "星期六";
            case SUNDAY: return "星期日";
            default: throw new IllegalArgumentException("unsupported day");
        }
    }
}

package cn.vcampus.client.view;

import cn.vcampus.course.Course;
import cn.vcampus.course.CourseOffering;
import cn.vcampus.course.CourseOfferingStatus;
import cn.vcampus.course.CourseSchedule;
import cn.vcampus.student.TeacherProfile;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** 教学班新增和编辑共用的弹窗，课程联动选择，教师输入过滤。 */
final class CourseOfferingEditorDialog extends JDialog {
    private final CourseOffering initial;
    private final String term;
    private final List<Course> allCourses;
    private final List<TeacherProfile> allTeachers;
    private final JTextField offeringId = new JTextField(14);
    private final JComboBox<Course> courseBox = new JComboBox<Course>();
    private final JTextField teacherField = new JTextField(14);
    private final JList<TeacherProfile> teacherCandidates = new JList<TeacherProfile>();
    private final JPopupMenu teacherPopup = new JPopupMenu();
    private final JTextField location = new JTextField(10);
    private final JTextField requiredCapacity = new JTextField(4);
    private final JTextField electiveCapacity = new JTextField(4);
    private final JTextField crossMajorCapacity = new JTextField(4);
    private final JTextField schedule = new JTextField(24);
    private final JLabel error = new JLabel(" ");
    private CourseSchedule meetingSchedule = CourseSchedule.empty();
    private CourseOffering result;
    /** 已明确选择的教师，与当前筛选文本分离。 */
    private TeacherProfile selectedTeacher;
    private boolean updatingTeacherField;

    private CourseOfferingEditorDialog(Component owner, String term, CourseOffering initial,
            List<Course> courses, List<TeacherProfile> teachers) {
        super(ownerWindow(owner), initial == null ? "新增教学班" : "编辑教学班",
                Dialog.ModalityType.APPLICATION_MODAL);
        if (term == null || term.trim().isEmpty() || courses == null || teachers == null) {
            throw new IllegalArgumentException("term, courses and teachers must not be null");
        }
        this.initial = initial;
        this.term = term.trim();
        this.allCourses = new ArrayList<Course>(courses);
        this.allTeachers = new ArrayList<TeacherProfile>(teachers);
        if (initial != null) fill(initial);
        build();
    }

    static CourseOffering create(Component owner, String term, List<Course> courses,
            List<TeacherProfile> teachers) {
        return new CourseOfferingEditorDialog(owner, term, null, courses, teachers).showDialog();
    }

    static CourseOffering edit(Component owner, CourseOffering offering, List<Course> courses,
            List<TeacherProfile> teachers) {
        return offering == null ? null
                : new CourseOfferingEditorDialog(owner, offering.getTerm(), offering, courses,
                        teachers).showDialog();
    }

    private CourseOffering showDialog() {
        setVisible(true);
        return result;
    }

    private void fill(CourseOffering offering) {
        offeringId.setText(offering.getOfferingId());
        location.setText(offering.getLocation());
        requiredCapacity.setText(String.valueOf(offering.getRequiredCapacity()));
        electiveCapacity.setText(String.valueOf(offering.getElectiveCapacity()));
        crossMajorCapacity.setText(String.valueOf(offering.getCrossMajorCapacity()));
        meetingSchedule = offering.getMeetingSchedule();
        schedule.setText(CourseScheduleEditorDialog.format(meetingSchedule));
    }

    private void build() {
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, UiMetrics.px(10), UiMetrics.px(8)));
        form.setOpaque(false);
        style(offeringId); style(courseBox); style(teacherField); style(location);
        style(requiredCapacity, 120); style(electiveCapacity, 120);
        style(crossMajorCapacity, 120); style(schedule);
        schedule.setEditable(false);
        courseBox.setRenderer(courseRenderer());
        for (Course course : allCourses) courseBox.addItem(course);
        if (initial != null) selectCourse(initial.getCourseId());
        configureTeacherPicker();
        if (initial != null) selectTeacher(initial.getTeacherId());
        form.add(new JLabel("教学班编号")); form.add(offeringId);
        form.add(new JLabel("课程编号 / 名称")); form.add(courseBox);
        form.add(new JLabel("学期")); form.add(new JLabel(term));
        form.add(new JLabel("任课教师")); form.add(teacherPicker());
        form.add(new JLabel("主上课地点")); form.add(location);
        form.add(new JLabel("必修 / 选修容量")); form.add(pair(requiredCapacity, electiveCapacity));
        form.add(new JLabel("跨专业容量")); form.add(crossMajorCapacity);
        form.add(new JLabel("结构化排课")); form.add(scheduleWithButton());
        content.add(form, BorderLayout.CENTER);
        content.add(actionBar(), BorderLayout.SOUTH);
        CourseFormDialogSupport.showScrollableForm(this, content, 820, 590, 680, 470);
    }

    private JPanel pair(JTextField first, JTextField second) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(6), 0));
        panel.setOpaque(false);
        panel.add(first);
        panel.add(new JLabel("/"));
        panel.add(second);
        return panel;
    }

    private JPanel scheduleWithButton() {
        JPanel panel = new JPanel(new BorderLayout(UiMetrics.px(6), 0));
        panel.setOpaque(false);
        JButton edit = new JButton("编辑排课");
        VCampusTheme.secondaryButton(edit);
        edit.addActionListener(e -> editSchedule());
        panel.add(schedule, BorderLayout.CENTER);
        panel.add(edit, BorderLayout.EAST);
        return panel;
    }

    private JPanel actionBar() {
        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        bottom.setOpaque(false);
        error.setForeground(VCampusTheme.DANGER);
        bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton(initial == null ? "新增教学班" : "保存教学班");
        VCampusTheme.secondaryButton(cancel);
        VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dispose());
        confirm.addActionListener(e -> confirm());
        actions.add(cancel);
        actions.add(confirm);
        bottom.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(confirm);
        return bottom;
    }

    private void editSchedule() {
        CourseSchedule changed = CourseScheduleEditorDialog.edit(this, meetingSchedule,
                value(location));
        if (changed == null) return;
        meetingSchedule = changed;
        schedule.setText(CourseScheduleEditorDialog.format(changed));
        if (value(location).isEmpty()) location.setText(changed.getMeetings().get(0).getLocation());
    }

    /** 在同一教师选择控件中按姓名、工号、院系过滤候选项。 */
    private void filterTeachers(String keyword, boolean showPopup) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        javax.swing.DefaultListModel<TeacherProfile> model =
                new javax.swing.DefaultListModel<TeacherProfile>();
        for (TeacherProfile teacher : allTeachers) {
            if (teacherText(teacher).toLowerCase().contains(normalized)) model.addElement(teacher);
        }
        teacherCandidates.setModel(model);
        if (showPopup && !model.isEmpty() && teacherField.isShowing()) {
            teacherPopup.show(teacherField, 0, teacherField.getHeight());
            restoreTeacherFieldFocus();
        } else if (model.isEmpty()) {
            teacherPopup.setVisible(false);
        }
    }

    /** 候选弹层出现后仍保持文本输入焦点和光标位置，支持连续输入与删除。 */
    private void restoreTeacherFieldFocus() {
        SwingUtilities.invokeLater(() -> {
            if (!teacherField.isShowing()) return;
            // DocumentListener 触发时插入或删除尚未完全更新光标位置，延后读取可保留最新位置。
            int caretPosition = teacherField.getCaretPosition();
            teacherField.requestFocusInWindow();
            teacherField.setCaretPosition(Math.min(caretPosition, teacherField.getText().length()));
        });
    }

    private void configureTeacherPicker() {
        teacherCandidates.setCellRenderer(teacherRenderer());
        teacherCandidates.setVisibleRowCount(5);
        teacherCandidates.setFocusable(false);
        teacherCandidates.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            TeacherProfile teacher = teacherCandidates.getSelectedValue();
            if (teacher != null) setSelectedTeacher(teacher);
        });
        JScrollPane candidateScroller = VCampusTheme.scrollPane(teacherCandidates);
        candidateScroller.setPreferredSize(UiMetrics.dimension(360, 176));
        teacherPopup.setBorder(javax.swing.BorderFactory.createLineBorder(VCampusTheme.BORDER));
        teacherPopup.setFocusable(false);
        teacherPopup.add(candidateScroller);
        teacherField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> {
            if (updatingTeacherField) return;
            selectedTeacher = null;
            filterTeachers(value(teacherField), true);
        }));
        filterTeachers("", false);
    }

    /** 文本输入和候选弹层组合为一个可输入筛选的教师选择控件。 */
    private JPanel teacherPicker() {
        JPanel picker = new JPanel(new BorderLayout(UiMetrics.px(6), 0));
        picker.setOpaque(false);
        JButton showCandidates = new JButton("⌄");
        showCandidates.setToolTipText("展开在职教师列表");
        VCampusTheme.secondaryButton(showCandidates);
        showCandidates.setPreferredSize(UiMetrics.dimension(42, 38));
        showCandidates.addActionListener(e -> filterTeachers(value(teacherField), true));
        picker.add(teacherField, BorderLayout.CENTER);
        picker.add(showCandidates, BorderLayout.EAST);
        return picker;
    }

    private void selectCourse(String courseId) {
        for (int index = 0; index < courseBox.getItemCount(); index++) {
            Course course = courseBox.getItemAt(index);
            if (course.getCourseId().equals(courseId)) {
                courseBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private void selectTeacher(String teacherId) {
        for (TeacherProfile teacher : allTeachers) {
            if (teacher.getTeacherId().equals(teacherId)) {
                setSelectedTeacher(teacher);
                return;
            }
        }
    }

    /** 选择候选后仅在文本框写入名称，提交仍使用独立保存的教师资料。 */
    private void setSelectedTeacher(TeacherProfile teacher) {
        selectedTeacher = teacher;
        updatingTeacherField = true;
        teacherField.setText(teacherText(teacher));
        updatingTeacherField = false;
        teacherPopup.setVisible(false);
    }

    private void confirm() {
        try {
            Course course = (Course) courseBox.getSelectedItem();
            TeacherProfile teacher = selectedTeacher;
            if (course == null) throw new IllegalArgumentException("请选择一门课程");
            if (teacher == null) throw new IllegalArgumentException("请选择一位在职教师");
            if (meetingSchedule.isEmpty()) throw new IllegalArgumentException("请至少添加一条结构化上课时段");
            result = new CourseOffering(text(offeringId, "教学班编号"), course.getCourseId(), term,
                    teacher.getTeacherId(), CourseScheduleEditorDialog.format(meetingSchedule),
                    text(location, "主上课地点"), nonNegative(requiredCapacity, "必修容量"),
                    nonNegative(electiveCapacity, "选修容量"),
                    nonNegative(crossMajorCapacity, "跨专业容量"),
                    initial == null ? CourseOfferingStatus.DRAFT : initial.getStatus())
                            .withMeetingSchedule(meetingSchedule);
            dispose();
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    private static void style(javax.swing.JComponent component) {
        CourseFormDialogSupport.styleField(component);
    }

    private static void style(javax.swing.JComponent component, int minimumWidth) {
        CourseFormDialogSupport.styleField(component, minimumWidth);
    }

    private static String value(JTextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static String text(JTextField field, String name) {
        String value = value(field);
        if (value.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    private static int nonNegative(JTextField field, String name) {
        try {
            int value = Integer.parseInt(text(field, name));
            if (value < 0) throw new IllegalArgumentException(name + "不能小于 0");
            return value;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + "必须是整数");
        }
    }

    private static String courseText(Course course) {
        return course.getCourseId() + " - " + course.getName();
    }

    private static String teacherText(TeacherProfile teacher) {
        if (teacher == null) return "";
        return teacher.getTeacherName()
                + (teacher.getDepartmentName() == null ? "" : " · " + teacher.getDepartmentName());
    }

    private static DefaultListCellRenderer courseRenderer() {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof Course ? courseText((Course) value) : value,
                        index, selected, focus);
            }
        };
    }

    private static DefaultListCellRenderer teacherRenderer() {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof TeacherProfile ? teacherText((TeacherProfile) value) : value,
                        index, selected, focus);
            }
        };
    }

    private static Window ownerWindow(Component owner) {
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }
}

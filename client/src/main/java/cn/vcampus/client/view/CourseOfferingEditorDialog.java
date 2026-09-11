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
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
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
    private final JComboBox<TeacherProfile> teacherBox = new JComboBox<TeacherProfile>();
    private final JTextField location = new JTextField(10);
    private final JTextField requiredCapacity = new JTextField(4);
    private final JTextField electiveCapacity = new JTextField(4);
    private final JTextField crossMajorCapacity = new JTextField(4);
    private final JTextField schedule = new JTextField(24);
    private final JLabel error = new JLabel(" ");
    private CourseSchedule meetingSchedule = CourseSchedule.empty();
    private CourseOffering result;
    private boolean filtering;

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
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, UiMetrics.px(10), UiMetrics.px(8)));
        form.setOpaque(false);
        style(offeringId); style(courseBox); style(teacherBox); style(location);
        style(requiredCapacity); style(electiveCapacity); style(crossMajorCapacity); style(schedule);
        schedule.setEditable(false);
        courseBox.setRenderer(courseRenderer());
        for (Course course : allCourses) courseBox.addItem(course);
        if (initial != null) selectCourse(initial.getCourseId());
        teacherBox.setRenderer(teacherRenderer());
        teacherBox.setEditable(true);
        ((JTextField) teacherBox.getEditor().getEditorComponent()).getDocument()
                .addDocumentListener(new SimpleDocumentListener(() -> {
                    if (!filtering) filterTeachers(teacherKeyword());
                }));
        filterTeachers("");
        if (initial != null) selectTeacher(initial.getTeacherId());
        form.add(new JLabel("教学班编号")); form.add(offeringId);
        form.add(new JLabel("课程编号 / 名称")); form.add(courseBox);
        form.add(new JLabel("学期")); form.add(new JLabel(term));
        form.add(new JLabel("任课教师")); form.add(teacherBox);
        form.add(new JLabel("主上课地点")); form.add(location);
        form.add(new JLabel("必修 / 选修容量")); form.add(pair(requiredCapacity, electiveCapacity));
        form.add(new JLabel("跨专业容量")); form.add(crossMajorCapacity);
        form.add(new JLabel("结构化排课")); form.add(scheduleWithButton());
        content.add(form, BorderLayout.CENTER);
        content.add(actionBar(), BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setSize(UiMetrics.dimension(650, 450));
        setResizable(false);
        setLocationRelativeTo(getOwner());
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

    /** 在同一个可输入下拉框中按姓名、工号、院系过滤教师。 */
    private void filterTeachers(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        TeacherProfile selected = teacherBox.getSelectedItem() instanceof TeacherProfile
                ? (TeacherProfile) teacherBox.getSelectedItem() : null;
        DefaultComboBoxModel<TeacherProfile> model = new DefaultComboBoxModel<TeacherProfile>();
        for (TeacherProfile teacher : allTeachers) {
            if (teacherText(teacher).toLowerCase().contains(normalized)) model.addElement(teacher);
        }
        filtering = true;
        teacherBox.setModel(model);
        if (selected != null && teacherText(selected).toLowerCase().contains(normalized)) {
            teacherBox.setSelectedItem(selected);
        } else {
            teacherBox.setSelectedItem(null);
            teacherBox.getEditor().setItem(keyword);
        }
        filtering = false;
    }

    private String teacherKeyword() {
        Object value = teacherBox.getEditor().getItem();
        return value == null ? "" : String.valueOf(value);
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
        for (int index = 0; index < teacherBox.getItemCount(); index++) {
            TeacherProfile teacher = teacherBox.getItemAt(index);
            if (teacher.getTeacherId().equals(teacherId)) {
                teacherBox.setSelectedIndex(index);
                return;
            }
        }
    }

    private void confirm() {
        try {
            Course course = (Course) courseBox.getSelectedItem();
            TeacherProfile teacher = teacherBox.getSelectedItem() instanceof TeacherProfile
                    ? (TeacherProfile) teacherBox.getSelectedItem() : null;
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
        VCampusTheme.roundedField(component);
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
        return teacher.getTeacherName() + "（" + teacher.getTeacherId() + "）"
                + (teacher.getDepartmentName() == null ? "" : " - " + teacher.getDepartmentName());
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

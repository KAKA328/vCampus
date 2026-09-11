package cn.vcampus.client.view;

import cn.vcampus.course.Course;
import cn.vcampus.course.SelectionType;
import cn.vcampus.course.TrainingPlan;
import cn.vcampus.course.TrainingPlanCourse;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** 培养方案基本信息和方案课程要求的受控编辑弹窗。 */
final class TrainingPlanEditorDialog extends JDialog {
    private final TrainingPlan initial;
    private final boolean courseRequirementMode;
    private final JTextField planId = new JTextField(14);
    private final JTextField majorName = new JTextField(14);
    private final JSpinner enrollmentYear = new JSpinner(new SpinnerNumberModel(2026, 1900, 9999, 1));
    private final JComboBox<Course> course = new JComboBox<Course>();
    private final JSpinner recommendedTerm = new JSpinner(new SpinnerNumberModel(1, 1, 16, 1));
    private final JComboBox<SelectionType> selectionType = new JComboBox<SelectionType>(
            new SelectionType[] { SelectionType.REQUIRED, SelectionType.ELECTIVE });
    private final JCheckBox crossMajorAllowed = new JCheckBox("允许跨专业选择");
    private final JLabel error = new JLabel(" ");
    private TrainingPlan planResult;
    private TrainingPlanCourse courseResult;

    private TrainingPlanEditorDialog(Component owner, TrainingPlan initial,
            TrainingPlanCourse initialCourse, List<Course> courses, boolean courseRequirementMode) {
        super(ownerWindow(owner), title(initial, initialCourse, courseRequirementMode),
                Dialog.ModalityType.APPLICATION_MODAL);
        this.initial = initial;
        this.courseRequirementMode = courseRequirementMode;
        planId.setEditable(initial == null);
        if (initial != null) {
            planId.setText(initial.getPlanId());
            majorName.setText(initial.getMajorName());
            enrollmentYear.setValue(Integer.valueOf(initial.getEnrollmentYear()));
        }
        if (courseRequirementMode) {
            populateCourses(courses, initialCourse);
            if (initialCourse != null) {
                recommendedTerm.setValue(Integer.valueOf(initialCourse.getRecommendedTerm()));
                selectionType.setSelectedItem(initialCourse.getSelectionType());
                crossMajorAllowed.setSelected(initialCourse.isCrossMajorAllowed());
            }
        }
        build();
    }

    static TrainingPlan create(Component owner) {
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, null, null,
                Collections.<Course>emptyList(), false);
        dialog.setVisible(true);
        return dialog.planResult;
    }

    static TrainingPlan edit(Component owner, TrainingPlan plan) {
        if (plan == null) return null;
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, plan, null,
                Collections.<Course>emptyList(), false);
        dialog.setVisible(true);
        return dialog.planResult;
    }

    static TrainingPlanCourse editCourse(Component owner, TrainingPlanCourse initial,
            List<Course> courses) {
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, null, initial, courses, true);
        dialog.setVisible(true);
        return dialog.courseResult;
    }

    private void populateCourses(List<Course> courses, TrainingPlanCourse initialCourse) {
        List<Course> choices = courses == null ? Collections.<Course>emptyList() : new ArrayList<Course>(courses);
        for (Course item : choices) course.addItem(item);
        course.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                String text = value instanceof Course ? ((Course) value).getCourseId() + " - "
                        + ((Course) value).getName() : "请选择课程";
                return super.getListCellRendererComponent(list, text, index, selected, focused);
            }
        });
        if (initialCourse != null) {
            for (int index = 0; index < course.getItemCount(); index++) {
                if (initialCourse.getCourseId().equals(course.getItemAt(index).getCourseId())) {
                    course.setSelectedIndex(index);
                    break;
                }
            }
        }
    }

    private void build() {
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        if (courseRequirementMode) addCourseFields(form); else addPlanFields(form);
        content.add(form, BorderLayout.CENTER);
        content.add(actionBar(), BorderLayout.SOUTH);
        CourseFormDialogSupport.showScrollableForm(this, content, 700,
                courseRequirementMode ? 500 : 460, 600, courseRequirementMode ? 420 : 380);
    }

    private void addPlanFields(JPanel form) {
        style(planId); style(majorName); style(enrollmentYear);
        addRow(form, "方案编号", planId);
        addRow(form, "专业", majorName);
        addRow(form, "入学年份", enrollmentYear);
        JLabel hint = new JLabel("学籍模块目前未提供专业目录查询，暂使用专业名称录入。 ");
        hint.setForeground(VCampusTheme.MUTED);
        addHint(form, hint);
    }

    private void addCourseFields(JPanel form) {
        style(course); style(recommendedTerm); style(selectionType);
        selectionType.setRenderer(CourseFormDialogSupport.courseCategoryRenderer());
        crossMajorAllowed.setOpaque(false);
        addRow(form, "课程", course);
        addRow(form, "建议学期", recommendedTerm);
        addRow(form, "课程类别", selectionType);
        addRow(form, " ", crossMajorAllowed);
    }

    private JPanel actionBar() {
        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        bottom.setOpaque(false);
        error.setForeground(VCampusTheme.DANGER);
        bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton(confirmText());
        VCampusTheme.secondaryButton(cancel); VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dispose()); confirm.addActionListener(e -> confirm());
        actions.add(cancel); actions.add(confirm); bottom.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(confirm);
        return bottom;
    }

    private void confirm() {
        try {
            if (courseRequirementMode) courseResult = courseRequirement();
            else if (initial == null) planResult = new TrainingPlan(text(planId, "方案编号"),
                    text(majorName, "专业"), ((Integer) enrollmentYear.getValue()).intValue(),
                    Collections.<TrainingPlanCourse>emptyList());
            else planResult = initial.withBasicInfo(text(majorName, "专业"),
                    ((Integer) enrollmentYear.getValue()).intValue());
            dispose();
        } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
    }

    private TrainingPlanCourse courseRequirement() {
        Course selected = (Course) course.getSelectedItem();
        if (selected == null) throw new IllegalArgumentException("请选择课程");
        return new TrainingPlanCourse(selected.getCourseId(), ((Integer) recommendedTerm.getValue()).intValue(),
                (SelectionType) selectionType.getSelectedItem(), crossMajorAllowed.isSelected());
    }

    private String confirmText() { return courseRequirementMode ? "保存课程要求" : initial == null ? "新建培养方案" : "保存方案信息"; }
    private static String title(TrainingPlan initial, TrainingPlanCourse course, boolean courseMode) {
        if (courseMode) return course == null ? "新增课程要求" : "编辑课程要求";
        return initial == null ? "新建培养方案" : "编辑培养方案";
    }
    private static void style(javax.swing.JComponent component) {
        CourseFormDialogSupport.styleField(component);
    }
    private static void addRow(JPanel form, String label, Component field) {
        GridBagConstraints left = constraints(); left.gridx = 0; left.weightx = 0; left.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), left);
        GridBagConstraints right = constraints(); right.gridx = 1; right.weightx = 1; right.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, right);
    }
    private static void addHint(JPanel form, JLabel hint) {
        GridBagConstraints constraints = constraints(); constraints.gridx = 0; constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL; form.add(hint, constraints);
    }
    private static GridBagConstraints constraints() {
        GridBagConstraints value = new GridBagConstraints(); value.gridy = GridBagConstraints.RELATIVE;
        value.anchor = GridBagConstraints.WEST; value.insets = UiMetrics.insets(4, 0, 4, 10); return value;
    }
    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }
    private static Window ownerWindow(Component owner) { return owner == null ? null : SwingUtilities.getWindowAncestor(owner); }
}

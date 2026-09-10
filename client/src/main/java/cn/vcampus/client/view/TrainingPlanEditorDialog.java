package cn.vcampus.client.view;

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
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** 培养方案及其课程要求的新增、编辑弹窗。 */
final class TrainingPlanEditorDialog extends JDialog {
    private final TrainingPlan initial;
    private final boolean courseRequirementMode;
    private final JTextField planId = new JTextField(14);
    private final JTextField majorName = new JTextField(14);
    private final JTextField enrollmentYear = new JTextField(6);
    private final JTextField courseId = new JTextField(12);
    private final JTextField recommendedTerm = new JTextField(4);
    private final JComboBox<SelectionType> selectionType = new JComboBox<SelectionType>(
            new SelectionType[] { SelectionType.REQUIRED, SelectionType.ELECTIVE,
                    SelectionType.CROSS_MAJOR });
    private final JCheckBox crossMajorAllowed = new JCheckBox("允许跨专业选择");
    private final JLabel error = new JLabel(" ");
    private TrainingPlan planResult;
    private TrainingPlanCourse courseResult;

    private TrainingPlanEditorDialog(Component owner, TrainingPlan initial,
            TrainingPlanCourse course, boolean courseRequirementMode) {
        super(ownerWindow(owner), title(initial, course, courseRequirementMode),
                Dialog.ModalityType.APPLICATION_MODAL);
        this.initial = initial;
        this.courseRequirementMode = courseRequirementMode;
        planId.setEditable(initial == null);
        if (initial != null) {
            planId.setText(initial.getPlanId());
            majorName.setText(initial.getMajorName());
            enrollmentYear.setText(String.valueOf(initial.getEnrollmentYear()));
        }
        if (course != null) {
            courseId.setText(course.getCourseId());
            recommendedTerm.setText(String.valueOf(course.getRecommendedTerm()));
            selectionType.setSelectedItem(course.getSelectionType());
            crossMajorAllowed.setSelected(course.isCrossMajorAllowed());
        }
        build();
    }

    static TrainingPlan create(Component owner) {
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, null, null, false);
        dialog.setVisible(true); return dialog.planResult;
    }

    static TrainingPlan edit(Component owner, TrainingPlan plan) {
        if (plan == null) return null;
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, plan, null, false);
        dialog.setVisible(true); return dialog.planResult;
    }

    static TrainingPlanCourse editCourse(Component owner, TrainingPlanCourse course) {
        TrainingPlanEditorDialog dialog = new TrainingPlanEditorDialog(owner, null, course, true);
        dialog.setVisible(true); return dialog.courseResult;
    }

    private void build() {
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(14)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        if (courseRequirementMode) addCourseFields(form);
        else addPlanFields(form);
        content.add(form, BorderLayout.CENTER); content.add(actionBar(), BorderLayout.SOUTH);
        setContentPane(content); pack();
        setMinimumSize(UiMetrics.dimension(560, courseRequirementMode ? 350 : 460));
        setSize(UiMetrics.dimension(560, courseRequirementMode ? 350 : 460));
        setResizable(false); setLocationRelativeTo(getOwner());
    }

    private void addPlanFields(JPanel form) {
        style(planId); style(majorName); style(enrollmentYear);
        addRow(form, "方案编号", planId);
        addRow(form, "专业", majorName);
        addRow(form, "入学年份", enrollmentYear);
        if (initial == null) {
            JLabel hint = new JLabel("新方案的首条课程要求"); hint.setForeground(VCampusTheme.MUTED);
            addSectionHint(form, hint);
            addCourseFields(form);
        }
    }

    private void addCourseFields(JPanel form) {
        style(courseId); style(recommendedTerm); style(selectionType);
        crossMajorAllowed.setOpaque(false);
        addRow(form, "课程编号", courseId);
        addRow(form, "建议学期", recommendedTerm);
        addRow(form, "课程类别", selectionType);
        addRow(form, " ", crossMajorAllowed);
    }

    /** 为可编辑文字输入框预留足够高度，避免高 DPI 下文字被边框压缩。 */
    private static void style(JTextField field) {
        field.setEnabled(true);
        field.setMinimumSize(UiMetrics.dimension(260, 38));
        field.setPreferredSize(UiMetrics.dimension(300, 38));
        VCampusTheme.roundedField(field);
    }

    private static void style(javax.swing.JComponent component) {
        VCampusTheme.roundedField(component);
    }

    private static void addRow(JPanel form, String label, java.awt.Component field) {
        GridBagConstraints left = constraints();
        left.gridx = 0; left.weightx = 0; left.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), left);
        GridBagConstraints right = constraints();
        right.gridx = 1; right.weightx = 1; right.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, right);
    }

    private static void addSectionHint(JPanel form, JLabel hint) {
        GridBagConstraints constraints = constraints();
        constraints.gridx = 0; constraints.gridwidth = 2; constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = UiMetrics.insets(10, 0, 2, 0);
        form.add(hint, constraints);
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints value = new GridBagConstraints();
        value.gridy = GridBagConstraints.RELATIVE;
        value.anchor = GridBagConstraints.WEST;
        value.insets = UiMetrics.insets(4, 0, 4, 10);
        return value;
    }

    private JPanel actionBar() {
        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6))); bottom.setOpaque(false);
        error.setForeground(VCampusTheme.DANGER); bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0)); actions.setOpaque(false);
        JButton cancel = new JButton("取消"); JButton confirm = new JButton(confirmText());
        VCampusTheme.secondaryButton(cancel); VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dispose()); confirm.addActionListener(e -> confirm());
        actions.add(cancel); actions.add(confirm); bottom.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(confirm); return bottom;
    }

    private void confirm() {
        try {
            if (courseRequirementMode) {
                courseResult = courseRequirement();
            } else if (initial == null) {
                planResult = new TrainingPlan(text(planId, "方案编号"), text(majorName, "专业"),
                        positive(enrollmentYear, "入学年份"), Collections.singletonList(courseRequirement()));
            } else {
                planResult = initial.withBasicInfo(text(majorName, "专业"),
                        positive(enrollmentYear, "入学年份"));
            }
            dispose();
        } catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
    }

    private TrainingPlanCourse courseRequirement() {
        return new TrainingPlanCourse(text(courseId, "课程编号"), positive(recommendedTerm, "建议学期"),
                (SelectionType) selectionType.getSelectedItem(), crossMajorAllowed.isSelected());
    }

    private String confirmText() {
        if (courseRequirementMode) return "保存课程要求";
        return initial == null ? "新建培养方案" : "保存方案信息";
    }
    private static String title(TrainingPlan initial, TrainingPlanCourse course,
            boolean courseRequirementMode) {
        if (courseRequirementMode) return course == null ? "新增课程要求" : "编辑课程要求";
        return initial == null ? "新建培养方案" : "编辑培养方案";
    }
    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + "不能为空"); return value;
    }
    private static int positive(JTextField field, String name) {
        try { int value = Integer.parseInt(text(field, name)); if (value <= 0) throw new IllegalArgumentException(name + "必须大于 0"); return value; }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + "必须是整数"); }
    }
    private static Window ownerWindow(Component owner) {
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }
}

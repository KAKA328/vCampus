package cn.vcampus.client.view;

import cn.vcampus.course.Course;
import cn.vcampus.course.CourseStatus;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** 课程目录新增和编辑共用的弹窗。 */
final class CourseEditorDialog extends JDialog {
    private final JTextField courseId = new JTextField(14);
    private final JTextField courseName = new JTextField(16);
    private final JTextField credits = new JTextField(5);
    private final JComboBox<CourseStatus> status = new JComboBox<CourseStatus>(
            CourseStatus.values());
    private final JLabel error = new JLabel(" ");
    private Course result;

    private CourseEditorDialog(Component owner, Course initial) {
        super(ownerWindow(owner), initial == null ? "新增课程" : "编辑课程", Dialog.ModalityType.APPLICATION_MODAL);
        if (initial != null) {
            courseId.setText(initial.getCourseId());
            courseName.setText(initial.getName());
            credits.setText(String.valueOf(initial.getCredits()));
            status.setSelectedItem(initial.getStatus());
        }
        build(initial == null);
    }

    static Course create(Component owner) { return new CourseEditorDialog(owner, null).showDialog(); }
    static Course edit(Component owner, Course course) {
        return course == null ? null : new CourseEditorDialog(owner, course).showDialog();
    }

    private Course showDialog() {
        setVisible(true);
        return result;
    }

    private void build(boolean creating) {
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(14)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, UiMetrics.px(10), UiMetrics.px(10)));
        form.setOpaque(false);
        VCampusTheme.field(courseId); VCampusTheme.field(courseName); VCampusTheme.field(credits);
        VCampusTheme.field(status);
        form.add(new JLabel("课程编号")); form.add(courseId);
        form.add(new JLabel("课程名称")); form.add(courseName);
        form.add(new JLabel("学分")); form.add(credits);
        form.add(new JLabel("课程状态")); form.add(status);
        content.add(form, BorderLayout.CENTER);
        content.add(actionBar(creating), BorderLayout.SOUTH);
        setContentPane(content);
        pack(); setSize(UiMetrics.dimension(410, 300));
        setResizable(false); setLocationRelativeTo(getOwner());
    }

    private JPanel actionBar(boolean creating) {
        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        bottom.setOpaque(false); error.setForeground(VCampusTheme.DANGER); bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("取消"); JButton confirm = new JButton(creating ? "新增课程" : "保存修改");
        VCampusTheme.secondaryButton(cancel); VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dispose()); confirm.addActionListener(e -> confirm());
        actions.add(cancel); actions.add(confirm); bottom.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(confirm);
        return bottom;
    }

    private void confirm() {
        try {
            int value = Integer.parseInt(credits.getText().trim());
            if (value <= 0) throw new IllegalArgumentException("学分必须是正整数");
            result = new Course(text(courseId, "课程编号"), text(courseName, "课程名称"), value)
                    .withStatus((CourseStatus) status.getSelectedItem());
            dispose();
        } catch (NumberFormatException invalid) {
            error.setText("学分必须是正整数。 ");
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }
    private static Window ownerWindow(Component owner) {
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }
}

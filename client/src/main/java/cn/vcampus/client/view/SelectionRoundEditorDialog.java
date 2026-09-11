package cn.vcampus.client.view;

import cn.vcampus.course.SelectionRound;
import cn.vcampus.course.SelectionRoundStatus;
import cn.vcampus.course.SelectionRoundType;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;

/** 以受控学期和时间控件创建、编辑选课轮次。 */
final class SelectionRoundEditorDialog extends JDialog {
    private final SelectionRound initial;
    private final JTextField roundId = new JTextField(16);
    private final JComboBox<String> term = CourseTermOptions.comboBox();
    private final JComboBox<SelectionRoundType> type =
            new JComboBox<SelectionRoundType>(SelectionRoundType.values());
    private final JSpinner startsAt = timeSpinner();
    private final JSpinner endsAt = timeSpinner();
    private final JLabel error = new JLabel(" ");
    private SelectionRound result;

    private SelectionRoundEditorDialog(Component owner, SelectionRound initial) {
        super(ownerWindow(owner), initial == null ? "新建选课轮次" : "编辑轮次时间",
                Dialog.ModalityType.APPLICATION_MODAL);
        this.initial = initial;
        if (initial != null) {
            roundId.setText(initial.getRoundId());
            roundId.setEditable(false);
            term.setSelectedItem(initial.getTerm());
            term.setEnabled(false);
            type.setSelectedItem(initial.getType());
            type.setEnabled(false);
            startsAt.setValue(toDate(initial.getStartsAt()));
            endsAt.setValue(toDate(initial.getEndsAt()));
        }
        build();
    }

    static SelectionRound create(Component owner) {
        SelectionRoundEditorDialog dialog = new SelectionRoundEditorDialog(owner, null);
        dialog.setVisible(true);
        return dialog.result;
    }

    static SelectionRound edit(Component owner, SelectionRound round) {
        if (round == null) {
            return null;
        }
        SelectionRoundEditorDialog dialog = new SelectionRoundEditorDialog(owner, round);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void build() {
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(14)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(18, 20, 18, 20));
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        style(roundId);
        style(term);
        style(type);
        style(startsAt);
        style(endsAt);
        addRow(form, "轮次编号", roundId);
        addRow(form, "学期", term);
        addRow(form, "轮次类型", type);
        addRow(form, "开始时间", startsAt);
        addRow(form, "结束时间", endsAt);
        JLabel hint = new JLabel(initial == null
                ? "新建轮次默认停用；启用后由服务器按当前时间自动判断是否开放。"
                : "已启用的轮次需先停用，才可修改开放时间。");
        hint.setForeground(VCampusTheme.MUTED);
        GridBagConstraints hintConstraints = constraints();
        hintConstraints.gridx = 0;
        hintConstraints.gridwidth = 2;
        hintConstraints.fill = GridBagConstraints.HORIZONTAL;
        form.add(hint, hintConstraints);
        content.add(form, BorderLayout.CENTER);
        content.add(actionBar(), BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setMinimumSize(UiMetrics.dimension(560, 370));
        setSize(UiMetrics.dimension(560, 370));
        setResizable(false);
        setLocationRelativeTo(getOwner());
    }

    private JPanel actionBar() {
        JPanel bottom = new JPanel(new BorderLayout(0, UiMetrics.px(6)));
        bottom.setOpaque(false);
        error.setForeground(VCampusTheme.DANGER);
        bottom.add(error, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton(initial == null ? "新建轮次" : "保存时间");
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

    private void confirm() {
        try {
            LocalDateTime start = toLocalDateTime((Date) startsAt.getValue());
            LocalDateTime end = toLocalDateTime((Date) endsAt.getValue());
            result = initial == null
                    ? new SelectionRound(text(roundId, "轮次编号"), (String) term.getSelectedItem(),
                            (SelectionRoundType) type.getSelectedItem(), start, end,
                            SelectionRoundStatus.CLOSED)
                    : initial.withTimeWindow(start, end);
            dispose();
        } catch (IllegalArgumentException invalid) {
            error.setText(invalid.getMessage());
        }
    }

    private static JSpinner timeSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(new Date(), null, null,
                java.util.Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd HH:mm"));
        return spinner;
    }

    private static void style(javax.swing.JComponent component) {
        component.setMinimumSize(UiMetrics.dimension(300, 38));
        component.setPreferredSize(UiMetrics.dimension(300, 38));
        VCampusTheme.roundedField(component);
    }

    private static void addRow(JPanel form, String label, Component field) {
        GridBagConstraints left = constraints();
        left.gridx = 0;
        left.weightx = 0;
        left.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), left);
        GridBagConstraints right = constraints();
        right.gridx = 1;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, right);
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = GridBagConstraints.RELATIVE;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = UiMetrics.insets(4, 0, 4, 10);
        return constraints;
    }

    private static String text(JTextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private static Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDateTime toLocalDateTime(Date value) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), ZoneId.systemDefault());
    }

    private static Window ownerWindow(Component owner) {
        return owner == null ? null : SwingUtilities.getWindowAncestor(owner);
    }
}

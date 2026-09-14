package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.BorderLayout;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** 仅供选课系统页面复用的状态反馈与高影响操作确认工具。 */
final class CourseUiSupport {
    enum UnsavedInputChoice {
        SAVE_AND_RETURN,
        DISCARD,
        CONTINUE_EDITING
    }

    private CourseUiSupport() {
    }

    /**
     * 将操作结果显示为统一的状态标签，同时提供完整文本作为悬停提示，避免长消息被截断后无法查看。
     */
    static void showStatus(JLabel label, String message, Color color) {
        label.setText(message);
        label.setToolTipText(message);
        VCampusTheme.statusPill(label, color);
    }

    /**
     * 在会明显改变业务状态的操作前说明后果，让调用方只在用户明确确认后继续请求服务器。
     */
    static boolean confirmHighImpact(Component owner, String title, String question,
            String consequence) {
        final boolean[] confirmed = { false };
        JDialog dialog = dialog(owner, title);
        JPanel content = dialogContent();
        content.add(messageBlock(question, consequence), BorderLayout.CENTER);
        content.add(actions(dialog, "确认", "取消", () -> confirmed[0] = true),
                BorderLayout.SOUTH);
        show(dialog, content, UiMetrics.dimension(460, 220));
        return confirmed[0];
    }

    /** 统一收集必须填写的简短说明；返回 null 代表用户取消。 */
    static String promptRequiredText(Component owner, String title, String instruction) {
        final String[] value = { null };
        JDialog dialog = dialog(owner, title);
        JPanel content = dialogContent();
        JPanel form = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        form.setOpaque(false);
        JLabel guide = new JLabel("<html><div style='width:320px;'>" + escape(instruction)
                + "</div></html>");
        guide.setForeground(VCampusTheme.TEXT);
        JTextArea input = new JTextArea(4, 30);
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        VCampusTheme.field(input);
        JLabel error = new JLabel(" ");
        error.setForeground(VCampusTheme.DANGER);
        error.setFont(VCampusTheme.font(Font.PLAIN, 13));
        form.add(guide, BorderLayout.NORTH);
        form.add(new JScrollPane(input), BorderLayout.CENTER);
        form.add(error, BorderLayout.SOUTH);
        content.add(form, BorderLayout.CENTER);

        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actionBar.setOpaque(false);
        JButton cancel = new JButton("取消");
        JButton confirm = new JButton("确认退回");
        VCampusTheme.secondaryButton(cancel);
        VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dialog.dispose());
        confirm.addActionListener(e -> {
            String text = input.getText() == null ? "" : input.getText().trim();
            if (text.isEmpty()) {
                error.setText("请填写退回原因后再确认。 ");
                input.requestFocusInWindow();
                return;
            }
            value[0] = text;
            dialog.dispose();
        });
        actionBar.add(cancel);
        actionBar.add(confirm);
        content.add(actionBar, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(confirm);
        show(dialog, content, UiMetrics.dimension(460, 270));
        return value[0];
    }

    /** 未保存输入离开页面前明确提供保存、放弃和继续编辑三个选择。 */
    static UnsavedInputChoice confirmUnsavedInput(Component owner, String title,
            String instruction) {
        final UnsavedInputChoice[] choice = { UnsavedInputChoice.CONTINUE_EDITING };
        JDialog dialog = dialog(owner, title);
        JPanel content = dialogContent();
        content.add(messageBlock("当前成绩输入尚未保存。", instruction), BorderLayout.CENTER);
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actionBar.setOpaque(false);
        JButton keepEditing = new JButton("继续编辑");
        JButton discard = new JButton("放弃修改");
        JButton saveAndReturn = new JButton("保存并返回");
        VCampusTheme.secondaryButton(keepEditing);
        VCampusTheme.secondaryButton(discard);
        VCampusTheme.primaryButton(saveAndReturn);
        keepEditing.addActionListener(e -> dialog.dispose());
        discard.addActionListener(e -> {
            choice[0] = UnsavedInputChoice.DISCARD;
            dialog.dispose();
        });
        saveAndReturn.addActionListener(e -> {
            choice[0] = UnsavedInputChoice.SAVE_AND_RETURN;
            dialog.dispose();
        });
        actionBar.add(keepEditing);
        actionBar.add(discard);
        actionBar.add(saveAndReturn);
        content.add(actionBar, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(saveAndReturn);
        show(dialog, content, UiMetrics.dimension(560, 230));
        return choice[0];
    }

    /** 页面实际显示后仅自动加载一次，避免构造阶段阻塞 EDT，也避免重复发起请求。 */
    static void loadOnFirstShow(final JComponent page, final Runnable loader) {
        page.addHierarchyListener(new HierarchyListener() {
            private boolean scheduled;

            @Override
            public void hierarchyChanged(HierarchyEvent event) {
                if (scheduled || (event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0
                        || !page.isShowing()) {
                    return;
                }
                scheduled = true;
                SwingUtilities.invokeLater(() -> {
                    if (page.isDisplayable()) {
                        loader.run();
                    }
                });
            }
        });
    }

    private static JDialog dialog(Component owner, String title) {
        Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        return dialog;
    }

    private static JPanel dialogContent() {
        JPanel content = new JPanel(new BorderLayout(0, UiMetrics.px(18)));
        content.setBackground(VCampusTheme.PANEL);
        content.setBorder(VCampusTheme.padding(20, 22, 18, 22));
        return content;
    }

    private static JPanel messageBlock(String question, String consequence) {
        JPanel block = new JPanel(new BorderLayout(UiMetrics.px(14), 0));
        block.setOpaque(false);
        JLabel icon = new JLabel("!", SwingConstants.CENTER);
        icon.setOpaque(true);
        icon.setBackground(new Color(254, 242, 242));
        icon.setForeground(VCampusTheme.DANGER);
        icon.setFont(VCampusTheme.font(Font.BOLD, 22));
        icon.setBorder(VCampusTheme.roundedBorder(new Color(254, 202, 202), 22));
        Dimension iconSize = UiMetrics.dimension(44, 44);
        icon.setPreferredSize(iconSize);
        icon.setMinimumSize(iconSize);
        icon.setMaximumSize(iconSize);
        JLabel message = new JLabel("<html><div style='width:320px;'>" + escape(question)
                + "<br/><br/><span style='color:#991b1b;'>影响：" + escape(consequence)
                + "</span></div></html>");
        message.setForeground(VCampusTheme.TEXT);
        block.add(icon, BorderLayout.WEST);
        block.add(message, BorderLayout.CENTER);
        return block;
    }

    private static JPanel actions(JDialog dialog, String confirmText, String cancelText,
            Runnable onConfirm) {
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(8), 0));
        actionBar.setOpaque(false);
        JButton cancel = new JButton(cancelText);
        JButton confirm = new JButton(confirmText);
        VCampusTheme.secondaryButton(cancel);
        VCampusTheme.primaryButton(confirm);
        cancel.addActionListener(e -> dialog.dispose());
        confirm.addActionListener(e -> {
            onConfirm.run();
            dialog.dispose();
        });
        actionBar.add(cancel);
        actionBar.add(confirm);
        dialog.getRootPane().setDefaultButton(confirm);
        return actionBar;
    }

    private static void show(JDialog dialog, JPanel content, Dimension minimumSize) {
        dialog.setContentPane(content);
        dialog.pack();
        Dimension preferred = dialog.getSize();
        dialog.setSize(Math.max(preferred.width, minimumSize.width),
                Math.max(preferred.height, minimumSize.height));
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

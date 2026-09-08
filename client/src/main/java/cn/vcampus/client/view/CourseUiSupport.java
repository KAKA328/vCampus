package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

/** 仅供选课系统页面复用的状态反馈与高影响操作确认工具。 */
final class CourseUiSupport {
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
        String message = "<html><div style='width:320px;'>" + escape(question)
                + "<br/><br/><span style='color:#991b1b;'>影响：" + escape(consequence)
                + "</span></div></html>";
        return JOptionPane.showConfirmDialog(owner, message, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}

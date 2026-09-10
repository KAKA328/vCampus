package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleCardPanelTest {
    @Test
    void summaryTextWrapsWithCardWidth() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "选课系统", "查询课程、提交选课、退课并查看已选课程。", "待接入");

        ModuleCardPanel panel = new ModuleCardPanel(descriptor, event -> { });

        JTextArea summary = (JTextArea) ((BorderLayout) panel.getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        assertTrue(summary.getLineWrap());
        assertTrue(summary.getWrapStyleWord());
        assertFalse(summary.isEditable());
    }

    @Test
    void cardKeepsComfortableHeightForWrappedContent() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "学生学籍管理",
                "查看和维护学生基础信息、班级、专业、联系方式，并为后续学业审查预留入口。",
                "待接入：等待学籍模块页面");

        ModuleCardPanel panel = new ModuleCardPanel(descriptor, event -> { });

        Dimension preferred = panel.getPreferredSize();
        assertTrue(preferred.height >= 180, "dashboard cards should not clip multi-line content");
        assertTrue(preferred.width >= 300, "dashboard cards should keep a readable minimum width");
    }

    @Test
    void statusAreaWrapsAndEnterButtonRemainsClear() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "商店",
                "浏览商品、提交购买并查看个人购买记录。",
                "可用：商店模块已接入");

        ModuleCardPanel panel = new ModuleCardPanel(descriptor, event -> { });
        JPanel bottom = (JPanel) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);

        assertTrue(((BorderLayout) bottom.getLayout()).getLayoutComponent(BorderLayout.CENTER) instanceof JLabel);
        JButton enter = (JButton) ((BorderLayout) bottom.getLayout()).getLayoutComponent(BorderLayout.EAST);
        assertTrue(enter.getPreferredSize().width >= UiMetrics.px(96),
                "enter button should be easy to read and click");
        Insets insets = enter.getInsets();
        int requiredWidth = enter.getFontMetrics(enter.getFont()).stringWidth(enter.getText())
                + insets.left + insets.right;
        assertTrue(enter.getPreferredSize().width >= requiredWidth,
                "enter button must show its complete label");
        JButton naturallySized = new JButton(enter.getText());
        VCampusTheme.primaryButton(naturallySized);
        assertTrue(enter.getPreferredSize().width >= naturallySized.getPreferredSize().width,
                "fixed dashboard button width must not be narrower than its natural themed size");
    }

    @Test
    void moduleStatusUsesCompactBadgeInsteadOfParagraph() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                "用户管理", "维护账号、权限、会话和审计。",
                "可用：用户管理核心流程已接入");

        ModuleCardPanel panel = new ModuleCardPanel(descriptor, event -> { });
        JPanel bottom = (JPanel) ((BorderLayout) panel.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
        JLabel status = (JLabel) ((BorderLayout) bottom.getLayout()).getLayoutComponent(BorderLayout.CENTER);

        assertTrue(status.isOpaque());
        assertTrue(status.getText().contains("可用"));
        assertTrue(status.getPreferredSize().height <= UiMetrics.px(34));
    }

    @Test
    void primaryButtonUsesHighContrastText() {
        JButton button = new JButton("登录系统");

        VCampusTheme.primaryButton(button);

        assertTrue(button.isOpaque());
        assertFalse(button.isFocusPainted());
        assertFalse(button.isFocusable());
        assertTrue(button.getFont().getStyle() == Font.BOLD || button.getFont().getSize() >= 15);
        assertTrue(button.getForeground().equals(java.awt.Color.WHITE));
    }

    @Test
    void disabledButtonTextRemainsReadable() {
        VCampusTheme.install();

        assertTrue(UIManager.getColor("Button.disabledText").equals(VCampusTheme.MUTED));
    }
}

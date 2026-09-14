package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** 图书馆共用的可换行标题、表单行、分类选择器和概览卡片。 */
final class LibraryWidgets {
    /** 构建带标题、数值及说明的概览卡片。 */
    static JPanel metricCard(String title, JLabel value, String hint, Color accent) {
        JPanel card = new JPanel(new BorderLayout(UiMetrics.px(12), 0));
        VCampusTheme.panel(card);
        card.setMinimumSize(UiMetrics.dimension(160, 112));

        JPanel rail = new JPanel();
        rail.setBackground(accent);
        rail.setPreferredSize(UiMetrics.dimension(4, 0));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(VCampusTheme.font(Font.BOLD, 13));
        titleLabel.setForeground(VCampusTheme.MUTED);
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(VCampusTheme.font(Font.PLAIN, 12));
        hintLabel.setForeground(VCampusTheme.MUTED);
        copy.add(titleLabel);
        copy.add(Box.createVerticalStrut(UiMetrics.px(4)));
        copy.add(value);
        copy.add(Box.createVerticalStrut(UiMetrics.px(3)));
        copy.add(hintLabel);

        card.add(rail, BorderLayout.WEST);
        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    /** 创建概览数值标签。 */
    static JLabel metricValue() {
        JLabel label = new JLabel("—");
        label.setFont(VCampusTheme.font(Font.BOLD, 22));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    /** 构建可换行的标题与说明。 */
    static JPanel sectionHeading(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        panel.setOpaque(false);
        JLabel titleLabel = new LibraryWrappingLabel(title);
        titleLabel.setFont(VCampusTheme.font(Font.BOLD, 18));
        titleLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitleLabel = new LibraryWrappingLabel(subtitle);
        subtitleLabel.setFont(VCampusTheme.font(Font.PLAIN, 13));
        subtitleLabel.setForeground(VCampusTheme.MUTED);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(subtitleLabel, BorderLayout.SOUTH);
        return panel;
    }

    /** 创建与目标输入控件关联的标签。 */
    static JLabel fieldLabel(String text, Component target) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(Font.BOLD, 13));
        label.setForeground(VCampusTheme.TEXT);
        label.setLabelFor(target);
        return label;
    }

    /** 把检索标签与输入控件组合成可换行布局单元。 */
    static JPanel searchField(String label, JComponent field) {
        JPanel group = new JPanel(new BorderLayout(UiMetrics.px(8), 0));
        group.setOpaque(false);
        group.add(LibraryWidgets.fieldLabel(label, field), BorderLayout.WEST);
        group.add(field, BorderLayout.CENTER);
        return group;
    }

    /** 为表单添加带样式和无障碍名称的输入行。 */
    static void addField(JPanel form, String label, JComponent field) {
        VCampusTheme.field(field);
        field.setFont(VCampusTheme.font(Font.PLAIN, 14));
        field.getAccessibleContext().setAccessibleName(label.replace("*", ""));
        LibraryWidgets.addFormRow(form, LibraryWidgets.fieldLabel(label, field), field);
    }

    /** 添加可换行的只读详情行，空值显示占位符。 */
    static void addDetailRow(JPanel panel, String label, String value) {
        JLabel key = new JLabel(label);
        key.setFont(VCampusTheme.font(Font.BOLD, 13));
        key.setForeground(VCampusTheme.MUTED);
        JLabel content = new LibraryWrappingLabel(value == null || value.trim().isEmpty() ? "—" : value);
        content.setFont(VCampusTheme.font(Font.PLAIN, 13));
        content.setForeground(VCampusTheme.TEXT);
        LibraryWidgets.addFormRow(panel, key, content);
    }

    /** 按实际内容高度布局标签与内容，避免等高压缩。 */
    static void addFormRow(JPanel panel, JLabel key, JComponent content) {
        int row = panel.getComponentCount() / 2;
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.NORTHWEST;
        label.insets = UiMetrics.insets(8, 0, 8, 18);
        panel.add(key, label);
        GridBagConstraints value = new GridBagConstraints();
        value.gridx = 1;
        value.gridy = row;
        value.weightx = 1.0d;
        value.fill = GridBagConstraints.HORIZONTAL;
        value.anchor = GridBagConstraints.NORTHWEST;
        value.insets = UiMetrics.insets(8, 0, 8, 0);
        panel.add(content, value);
    }

    /** 创建不可自由输入的分类下拉框，可选包含全部分类。 */
    static JComboBox<String> categoryChoices(boolean includeAll) {
        JComboBox<String> choices = new JComboBox<String>();
        if (includeAll) choices.addItem("全部分类");
        for (String category : new String[] {"文学", "科幻", "计算机", "历史", "教材", "哲学", "艺术", "经济", "自然科学"}) {
            choices.addItem(category);
        }
        choices.setEditable(false);
        choices.setPrototypeDisplayValue("自然科学分类");
        return choices;
    }

    /** 将有效的新分类补充到下拉列表，避免重复条目。 */
    static void addCategoryChoice(JComboBox<String> choices, String category) {
        if (category == null || category.trim().isEmpty()) return;
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (choices.getItemAt(index).equals(category.trim())) return;
        }
        choices.addItem(category.trim());
    }

    /** 构建可滚动的操作确认内容。 */
    static JPanel confirmationPanel(String title, String text) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        panel.add(LibraryWidgets.sectionHeading(title, text), BorderLayout.NORTH);
        return panel;
    }
}

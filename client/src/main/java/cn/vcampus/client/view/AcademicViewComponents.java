package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.*;

/** Small presentation helpers scoped to the academic pages. */
final class AcademicViewComponents {
    private AcademicViewComponents() { }

    static JPanel metric(String title, JLabel value, String description) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        VCampusTheme.panel(card);
        JLabel label = new JLabel(title);
        label.setForeground(VCampusTheme.MUTED);
        value.setFont(VCampusTheme.font(Font.BOLD, 30));
        value.setForeground(VCampusTheme.PRIMARY);
        value.getAccessibleContext().setAccessibleName(title);
        JLabel hint = new JLabel(description);
        hint.setFont(VCampusTheme.font(Font.PLAIN, 12));
        hint.setForeground(VCampusTheme.MUTED);
        card.add(label, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(hint, BorderLayout.SOUTH);
        return card;
    }

    static JPanel section(String title, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        JLabel heading = new JLabel(title);
        heading.setFont(VCampusTheme.font(Font.BOLD, 15));
        heading.setForeground(VCampusTheme.PRIMARY_DARK);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** 与商店等页面共用同一套数值感知排序比较器，避免数值列退化成字典序。 */
    static void sortable(JTable table) {
        SortableTables.apply(table);
    }
}

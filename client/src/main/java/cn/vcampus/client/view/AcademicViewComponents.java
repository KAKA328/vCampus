package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Font;
import java.text.Collator;
import java.util.Locale;
import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

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

    static void sortable(JTable table) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(table.getModel());
        Collator collator = Collator.getInstance(Locale.CHINA);
        for (int column = 0; column < table.getColumnCount(); column++) {
            sorter.setComparator(column, (a, b) -> {
                if (a instanceof Number && b instanceof Number) {
                    return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
                }
                return collator.compare(String.valueOf(a), String.valueOf(b));
            });
        }
        table.setRowSorter(sorter);
    }
}

package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** Reusable dashboard card for one business module. */
final class ModuleCardPanel extends JPanel {
    private static final Dimension READABLE_CARD_SIZE = new Dimension(300, 180);

    ModuleCardPanel(ModuleDescriptor module, ActionListener enterListener) {
        super(new BorderLayout(0, 10));
        VCampusTheme.panel(this);
        setMinimumSize(READABLE_CARD_SIZE);
        setPreferredSize(READABLE_CARD_SIZE);

        JLabel title = new JLabel(module.getTitle());
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JTextArea summary = new JTextArea(module.getSummary());
        summary.setEditable(false);
        summary.setFocusable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setOpaque(false);
        summary.setFont(VCampusTheme.font(Font.PLAIN, 14));
        summary.setForeground(VCampusTheme.TEXT);

        JTextArea status = new JTextArea(module.getStatus());
        status.setEditable(false);
        status.setFocusable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setFont(VCampusTheme.font(Font.PLAIN, 13));
        status.setForeground(module.getStatus().contains("可用") ? VCampusTheme.SUCCESS : VCampusTheme.MUTED);

        JButton enter = new JButton("进入模块");
        VCampusTheme.secondaryButton(enter);
        enter.setPreferredSize(new Dimension(104, enter.getPreferredSize().height));
        enter.setMinimumSize(enter.getPreferredSize());
        enter.addActionListener(enterListener);

        add(title, BorderLayout.NORTH);
        add(summary, BorderLayout.CENTER);
        add(bottom(status, enter), BorderLayout.SOUTH);
    }

    private JPanel bottom(JTextArea status, JButton enter) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(status, BorderLayout.CENTER);
        panel.add(enter, BorderLayout.EAST);
        return panel;
    }
}

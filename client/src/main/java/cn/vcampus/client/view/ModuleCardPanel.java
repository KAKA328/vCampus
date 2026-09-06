package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Color;
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
        super(new BorderLayout(0, 12));
        VCampusTheme.panel(this);
        setMinimumSize(READABLE_CARD_SIZE);
        setPreferredSize(READABLE_CARD_SIZE);

        JLabel title = new JLabel(module.getTitle());
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        title.setBorder(javax.swing.BorderFactory.createMatteBorder(3, 0, 0, 0,
                module.getStatus().contains("可用") ? VCampusTheme.PRIMARY : VCampusTheme.BORDER));

        JTextArea summary = new JTextArea(module.getSummary());
        summary.setEditable(false);
        summary.setFocusable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setOpaque(false);
        summary.setFont(VCampusTheme.font(Font.PLAIN, 14));
        summary.setForeground(VCampusTheme.TEXT);

        JLabel status = new JLabel(statusLabel(module.getStatus()));
        status.setToolTipText(module.getStatus());
        Color statusColor = module.getStatus().contains("可用") ? VCampusTheme.SUCCESS : VCampusTheme.MUTED;
        VCampusTheme.statusPill(status, statusColor);

        JButton enter = new JButton("进入模块");
        if (module.getStatus().contains("可用")) {
            VCampusTheme.primaryButton(enter);
        } else {
            VCampusTheme.secondaryButton(enter);
        }
        enter.setPreferredSize(new Dimension(118, enter.getPreferredSize().height));
        enter.setMinimumSize(enter.getPreferredSize());
        enter.addActionListener(enterListener);

        add(title, BorderLayout.NORTH);
        add(summary, BorderLayout.CENTER);
        add(bottom(status, enter), BorderLayout.SOUTH);
    }

    private static String statusLabel(String status) {
        if (status == null || status.trim().isEmpty()) {
            return "待确认";
        }
        int separator = status.indexOf('：');
        if (separator > 0) {
            return status.substring(0, separator);
        }
        return status.length() > 8 ? status.substring(0, 8) : status;
    }

    private JPanel bottom(JLabel status, JButton enter) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(status, BorderLayout.CENTER);
        panel.add(enter, BorderLayout.EAST);
        return panel;
    }
}

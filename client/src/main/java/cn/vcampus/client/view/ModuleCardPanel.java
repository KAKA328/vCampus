package cn.vcampus.client.view;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Reusable dashboard card for one business module. */
final class ModuleCardPanel extends JPanel {
    ModuleCardPanel(ModuleDescriptor module, ActionListener enterListener) {
        super(new BorderLayout(0, 10));
        VCampusTheme.panel(this);

        JLabel title = new JLabel(module.getTitle());
        title.setFont(VCampusTheme.font(Font.BOLD, 17));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JLabel summary = new JLabel("<html><div style='width:260px;line-height:1.5;'>"
                + module.getSummary()
                + "</div></html>");
        summary.setForeground(VCampusTheme.TEXT);

        JLabel status = new JLabel(module.getStatus());
        status.setForeground(module.getStatus().contains("可用") ? VCampusTheme.SUCCESS : VCampusTheme.MUTED);

        JButton enter = new JButton("进入模块");
        VCampusTheme.secondaryButton(enter);
        enter.addActionListener(enterListener);

        add(title, BorderLayout.NORTH);
        add(summary, BorderLayout.CENTER);
        add(bottom(status, enter), BorderLayout.SOUTH);
    }

    private JPanel bottom(JLabel status, JButton enter) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(status, BorderLayout.CENTER);
        panel.add(enter, BorderLayout.EAST);
        return panel;
    }
}

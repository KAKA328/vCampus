package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Font;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Shared Swing styling for the vCampus desktop client. */
final class VCampusTheme {
    static final Color PRIMARY = new Color(33, 99, 154);
    static final Color PRIMARY_DARK = new Color(24, 67, 112);
    static final Color ACCENT = new Color(51, 132, 203);
    static final Color BACKGROUND = new Color(245, 247, 250);
    static final Color SIDEBAR = new Color(232, 239, 247);
    static final Color PANEL = Color.WHITE;
    static final Color TEXT = new Color(38, 49, 57);
    static final Color MUTED = new Color(105, 119, 132);
    static final Color BORDER = new Color(217, 226, 235);
    static final Color SUCCESS = new Color(30, 132, 73);
    static final Color DANGER = new Color(184, 53, 53);

    private VCampusTheme() { }

    static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default look and feel is acceptable if the platform one is unavailable.
        }
        UIManager.put("Button.font", font(Font.PLAIN, 14));
        UIManager.put("Label.font", font(Font.PLAIN, 14));
        UIManager.put("TextField.font", font(Font.PLAIN, 14));
        UIManager.put("PasswordField.font", font(Font.PLAIN, 14));
        UIManager.put("ComboBox.font", font(Font.PLAIN, 14));
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("Button.disabledForeground", MUTED);
    }

    static Font font(int style, int size) {
        return new Font("Microsoft YaHei UI", style, size);
    }

    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static void panel(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(18, 22, 18, 22)));
    }

    static void field(JComponent component) {
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(8, 10, 8, 10)));
    }

    static void primaryButton(AbstractButton button) {
        button.setFont(font(Font.BOLD, 15));
        keepButtonReadable(button, PRIMARY, Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(PRIMARY_DARK, 2),
                padding(10, 24, 10, 24)));
    }

    static void secondaryButton(AbstractButton button) {
        button.setFont(font(Font.PLAIN, 14));
        keepButtonReadable(button, new Color(238, 244, 250), PRIMARY_DARK);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(9, 18, 9, 18)));
    }

    static void navButton(AbstractButton button, boolean active) {
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setBorder(padding(10, 14, 10, 14));
        prepareButton(button);
        button.setBackground(active ? PRIMARY : Color.WHITE);
        button.setForeground(active ? Color.WHITE : PRIMARY_DARK);
    }

    private static void keepButtonReadable(final AbstractButton button, final Color background,
            final Color foreground) {
        button.setBackground(background);
        button.setForeground(foreground);
        prepareButton(button);
        button.getModel().addChangeListener(event -> {
            if (button.isEnabled()) {
                button.setBackground(background);
                button.setForeground(foreground);
            } else {
                button.setBackground(new Color(229, 235, 241));
                button.setForeground(PRIMARY_DARK);
            }
        });
    }

    private static void prepareButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setRequestFocusEnabled(false);
        button.setRolloverEnabled(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
    }
}

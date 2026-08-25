package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Shared Swing styling for the vCampus desktop client. */
final class VCampusTheme {
    static final Color PRIMARY = new Color(33, 99, 154);
    static final Color BACKGROUND = new Color(245, 247, 250);
    static final Color PANEL = Color.WHITE;
    static final Color TEXT = new Color(38, 49, 57);

    private VCampusTheme() { }

    static void install() {
        UIManager.put("Button.font", font(Font.PLAIN, 14));
        UIManager.put("Label.font", font(Font.PLAIN, 14));
        UIManager.put("TextField.font", font(Font.PLAIN, 14));
        UIManager.put("PasswordField.font", font(Font.PLAIN, 14));
        UIManager.put("ComboBox.font", font(Font.PLAIN, 14));
    }

    static Font font(int style, int size) {
        return new Font("Microsoft YaHei UI", style, size);
    }

    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static void panel(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(padding(18, 22, 18, 22));
    }
}

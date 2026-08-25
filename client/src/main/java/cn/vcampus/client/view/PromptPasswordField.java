package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JPasswordField;

/** Password field that paints a light prompt while empty. */
final class PromptPasswordField extends JPasswordField {
    private final String prompt;

    PromptPasswordField(int columns, String prompt) {
        super(columns);
        this.prompt = prompt;
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (getPassword().length > 0) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        Insets insets = getInsets();
        g.setColor(new Color(150, 160, 170));
        g.setFont(getFont());
        int y = (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent();
        g.drawString(prompt, insets.left + 2, y);
        g.dispose();
    }
}

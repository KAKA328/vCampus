package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VCampusThemeTest {
    @Test
    void primaryButtonUsesHighContrastColors() {
        JButton button = new JButton("登录系统");

        VCampusTheme.primaryButton(button);

        assertTrue(contrast(button.getBackground(), button.getForeground()) >= 7.0,
                "primary button text must remain readable");
        assertTrue(button.isOpaque());
        assertTrue(button.getUI() instanceof VCampusTheme.ReadableButtonUI,
                "button must use the cross-platform readable renderer");
        assertTrue(!button.isContentAreaFilled(),
                "native look and feel must not overwrite the custom background");
    }

    @Test
    void secondaryButtonUsesDarkTextOnLightBackground() {
        JButton button = new JButton("取消");

        VCampusTheme.secondaryButton(button);

        assertTrue(contrast(button.getBackground(), button.getForeground()) >= 7.0,
                "secondary button text must remain readable");
        assertEquals(VCampusTheme.PRIMARY_DARK, button.getForeground());
    }

    @Test
    void disabledStyledButtonKeepsStablePaintColors() {
        JButton button = new JButton("刷新课程");
        VCampusTheme.secondaryButton(button);
        Color enabledBackground = button.getBackground();
        Color enabledForeground = button.getForeground();

        button.setSize(120, 42);
        button.setEnabled(false);

        assertEquals(enabledBackground, button.getBackground());
        assertEquals(enabledForeground, button.getForeground());
        assertEquals(enabledBackground, paintedBackgroundColor(button));
    }

    private static double contrast(Color first, Color second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        double lighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed())
                + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static Color paintedBackgroundColor(JButton button) {
        BufferedImage image = new BufferedImage(
                button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
        return new Color(image.getRGB(10, 10));
    }
}

package cn.vcampus.client.view;

import java.awt.Color;
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
    }

    @Test
    void secondaryButtonUsesDarkTextOnLightBackground() {
        JButton button = new JButton("取消");

        VCampusTheme.secondaryButton(button);

        assertTrue(contrast(button.getBackground(), button.getForeground()) >= 7.0,
                "secondary button text must remain readable");
        assertEquals(VCampusTheme.PRIMARY_DARK, button.getForeground());
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
}

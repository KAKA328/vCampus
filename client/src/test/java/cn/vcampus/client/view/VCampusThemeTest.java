package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.DefaultTableModel;
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
    void commercialPaletteUsesLayeredBrightBlueSurfaces() {
        assertEquals(Color.WHITE, VCampusTheme.PANEL);
        assertTrue(VCampusTheme.BACKGROUND.getRed() >= 245);
        assertTrue(VCampusTheme.BACKGROUND.getGreen() >= 247);
        assertTrue(VCampusTheme.BACKGROUND.getBlue() >= 250);
        assertTrue(!VCampusTheme.SIDEBAR.equals(VCampusTheme.PANEL));
        assertTrue(!VCampusTheme.HEADER_BACKGROUND.equals(VCampusTheme.PANEL));
        assertTrue(!VCampusTheme.HEADER_BACKGROUND.equals(VCampusTheme.SIDEBAR));
        assertTrue(VCampusTheme.PRIMARY.getBlue() > VCampusTheme.PRIMARY.getRed());
        assertTrue(VCampusTheme.PRIMARY.getBlue() > VCampusTheme.PRIMARY.getGreen());
        assertTrue(VCampusTheme.ACCENT.getBlue() > VCampusTheme.ACCENT.getRed());
    }

    @Test
    void navButtonKeepsCompactCommercialSidebarItemSize() {
        JButton button = new JButton("用户管理");

        VCampusTheme.navButton(button, false);

        assertEquals(SwingConstants.LEFT, button.getHorizontalAlignment());
        assertTrue(button.getMaximumSize().height <= 48);
        assertEquals(VCampusTheme.SIDEBAR, button.getBackground());
        assertEquals(VCampusTheme.TEXT, button.getForeground());

        VCampusTheme.navButton(button, true);

        assertEquals(VCampusTheme.NAV_ACTIVE_BACKGROUND, button.getBackground());
        assertEquals(VCampusTheme.PRIMARY, button.getForeground());
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

    @Test
    void tableStyleCreatesReadableDenseDataSurface() {
        JTable table = new JTable(new DefaultTableModel(new Object[] {"编号", "姓名"}, 0));

        VCampusTheme.table(table);

        assertTrue(table.getRowHeight() >= 32);
        assertTrue(table.getFillsViewportHeight());
        assertTrue(!table.getShowVerticalLines());
        assertEquals(VCampusTheme.BORDER, table.getGridColor());
        assertEquals(VCampusTheme.SURFACE_ALT, table.getTableHeader().getBackground());
        assertEquals(VCampusTheme.PRIMARY_DARK, table.getTableHeader().getForeground());
        assertTrue(table.getTableHeader().getFont().isBold());
        assertTrue(table.getDefaultRenderer(Object.class) instanceof VCampusTheme.ReadableTableCellRenderer);
    }

    @Test
    void tableRendererUsesZebraRowsAndSelectionColor() {
        JTable table = new JTable(new DefaultTableModel(new Object[][] {
                {"001", "张三"}, {"002", "李四"}
        }, new Object[] {"编号", "姓名"}));

        VCampusTheme.table(table);
        TableCellRenderer renderer = table.getDefaultRenderer(Object.class);

        assertEquals(VCampusTheme.PANEL, renderer.getTableCellRendererComponent(
                table, "001", false, false, 0, 0).getBackground());
        assertEquals(VCampusTheme.TABLE_STRIPE, renderer.getTableCellRendererComponent(
                table, "002", false, false, 1, 0).getBackground());
        assertEquals(table.getSelectionBackground(), renderer.getTableCellRendererComponent(
                table, "002", true, false, 1, 0).getBackground());
    }

    @Test
    void scrollPaneUsesSlimCommercialScrollbars() {
        JScrollPane scroller = VCampusTheme.scrollPane(new JTable());
        JScrollBar vertical = scroller.getVerticalScrollBar();

        assertTrue(vertical.getUI() instanceof VCampusTheme.SlimScrollBarUI);
        assertTrue(vertical.getPreferredSize().width <= 10);
    }

    @Test
    void activeNavButtonHasBrandRailIndicator() {
        JButton button = new JButton("学籍查询");

        VCampusTheme.navButton(button, true);

        assertTrue(button.getBorder() instanceof VCampusTheme.ActiveNavBorder);
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

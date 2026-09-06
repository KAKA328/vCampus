package cn.vcampus.client.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.table.JTableHeader;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.border.Border;

/** Shared Swing styling for the vCampus desktop client. */
final class VCampusTheme {
    // Dark enough to keep white button labels readable on every supported L&F.
    static final Color PRIMARY = new Color(30, 64, 175);
    static final Color PRIMARY_DARK = new Color(15, 23, 42);
    static final Color ACCENT = new Color(6, 182, 212);
    static final Color HEADER_BACKGROUND = new Color(37, 99, 235);
    static final Color BACKGROUND = new Color(248, 250, 252);
    static final Color SIDEBAR = new Color(239, 246, 255);
    static final Color PANEL = Color.WHITE;
    static final Color SURFACE_ALT = new Color(241, 245, 249);
    static final Color TABLE_STRIPE = new Color(248, 250, 252);
    static final Color NAV_ACTIVE_BACKGROUND = Color.WHITE;
    static final Color TEXT = new Color(30, 41, 59);
    static final Color MUTED = new Color(100, 116, 139);
    static final Color BORDER = new Color(226, 232, 240);
    static final Color SUCCESS = new Color(22, 163, 74);
    static final Color DANGER = new Color(220, 38, 38);

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
        UIManager.put("TabbedPane.font", font(Font.PLAIN, 14));
        UIManager.put("TabbedPane.selected", PANEL);
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
                padding(18, 20, 18, 20)));
    }

    static void statusPill(JLabel label, Color color) {
        label.setOpaque(true);
        label.setFont(font(Font.BOLD, 13));
        label.setForeground(color);
        label.setBackground(tint(color, 12));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(tint(color, 35)),
                padding(5, 10, 5, 10)));
    }

    private static Color tint(Color color, int percent) {
        return new Color(
                mix(PANEL.getRed(), color.getRed(), percent),
                mix(PANEL.getGreen(), color.getGreen(), percent),
                mix(PANEL.getBlue(), color.getBlue(), percent));
    }

    private static int mix(int base, int accent, int percent) {
        return (base * (100 - percent) + accent * percent) / 100;
    }

    static void field(JComponent component) {
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(8, 10, 8, 10)));
        component.setBackground(Color.WHITE);
    }

    static void table(JTable table) {
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setGridColor(BORDER);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(PRIMARY_DARK);
        table.setFont(font(Font.PLAIN, 13));
        table.setDefaultRenderer(Object.class, new ReadableTableCellRenderer());
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(SURFACE_ALT);
        header.setForeground(PRIMARY_DARK);
        header.setFont(font(Font.BOLD, 13));
        header.setBorder(BorderFactory.createLineBorder(BORDER));
    }

    static void tabs(JTabbedPane tabs) {
        tabs.setFont(font(Font.PLAIN, 14));
        tabs.setBackground(BACKGROUND);
        tabs.setForeground(PRIMARY_DARK);
    }

    static JScrollPane pageScroll(JComponent view) {
        JScrollPane scroller = scrollPane(view);
        scroller.setBorder(null);
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroller;
    }

    static JScrollPane scrollPane(JComponent view) {
        JScrollPane scroller = new JScrollPane(view);
        scroller.setBorder(BorderFactory.createLineBorder(BORDER));
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.getVerticalScrollBar().setUnitIncrement(18);
        styleScrollBar(scroller.getVerticalScrollBar());
        styleScrollBar(scroller.getHorizontalScrollBar());
        return scroller;
    }

    private static void styleScrollBar(JScrollBar scrollBar) {
        scrollBar.setUI(new SlimScrollBarUI());
        scrollBar.setPreferredSize(new Dimension(8, 8));
        scrollBar.setOpaque(false);
        scrollBar.setUnitIncrement(18);
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
        keepButtonReadable(button, SURFACE_ALT, PRIMARY_DARK);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(9, 18, 9, 18)));
    }

    static void navButton(AbstractButton button, boolean active) {
        button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        button.setBorder(active ? new ActiveNavBorder() : padding(11, 16, 11, 16));
        prepareButton(button);
        button.setBackground(active ? NAV_ACTIVE_BACKGROUND : SIDEBAR);
        button.setForeground(active ? PRIMARY : TEXT);
        button.setFont(font(active ? Font.BOLD : Font.PLAIN, 14));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setPreferredSize(new Dimension(160, 44));
    }

    private static void keepButtonReadable(final AbstractButton button, final Color background,
            final Color foreground) {
        button.setBackground(background);
        button.setForeground(foreground);
        prepareButton(button);
    }

    private static void prepareButton(AbstractButton button) {
        button.setUI(new ReadableButtonUI());
        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setRequestFocusEnabled(false);
        button.setRolloverEnabled(false);
        button.setContentAreaFilled(false);
        button.setOpaque(true);
    }

    static final class ReadableTableCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            Component component = super.getTableCellRendererComponent(
                    table, value, selected, focus, row, column);
            component.setFont(font(Font.PLAIN, 13));
            setBorder(padding(0, 10, 0, 10));
            if (selected) {
                component.setBackground(table.getSelectionBackground());
                component.setForeground(table.getSelectionForeground());
            } else {
                component.setBackground(row % 2 == 0 ? PANEL : TABLE_STRIPE);
                component.setForeground(TEXT);
            }
            return component;
        }
    }

    static final class ActiveNavBorder extends AbstractBorder {
        @Override public java.awt.Insets getBorderInsets(Component component) {
            return new java.awt.Insets(11, 20, 11, 16);
        }

        @Override public java.awt.Insets getBorderInsets(Component component, java.awt.Insets insets) {
            insets.top = 11;
            insets.left = 20;
            insets.bottom = 11;
            insets.right = 16;
            return insets;
        }

        @Override public void paintBorder(Component component, Graphics graphics,
                int x, int y, int width, int height) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(PRIMARY);
            copy.fillRoundRect(x + 5, y + 8, 4, height - 16, 4, 4);
            copy.dispose();
        }
    }

    static final class SlimScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(148, 163, 184);
            trackColor = new Color(241, 245, 249);
        }

        @Override protected JButton createDecreaseButton(int orientation) {
            return emptyButton();
        }

        @Override protected JButton createIncreaseButton(int orientation) {
            return emptyButton();
        }

        @Override protected void paintTrack(Graphics graphics, JComponent component, Rectangle bounds) {
            graphics.setColor(trackColor);
            graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override protected void paintThumb(Graphics graphics, JComponent component, Rectangle bounds) {
            if (!component.isEnabled() || bounds.width <= 0 || bounds.height <= 0) {
                return;
            }
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(thumbColor);
            copy.fillRoundRect(bounds.x + 1, bounds.y + 1,
                    Math.max(4, bounds.width - 2), Math.max(4, bounds.height - 2), 8, 8);
            copy.dispose();
        }

        private static JButton emptyButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }
    }

    /** Paints the configured button background instead of letting Windows L&F replace it. */
    static final class ReadableButtonUI extends BasicButtonUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = button.getBackground();
            if (button.getModel().isPressed() && button.isEnabled()) {
                background = background.darker();
            }
            copy.setColor(background);
            copy.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 8, 8);
            copy.dispose();
            super.paint(graphics, component);
        }

        @Override
        protected void paintText(Graphics graphics, JComponent component, Rectangle textRect, String text) {
            AbstractButton button = (AbstractButton) component;
            ButtonModel model = button.getModel();
            FontMetrics metrics = graphics.getFontMetrics();
            int shift = model.isPressed() && model.isArmed() ? getTextShiftOffset() : 0;
            graphics.setColor(button.getForeground());
            BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text,
                    button.getDisplayedMnemonicIndex(),
                    textRect.x + shift, textRect.y + metrics.getAscent() + shift);
        }
    }
}

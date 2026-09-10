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
import java.awt.Shape;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
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
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.border.Border;
import javax.swing.JPanel;
import java.awt.LayoutManager;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** Shared Swing styling for the vCampus desktop client. */
final class VCampusTheme {
    // Dark enough to keep white button labels readable on every supported L&F.
    static final Color PRIMARY = new Color(30, 64, 175);
    static final Color PRIMARY_DARK = new Color(15, 23, 42);
    static final Color ACCENT = new Color(6, 182, 212);
    static final Color HEADER_BACKGROUND = new Color(37, 99, 235);
    static final Color BACKGROUND = new Color(248, 250, 252);
    static final Color SIDEBAR = new Color(30, 41, 59);
    static final Color PANEL = Color.WHITE;
    static final Color SURFACE_ALT = new Color(241, 245, 249);
    static final Color TABLE_STRIPE = new Color(248, 250, 252);
    static final Color NAV_ACTIVE_BACKGROUND = new Color(37, 99, 235);
    static final Color NAV_TEXT = new Color(226, 232, 240);
    static final Color TEXT = new Color(30, 41, 59);
    static final Color MUTED = new Color(100, 116, 139);
    static final Color BORDER = new Color(226, 232, 240);
    static final Color SUCCESS = new Color(22, 163, 74);
    static final Color DANGER = new Color(220, 38, 38);

    // 商店按钮专属：设置该 client property 后 ReadableButtonUI 才绘制悬停浅色与圆角焦点环；
    // 未设置的按钮（其他模块）走原路径，视觉与行为零变化。
    static final String HOVER_KEY = "vcampus.button.hover";

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
        return UiMetrics.font("Microsoft YaHei UI", style, size);
    }

    static Border padding(int top, int left, int bottom, int right) {
        java.awt.Insets scaled = UiMetrics.insets(top, left, bottom, right);
        return BorderFactory.createEmptyBorder(scaled.top, scaled.left, scaled.bottom, scaled.right);
    }

    /** 抗锯齿圆角线边框；arcLogical 为逻辑圆角半径（经 UiMetrics 缩放）。供商店表面/卡片/滚动容器复用。 */
    static Border roundedBorder(final Color color, final int arcLogical) {
        return new AbstractBorder() {
            @Override public java.awt.Insets getBorderInsets(Component component) {
                int line = UiMetrics.px(1);
                return new java.awt.Insets(line, line, line, line);
            }

            @Override public java.awt.Insets getBorderInsets(Component component, java.awt.Insets insets) {
                int line = UiMetrics.px(1);
                insets.top = line;
                insets.left = line;
                insets.bottom = line;
                insets.right = line;
                return insets;
            }

            @Override public void paintBorder(Component component, Graphics graphics,
                    int x, int y, int width, int height) {
                Graphics2D copy = (Graphics2D) graphics.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(color);
                int arc = UiMetrics.px(arcLogical);
                copy.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
                copy.dispose();
            }
        };
    }

    /** 白色圆角表面：白底 + 圆角边(16) + 内边距(12,16)，把一组控件收成清晰分区。 */
    static void surface(JComponent component) {
        component.setOpaque(true);
        component.setBackground(PANEL);
        component.setBorder(BorderFactory.createCompoundBorder(
                roundedBorder(BORDER, 16), padding(12, 16, 12, 16)));
    }

    /** 圆角输入控件外观（文本框/下拉/数字器）：圆角边(10) + 白底 + 竖直内边距撑到约 36 高。 */
    static void roundedField(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(BorderFactory.createCompoundBorder(
                roundedBorder(BORDER, 10), padding(8, 12, 8, 12)));
    }

    /** 把主色按 percent 掺白，供商店强调底/边用（复用私有 tint，不新增色板）。 */
    static Color tintOf(Color color, int percent) {
        return tint(color, percent);
    }

    /** 开启按钮悬停浅色 + 圆角焦点环（仅商店按钮调用；其他模块不设此属性故零影响）。 */
    static void interactive(AbstractButton button) {
        button.putClientProperty(HOVER_KEY, Boolean.TRUE);
        button.setRolloverEnabled(true);
        button.setFocusable(true);
        button.setRequestFocusEnabled(true);
        button.setFocusPainted(true);
    }

    static void panel(JComponent component) {
        component.setBackground(PANEL);
        component.setBorder(BorderFactory.createCompoundBorder(
                roundedBorder(BORDER, 10),
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

    /** 悬停浅色：深底按钮叠半透明白、浅底按钮叠半透明主色，得到克制的 hover 反馈。 */
    private static Color hoverWash(Color background) {
        double luminance = (0.299d * background.getRed() + 0.587d * background.getGreen()
                + 0.114d * background.getBlue()) / 255.0d;
        return luminance < 0.5d
                ? new Color(255, 255, 255, 26)
                : new Color(PRIMARY.getRed(), PRIMARY.getGreen(), PRIMARY.getBlue(), 20);
    }

    static void field(JComponent component) {
        component.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                padding(8, 10, 8, 10)));
        component.setBackground(Color.WHITE);
    }

    static void table(JTable table) {
        table.setRowHeight(UiMetrics.px(34));
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
        installNestedScrollForwarding(scroller, view);
        return scroller;
    }

    /**
     * 内层列表没有滚动空间，或已抵达上下边界时，将滚轮交给外层页面。
     * 这样课程、教学班等较短列表不会截获整页滚动。
     */
    private static void installNestedScrollForwarding(final JScrollPane scroller,
            JComponent view) {
        MouseWheelListener listener = event -> forwardVerticalWheelAtBoundary(scroller, event);
        scroller.addMouseWheelListener(listener);
        view.addMouseWheelListener(listener);
    }

    static boolean forwardVerticalWheelAtBoundary(JScrollPane scroller, MouseWheelEvent event) {
        if (scroller == null || event == null || event.getWheelRotation() == 0) return false;
        JScrollBar innerBar = scroller.getVerticalScrollBar();
        int maximum = innerBar.getMaximum() - innerBar.getVisibleAmount();
        boolean scrollUp = event.getWheelRotation() < 0;
        if ((scrollUp && innerBar.getValue() > innerBar.getMinimum())
                || (!scrollUp && innerBar.getValue() < maximum)) {
            return false;
        }
        JScrollPane outer = parentScrollPane(scroller);
        if (outer == null) return false;
        JScrollBar outerBar = outer.getVerticalScrollBar();
        int outerMaximum = outerBar.getMaximum() - outerBar.getVisibleAmount();
        int units = event.getUnitsToScroll();
        if (units == 0) units = event.getWheelRotation();
        int unitIncrement = Math.max(1, outerBar.getUnitIncrement(units < 0 ? -1 : 1));
        int target = outerBar.getValue() + units * unitIncrement;
        target = Math.max(outerBar.getMinimum(), Math.min(outerMaximum, target));
        if (target == outerBar.getValue()) return false;
        outerBar.setValue(target);
        event.consume();
        return true;
    }

    private static JScrollPane parentScrollPane(JScrollPane inner) {
        java.awt.Container parent = inner.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) return (JScrollPane) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private static void styleScrollBar(JScrollBar scrollBar) {
        scrollBar.setUI(new SlimScrollBarUI());
        scrollBar.setPreferredSize(UiMetrics.dimension(8, 8));
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
        button.setForeground(active ? Color.WHITE : NAV_TEXT);
        button.setFont(font(active ? Font.BOLD : Font.PLAIN, 14));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, UiMetrics.px(44)));
        button.setPreferredSize(UiMetrics.dimension(160, 44));
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
            return UiMetrics.insets(11, 20, 11, 16);
        }

        @Override public java.awt.Insets getBorderInsets(Component component, java.awt.Insets insets) {
            java.awt.Insets scaled = UiMetrics.insets(11, 20, 11, 16);
            insets.top = scaled.top;
            insets.left = scaled.left;
            insets.bottom = scaled.bottom;
            insets.right = scaled.right;
            return insets;
        }

        @Override public void paintBorder(Component component, Graphics graphics,
                int x, int y, int width, int height) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(ACCENT);
            int left = UiMetrics.px(5);
            int top = UiMetrics.px(8);
            int indicatorWidth = UiMetrics.px(4);
            int indicatorHeight = Math.max(0, height - UiMetrics.px(16));
            int arc = UiMetrics.px(4);
            copy.fillRoundRect(x + left, y + top, indicatorWidth, indicatorHeight, arc, arc);
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
            int inset = UiMetrics.px(1);
            int minimumThumb = UiMetrics.px(4);
            int arc = UiMetrics.px(8);
            copy.fillRoundRect(bounds.x + inset, bounds.y + inset,
                    Math.max(minimumThumb, bounds.width - inset * 2),
                    Math.max(minimumThumb, bounds.height - inset * 2), arc, arc);
            copy.dispose();
        }

        private static JButton emptyButton() {
            JButton button = new JButton();
            Dimension empty = UiMetrics.dimension(0, 0);
            button.setPreferredSize(empty);
            button.setMinimumSize(empty);
            button.setMaximumSize(empty);
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
            int arc = UiMetrics.px(8);
            copy.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, arc, arc);
            // 仅商店按钮（设了 HOVER_KEY）在悬停时叠一层浅色；其他模块 isRollover 恒 false，路径不变
            if (Boolean.TRUE.equals(button.getClientProperty(HOVER_KEY))
                    && button.isEnabled() && button.getModel().isRollover()) {
                copy.setColor(hoverWash(background));
                copy.fillRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, arc, arc);
            }
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

        @Override
        protected void paintFocus(Graphics graphics, AbstractButton button,
                Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
            // 非商店按钮：保持父类默认（且它们 focusPainted=false，本就不会调用到这里）
            if (!Boolean.TRUE.equals(button.getClientProperty(HOVER_KEY))) {
                super.paintFocus(graphics, button, viewRect, textRect, iconRect);
                return;
            }
            // 商店按钮：绘制圆角焦点环（ACCENT 色、内缩 2px），替代 Swing 默认虚线方框
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(ACCENT);
            int inset = UiMetrics.px(2);
            int arc = UiMetrics.px(8);
            copy.drawRoundRect(inset, inset,
                    button.getWidth() - inset * 2 - 1, button.getHeight() - inset * 2 - 1, arc, arc);
            copy.dispose();
        }
    }

    /** 圆角裁剪容器：把子组件裁剪进圆角矩形，避免内部方角从圆角外框四角戳出毛刺。 */
    static final class RoundedClipPanel extends JPanel {
        private final int arcLogical;

        RoundedClipPanel(LayoutManager layout, int arcLogical) {
            super(layout);
            this.arcLogical = arcLogical;
            setOpaque(false);
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            int arc = UiMetrics.px(arcLogical);
            copy.clip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            super.paintChildren(copy);
            copy.dispose();
        }
    }

    /** 圆角白底容器：自身绘制圆角白底并裁剪子组件，供无边框半透明对话框做真圆角卡片。 */
    static final class RoundedSurfacePanel extends JPanel {
        private final int arcLogical;

        RoundedSurfacePanel(LayoutManager layout, int arcLogical) {
            super(layout);
            this.arcLogical = arcLogical;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(PANEL);
            int arc = UiMetrics.px(arcLogical);
            copy.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            copy.dispose();
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            int arc = UiMetrics.px(arcLogical);
            copy.clip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc));
            super.paintChildren(copy);
            copy.dispose();
        }
    }

    /** 便签式页签：圆角顶“ sticky note ”造型，选中白底主色边、未选中浅主色底，仅商店页签使用。 */
    static final class StickyTabbedPaneUI extends BasicTabbedPaneUI {
        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return super.calculateTabHeight(tabPlacement, tabIndex, fontHeight) + UiMetrics.px(10);
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                int x, int y, int w, int h, boolean isSelected) {
            Graphics2D copy = (Graphics2D) g.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(isSelected ? PANEL : tintOf(PRIMARY, 6));
            copy.fill(tabShape(tabPlacement, x, y, w, h));
            copy.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                int x, int y, int w, int h, boolean isSelected) {
            Graphics2D copy = (Graphics2D) g.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(isSelected ? tintOf(PRIMARY, 45) : BORDER);
            copy.draw(tabShape(tabPlacement, x, y, w, h));
            copy.dispose();
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            if (tabPlacement != JTabbedPane.TOP) {
                super.paintContentBorder(g, tabPlacement, selectedIndex);
                return;
            }
            // 只画一条基线替掉默认方框内容边，让页签像贴在基线上的便签
            java.awt.Insets insets = tabPane.getInsets();
            java.awt.Insets tabAreaInsets = getTabAreaInsets(tabPlacement);
            int y = insets.top + tabAreaInsets.top + maxTabHeight;
            g.setColor(BORDER);
            g.drawLine(insets.left, y, tabPane.getWidth() - insets.right, y);
        }

        private static Shape tabShape(int tabPlacement, int x, int y, int w, int h) {
            Path2D path = new Path2D.Double();
            if (tabPlacement == JTabbedPane.TOP) {
                int arc = UiMetrics.px(12);
                path.moveTo(x, y + h);
                path.lineTo(x, y + arc);
                path.quadTo(x, y, x + arc, y);
                path.lineTo(x + w - arc, y);
                path.quadTo(x + w, y, x + w, y + arc);
                path.lineTo(x + w, y + h);
            } else {
                path.append(new Rectangle(x, y, w, h), true);
            }
            path.closePath();
            return path;
        }
    }

    /** 带柔和投影的圆角白卡：外圈多层半透明环模拟阴影、内圈白底并裁剪子组件，让对话框从浅色背景中浮起。 */
    static final class ShadowedCardPanel extends JPanel {
        private final int arcLogical;
        private final int shadowLogical;

        ShadowedCardPanel(LayoutManager layout, int arcLogical, int shadowLogical) {
            super(layout);
            this.arcLogical = arcLogical;
            this.shadowLogical = shadowLogical;
            setOpaque(false);
            int shadow = UiMetrics.px(shadowLogical);
            setBorder(BorderFactory.createEmptyBorder(shadow, shadow, shadow, shadow));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int shadow = UiMetrics.px(shadowLogical);
            int arc = UiMetrics.px(arcLogical);
            // 由外向内逐层加深的半透明环，模拟柔和投影
            for (int i = shadow; i >= 1; i--) {
                int alpha = (int) Math.round(30.0d * (shadow - i + 1) / shadow);
                copy.setColor(new Color(PRIMARY_DARK.getRed(), PRIMARY_DARK.getGreen(), PRIMARY_DARK.getBlue(), alpha));
                copy.fill(new RoundRectangle2D.Double(i - 1, i - 1,
                        getWidth() - 2 * (i - 1), getHeight() - 2 * (i - 1), arc + 2 * i, arc + 2 * i));
            }
            copy.setColor(PANEL);
            copy.fill(new RoundRectangle2D.Double(shadow, shadow,
                    getWidth() - 2 * shadow, getHeight() - 2 * shadow, arc, arc));
            copy.dispose();
        }

        @Override
        protected void paintChildren(Graphics graphics) {
            Graphics2D copy = (Graphics2D) graphics.create();
            int shadow = UiMetrics.px(shadowLogical);
            int arc = UiMetrics.px(arcLogical);
            copy.clip(new RoundRectangle2D.Double(shadow, shadow,
                    getWidth() - 2 * shadow, getHeight() - 2 * shadow, arc, arc));
            super.paintChildren(copy);
            copy.dispose();
        }
    }
}

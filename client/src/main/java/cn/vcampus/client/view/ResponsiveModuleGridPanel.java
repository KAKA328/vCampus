package cn.vcampus.client.view;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Dashboard grid that recalculates card columns when the window width changes. */
final class ResponsiveModuleGridPanel extends JPanel implements Scrollable {
    private static final int DEFAULT_MIN_CARD_WIDTH = 300;
    private static final int DEFAULT_CARD_HEIGHT = 190;
    private static final int DEFAULT_GAP = 16;

    private final int minCardWidth;
    private final int cardHeight;
    private final int hgap;
    private final int vgap;

    ResponsiveModuleGridPanel() {
        this(DEFAULT_MIN_CARD_WIDTH, DEFAULT_CARD_HEIGHT, DEFAULT_GAP, DEFAULT_GAP);
    }

    ResponsiveModuleGridPanel(int minCardWidth, int cardHeight, int hgap, int vgap) {
        super(null);
        this.minCardWidth = minCardWidth;
        this.cardHeight = cardHeight;
        this.hgap = hgap;
        this.vgap = vgap;
        setOpaque(false);
    }

    int getHgap() {
        return hgap;
    }

    int getVgap() {
        return vgap;
    }

    static int columnsForWidth(int width, int minCardWidth, int gap) {
        if (width <= minCardWidth) {
            return 1;
        }
        return Math.max(1, (width + gap) / (minCardWidth + gap));
    }

    @Override public void doLayout() {
        int count = getComponentCount();
        if (count == 0) {
            return;
        }
        int columns = columnsForWidth(getWidth(), minCardWidth, hgap);
        int cardWidth = Math.max(minCardWidth, (getWidth() - (columns - 1) * hgap) / columns);
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            int x = column * (cardWidth + hgap);
            int y = row * (cardHeight + vgap);
            getComponent(index).setBounds(x, y, cardWidth, cardHeight);
        }
    }

    @Override public Dimension getPreferredSize() {
        int count = getComponentCount();
        if (count == 0) {
            return new Dimension(minCardWidth, cardHeight);
        }
        int width = Math.max(minCardWidth, getWidth());
        if (getParent() != null && getParent().getWidth() > 0) {
            width = Math.max(minCardWidth, getParent().getWidth());
        }
        int columns = Math.min(count, columnsForWidth(width, minCardWidth, hgap));
        int rows = (int) Math.ceil(count / (double) columns);
        return new Dimension(width, rows * cardHeight + Math.max(0, rows - 1) * vgap);
    }

    Dimension preferredSizeForWidth(int width) {
        int count = getComponentCount();
        if (count == 0) {
            return new Dimension(Math.max(minCardWidth, width), cardHeight);
        }
        int safeWidth = Math.max(minCardWidth, width);
        int columns = Math.min(count, columnsForWidth(safeWidth, minCardWidth, hgap));
        int rows = (int) Math.ceil(count / (double) columns);
        return new Dimension(safeWidth, rows * cardHeight + Math.max(0, rows - 1) * vgap);
    }

    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 24;
    }

    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(24, visibleRect.height - 24) : visibleRect.width;
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

package cn.vcampus.client.view;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Page body that keeps full width while allowing vertical scrolling on compact windows. */
final class ScrollablePagePanel extends JPanel implements Scrollable {
    ScrollablePagePanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 18;
    }

    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? Math.max(18, visibleRect.height - 24)
                : Math.max(18, visibleRect.width - 24);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}

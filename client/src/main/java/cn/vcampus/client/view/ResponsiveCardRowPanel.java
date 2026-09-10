package cn.vcampus.client.view;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JPanel;

/** Card row that stretches cards on wide windows and stacks them on compact ones. */
final class ResponsiveCardRowPanel extends JPanel {
    private final int minCardWidth;
    private final int gap;
    private final int maxCardWidth;

    ResponsiveCardRowPanel(int minCardWidth, int gap) {
        this(minCardWidth, gap, Integer.MAX_VALUE);
    }

    ResponsiveCardRowPanel(int minCardWidth, int gap, int maxCardWidth) {
        super(null);
        if (minCardWidth <= 0 || gap < 0 || maxCardWidth < minCardWidth) {
            throw new IllegalArgumentException("card row dimensions are invalid");
        }
        this.minCardWidth = minCardWidth;
        this.gap = gap;
        this.maxCardWidth = maxCardWidth;
        setOpaque(false);
    }

    @Override public void doLayout() {
        int count = getComponentCount();
        if (count == 0) {
            return;
        }
        Insets insets = getInsets();
        int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
        int columns = columnsFor(availableWidth, count);
        int stretchedWidth = columns == 1
                ? availableWidth
                : (availableWidth - (columns - 1) * gap) / columns;
        int cardWidth = columns == 1 ? stretchedWidth : Math.min(maxCardWidth, stretchedWidth);
        int rowWidth = columns * cardWidth + (columns - 1) * gap;

        int x = insets.left + Math.max(0, (availableWidth - rowWidth) / 2);
        int y = insets.top;
        int rowHeight = 0;
        for (int index = 0; index < count; index++) {
            if (index > 0 && index % columns == 0) {
                x = insets.left + Math.max(0, (availableWidth - rowWidth) / 2);
                y += rowHeight + gap;
                rowHeight = 0;
            }
            Component child = getComponent(index);
            int childHeight = Math.max(child.getMinimumSize().height, child.getPreferredSize().height);
            child.setBounds(x, y, cardWidth, childHeight);
            rowHeight = Math.max(rowHeight, childHeight);
            x += cardWidth + gap;
        }
    }

    @Override public Dimension getPreferredSize() {
        int count = getComponentCount();
        if (count == 0) {
            return new Dimension(minCardWidth, 0);
        }
        int width = currentWidth();
        int columns = columnsFor(width, count);
        int rows = (int) Math.ceil(count / (double) columns);
        int preferredHeight = 0;
        for (int row = 0; row < rows; row++) {
            int rowHeight = 0;
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                if (index >= count) {
                    break;
                }
                Component child = getComponent(index);
                rowHeight = Math.max(rowHeight, Math.max(
                        child.getMinimumSize().height, child.getPreferredSize().height));
            }
            preferredHeight += rowHeight;
            if (row < rows - 1) {
                preferredHeight += gap;
            }
        }
        Insets insets = getInsets();
        return new Dimension(width + insets.left + insets.right,
                preferredHeight + insets.top + insets.bottom);
    }

    private int currentWidth() {
        if (getWidth() > 0) {
            return getWidth();
        }
        if (getParent() != null && getParent().getWidth() > 0) {
            return getParent().getWidth();
        }
        return minCardWidth;
    }

    private int columnsFor(int availableWidth, int count) {
        if (availableWidth < minCardWidth * 2 + gap) {
            return 1;
        }
        return Math.max(1, Math.min(count, (availableWidth + gap) / (minCardWidth + gap)));
    }
}

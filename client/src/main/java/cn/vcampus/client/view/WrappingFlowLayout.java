package cn.vcampus.client.view;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/** Flow layout whose preferred height reflects wrapped rows. */
final class WrappingFlowLayout extends FlowLayout {
    WrappingFlowLayout(int align, int horizontalGap, int verticalGap) {
        super(align, horizontalGap, verticalGap);
    }

    @Override public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) {
                targetWidth = target.getParent().getWidth();
            }
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
            int width = 0;
            int height = 0;
            int rowWidth = 0;
            int rowHeight = 0;

            for (int index = 0; index < target.getComponentCount(); index++) {
                Component component = target.getComponent(index);
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + size.width > maxWidth) {
                    width = Math.max(width, rowWidth);
                    height += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0) {
                    rowWidth += getHgap();
                }
                rowWidth += size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }

            width = Math.max(width, rowWidth);
            height += rowHeight;
            return new Dimension(width + insets.left + insets.right + getHgap() * 2,
                    height + insets.top + insets.bottom + getVgap() * 2);
        }
    }
}

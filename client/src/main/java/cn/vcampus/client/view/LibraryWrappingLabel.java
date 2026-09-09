package cn.vcampus.client.view;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

/** 图书馆长提示按可用宽度换行，保留统一状态标签的主题和无障碍说明。 */
final class LibraryWrappingLabel extends JLabel {
    LibraryWrappingLabel(String text) {
        super();
        setText(text);
        setVerticalAlignment(TOP);
    }

    @Override public void setText(String text) {
        String plain = text == null ? "" : text;
        super.setText("<html>" + plain.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "<br>") + "</html>");
        setToolTipText(plain);
        getAccessibleContext().setAccessibleDescription(plain);
    }

    @Override public Dimension getPreferredSize() {
        Dimension natural = super.getPreferredSize();
        View html = (View) getClientProperty(BasicHTML.propertyKey);
        if (html == null) return natural;
        Insets insets = getInsets();
        int width = getWidth();
        if (width <= 0 && getParent() != null) {
            Insets parentInsets = getParent().getInsets();
            width = getParent().getWidth() - parentInsets.left - parentInsets.right;
        }
        if (width <= 0) width = UiMetrics.px(320);
        html.setSize(Math.max(1, width - insets.left - insets.right), 0);
        return new Dimension(Math.min(width, natural.width),
                (int) Math.ceil(html.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
    }

    @Override public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    @Override public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) revalidate();
    }
}

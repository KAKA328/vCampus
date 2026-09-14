package cn.vcampus.client.view;

import java.awt.Dimension;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;

/** 图书馆长提示按可用宽度换行，保留统一状态标签的主题和无障碍说明。 */
final class LibraryWrappingLabel extends JLabel {
    /** 创建按可用宽度自动换行、用于长说明文字的标签。 */
    LibraryWrappingLabel(String text) {
        super();
        setText(text);
        setVerticalAlignment(TOP);
    }

    /** 将外部文本安全转义后用于可换行显示。 */
    @Override public void setText(String text) {
        String plain = text == null ? "" : text;
        super.setText("<html>" + plain.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "<br>") + "</html>");
        setToolTipText(plain);
        getAccessibleContext().setAccessibleDescription(plain);
    }

    /** 根据可用宽度计算完整文本所需尺寸。 */
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

    /** 提供可缩放的最小尺寸，避免文本强制撑宽页面。 */
    @Override public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    /** 尺寸改变后刷新换行显示。 */
    @Override public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) revalidate();
    }
}

package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Toolkit;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** 图书详情呈现及适应屏幕尺寸的滚动对话框。 */
final class LibraryBookDetails {
    /** 构建可换行的图书详情内容，不执行借还操作。 */
    static JPanel bookDetailPanel(Book book) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(18)));
        panel.setName("libraryBookDetails");
        panel.setBackground(VCampusTheme.PANEL);
        panel.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        panel.add(LibraryWidgets.sectionHeading(book.getTitle(), book.getAuthor() + " · " + book.getCategory()),
                BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setName("libraryDetailFields");
        fields.setOpaque(false);
        LibraryWidgets.addDetailRow(fields, "图书编号", book.getBookId());
        LibraryWidgets.addDetailRow(fields, "ISBN", book.getIsbn());
        LibraryWidgets.addDetailRow(fields, "出版社", book.getPublisher());
        LibraryWidgets.addDetailRow(fields, "参考价格", LibraryRowMapper.formatPrice(book.getPrice()));
        LibraryWidgets.addDetailRow(fields, "馆藏位置", book.getLocation());
        LibraryWidgets.addDetailRow(fields, "总册数", String.valueOf(book.getTotalCopies()));
        LibraryWidgets.addDetailRow(fields, "当前可借", String.valueOf(book.getAvailableCopies()));
        panel.add(fields, BorderLayout.CENTER);

        JLabel availability = new LibraryWrappingLabel(book.getAvailableCopies() > 0
                ? "当前可借，可在馆藏列表选择后办理借阅"
                : "当前已无可借库存，请稍后再查询");
        VCampusTheme.statusPill(availability,
                book.getAvailableCopies() > 0 ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
        panel.add(availability, BorderLayout.SOUTH);
        return panel;
    }

    /** 以可缩放滚动对话框显示详情、表单或操作确认。 */
    static int showScrollableDialog(LibraryViewState v, JPanel content, String title, int options) {
        JScrollPane scroller = VCampusTheme.pageScroll(content);
        scroller.setPreferredSize(UiMetrics.dimension(520, 480));
        JOptionPane pane = new JOptionPane(scroller, JOptionPane.PLAIN_MESSAGE, options);
        JDialog dialog = pane.createDialog(v.panel, title);
        dialog.setResizable(true);
        Rectangle screen = dialog.getGraphicsConfiguration().getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
        int availableWidth = Math.max(1, screen.width - screenInsets.left - screenInsets.right - UiMetrics.px(24));
        int availableHeight = Math.max(1, screen.height - screenInsets.top - screenInsets.bottom - UiMetrics.px(24));
        Dimension packed = dialog.getSize();
        dialog.setMinimumSize(new Dimension(Math.min(UiMetrics.px(360), availableWidth),
                Math.min(UiMetrics.px(300), availableHeight)));
        dialog.setSize(Math.min(packed.width, availableWidth), Math.min(packed.height, availableHeight));
        dialog.setLocationRelativeTo(v.panel);
        try {
            dialog.setVisible(true);
            Object selected = pane.getValue();
            return selected instanceof Integer ? ((Integer) selected).intValue() : JOptionPane.CLOSED_OPTION;
        } finally {
            dialog.dispose();
        }
    }
}

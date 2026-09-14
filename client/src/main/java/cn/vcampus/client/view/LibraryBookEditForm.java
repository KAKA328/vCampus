package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import java.awt.GridBagLayout;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** 馆藏资料编辑表单；不提供图书号或库存输入框。 */
final class LibraryBookEditForm {
    /** 打开表单时的原资料，用于服务器冲突检查。 */
    final Book expected;
    /** 可编辑的书名。 */
    final JTextField title;
    /** 可编辑的作者。 */
    final JTextField author;
    /** 可编辑的 ISBN。 */
    final JTextField isbn;
    /** 分类下拉框。 */
    final JComboBox<String> category = LibraryWidgets.categoryChoices(false);
    /** 可编辑的出版社。 */
    final JTextField publisher;
    /** 允许零元的参考价格。 */
    final JTextField price;
    /** 可编辑的馆藏位置。 */
    final JTextField location;
    /** 由滚动对话框容纳的表单面板。 */
    final JPanel panel = new JPanel(new GridBagLayout());
    /** 使用最新资料预填，保留旧的自定义分类。 */
    LibraryBookEditForm(Book book) {
        expected = book;
        title = new JTextField(book.getTitle()); author = new JTextField(book.getAuthor());
        isbn = new JTextField(book.getIsbn()); publisher = new JTextField(book.getPublisher());
        price = new JTextField(String.valueOf(book.getPrice())); location = new JTextField(book.getLocation());
        LibraryWidgets.addCategoryChoice(category, book.getCategory());
        category.setSelectedItem(book.getCategory());
        panel.setOpaque(false);
        LibraryWidgets.addField(panel, "书名*", title); LibraryWidgets.addField(panel, "作者*", author);
        LibraryWidgets.addField(panel, "ISBN", isbn); LibraryWidgets.addField(panel, "分类", category);
        LibraryWidgets.addField(panel, "出版社", publisher); LibraryWidgets.addField(panel, "参考价格（元）*", price);
        LibraryWidgets.addField(panel, "馆藏位置", location);
        price.setToolTipText("允许0元；不会改变已生成赔偿单的金额");
    }
    /** 仅构造资料变化，图书号和库存保持原快照，由服务器合并最新库存。 */
    Book replacement() {
        Object selected = category.getSelectedItem();
        return new Book(expected.getBookId(), title.getText(), author.getText(), isbn.getText(),
                selected == null ? "" : selected.toString(), publisher.getText(), LibraryBookInput.price(price.getText()),
                expected.getTotalCopies(), expected.getAvailableCopies(), location.getText());
    }
}

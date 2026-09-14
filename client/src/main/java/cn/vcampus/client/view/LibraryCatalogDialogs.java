package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import java.awt.BorderLayout;
import java.awt.GridBagLayout;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** 管理员新增馆藏和补充现有图书库存的交互。 */
final class LibraryCatalogDialogs {
    /** 显示新增馆藏表单，经本地校验后调用原有新增接口。 */
    static void showAddBookDialog(LibraryViewState v) {
        final JTextField id = new JTextField();
        final JTextField title = new JTextField();
        final JTextField author = new JTextField();
        final JTextField isbn = new JTextField();
        final JComboBox<String> category = LibraryWidgets.categoryChoices(false);
        for (int index = 1; index < v.categoryField.getItemCount(); index++) {
            LibraryWidgets.addCategoryChoice(category, v.categoryField.getItemAt(index));
        }
        final JTextField publisher = new JTextField();
        final JTextField price = new JTextField();
        price.setToolTipText("允许0元图书；价格不能为负数");
        final JTextField copies = new JTextField("1");
        final JTextField location = new JTextField();
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        LibraryWidgets.addField(form, "图书号*", id);
        LibraryWidgets.addField(form, "书名*", title);
        LibraryWidgets.addField(form, "作者*", author);
        LibraryWidgets.addField(form, "ISBN", isbn);
        LibraryWidgets.addField(form, "分类*", category);
        LibraryWidgets.addField(form, "出版社", publisher);
        LibraryWidgets.addField(form, "参考价格（元）*", price);
        LibraryWidgets.addField(form, "初始册数*", copies);
        LibraryWidgets.addField(form, "馆藏位置", location);
        JPanel dialog = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
        dialog.setBackground(VCampusTheme.PANEL);
        dialog.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        dialog.add(LibraryWidgets.sectionHeading("录入馆藏", "带 * 的字段为必填项；价格允许0元，新书初始可借册数与总册数一致。"),
                BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        while (LibraryBookDetails.showScrollableDialog(v, dialog, "新增图书", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                int total = Integer.parseInt(copies.getText().trim());
                double referencePrice = LibraryBookInput.price(price.getText());
                if (total <= 0) {
                    throw new IllegalArgumentException("copies must be positive");
                }
                final Book book = new Book(id.getText(), title.getText(), author.getText(), isbn.getText(),
                        String.valueOf(category.getSelectedItem()), publisher.getText(), referencePrice,
                        total, total, location.getText());
                LibraryRequestRunner.runRequest(v, "正在新增图书…", service -> service.addBook(v.session.getToken(), book), response -> {
                    if (!LibraryRequestRunner.isSuccessful(v, response)) return;
                    LibraryRequestRunner.showStatus(v, "新增图书成功，正在刷新馆藏…", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(() -> LibraryCatalogActions.loadBooks(v));
                });
                return;
            } catch (NumberFormatException invalidNumber) {
                JOptionPane.showMessageDialog(v.panel, "参考价格必须是非负数字（允许0元），初始册数必须是正整数",
                        "请检查输入", JOptionPane.WARNING_MESSAGE);
            } catch (IllegalArgumentException invalidBook) {
                JOptionPane.showMessageDialog(v.panel, "请填写图书号、书名、作者；价格允许0元但不能为负数，册数必须为正整数",
                        "请检查输入", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /** 读取当前库存后填写增加册数，提交后刷新馆藏。 */
    static void showRestockDialog(LibraryViewState v) {
        final String bookId = LibraryCatalogActions.selectedBookId(v);
        if (bookId == null) {
            LibraryRequestRunner.showStatus(v, "请先选择需要补充库存的图书", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在读取当前库存…", service -> service.detail(v.session.getToken(), bookId), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response) || !(response.getPayload() instanceof Book)) return;
            Book book = (Book) response.getPayload();
            JTextField copies = new JTextField("1", 10);
            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            LibraryWidgets.addField(form, "新增册数*", copies);
            JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
            content.add(LibraryWidgets.sectionHeading("为《" + book.getTitle() + "》增加库存",
                    "图书号 " + bookId + "；当前总量 " + book.getTotalCopies() + " 册，可借 "
                            + book.getAvailableCopies() + " 册。只填写本次增加数量，不是新的总量；借出记录不变。"), BorderLayout.NORTH);
            content.add(form, BorderLayout.CENTER);
            while (LibraryBookDetails.showScrollableDialog(v, content, "增加库存", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                final int count;
                try {
                    count = Integer.parseInt(copies.getText().trim());
                    if (count <= 0) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    JOptionPane.showMessageDialog(v.panel, "新增册数必须是大于 0 的整数", "请检查输入", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                SwingUtilities.invokeLater(() -> LibraryRequestRunner.runRequest(v, "正在增加库存…", service -> service.restock(v.session.getToken(), bookId, count), result -> {
                    if (!LibraryRequestRunner.isSuccessful(v, result)) return;
                    LibraryRequestRunner.showStatus(v, "库存已增加 " + count + " 册，正在刷新馆藏…", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(() -> LibraryCatalogActions.loadBooks(v));
                }, "未能确认增加库存的结果，请先查询该书核对总量，再决定是否重试，避免重复补充。"));
                return;
            }
        });
    }
}

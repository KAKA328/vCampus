package cn.vcampus.client.view;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** 馆藏检索、详情查询与读者借阅操作。 */
final class LibraryCatalogActions {
    /** 清空关键词与分类，不修改馆藏数据。 */
    static void resetSearch(LibraryViewState v) {
        v.keywordField.setText("");
        v.categoryField.setSelectedIndex(0);
        LibraryRequestRunner.showStatus(v, "筛选条件已清空，可重新查询全部馆藏", VCampusTheme.MUTED);
        v.keywordField.requestFocusInWindow();
    }

    /** 异步查询当前筛选条件下的馆藏。 */
    static void loadBooks(LibraryViewState v) {
        final String keyword = v.keywordField.getText().trim();
        final String category = LibraryCatalogActions.selectedCategory(v);
        LibraryRequestRunner.runRequest(v, "正在查询馆藏…", service -> service.search(v.session.getToken(), keyword, category),
                response -> LibraryViewData.showBooks(v, response));
    }

    /** 加载选中图书的服务器详情。 */
    static void loadSelectedDetail(LibraryViewState v) {
        final String bookId = LibraryCatalogActions.selectedBookId(v);
        if (bookId == null) {
            LibraryRequestRunner.showStatus(v, "请先选择一本图书", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在查询图书详情…", service -> service.detail(v.session.getToken(), bookId), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response) || !(response.getPayload() instanceof Book)) {
                if (response.getStatusCode() == StatusCode.OK) {
                    LibraryRequestRunner.showStatus(v, "服务器返回的图书详情格式不正确", VCampusTheme.DANGER);
                }
                return;
            }
            Book book = (Book) response.getPayload();
            LibraryBookDetails.showScrollableDialog(v, LibraryBookDetails.bookDetailPanel(book), "图书详情", JOptionPane.DEFAULT_OPTION);
            LibraryRequestRunner.showStatus(v, "已加载《" + book.getTitle() + "》的详情", VCampusTheme.SUCCESS);
        });
    }

    /** 提交选中图书的批量借阅请求，成功后刷新馆藏与记录。 */
    static void borrowSelected(LibraryViewState v) {
        final List<String> bookIds = LibraryCatalogActions.selectedBookIds(v);
        if (bookIds.isEmpty()) {
            LibraryRequestRunner.showStatus(v, "请先选择一本或多本图书", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在提交借阅请求…", service -> service.borrow(v.session.getToken(), bookIds), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response)) return;
            int count = response.getPayload() instanceof List<?> ? ((List<?>) response.getPayload()).size() : bookIds.size();
            LibraryRequestRunner.showStatus(v, "借阅成功，共 " + count + " 本；正在刷新馆藏与到期提醒…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(() -> LibraryCirculationActions.refreshCatalogAndHistory(v));
        });
    }

    /** 读取筛选分类，全部分类对应空条件。 */
    static String selectedCategory(LibraryViewState v) {
        return v.categoryField.getSelectedIndex() == 0 ? "" : String.valueOf(v.categoryField.getSelectedItem());
    }

    /** 取得选中行对应的图书编号。 */
    static String selectedBookId(LibraryViewState v) {
        int selected = v.bookTable.getSelectedRow();
        if (selected < 0) return null;
        return String.valueOf(v.bookModel.getValueAt(v.bookTable.convertRowIndexToModel(selected), 0));
    }

    /** 取得所有选中行的图书编号，不使用视图行号作为业务编号。 */
    static List<String> selectedBookIds(LibraryViewState v) {
        List<String> ids = new ArrayList<String>();
        for (int selected : v.bookTable.getSelectedRows()) {
            ids.add(String.valueOf(v.bookModel.getValueAt(v.bookTable.convertRowIndexToModel(selected), 0)));
        }
        return ids;
    }
}

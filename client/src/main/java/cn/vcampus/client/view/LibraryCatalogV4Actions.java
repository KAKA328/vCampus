package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import cn.vcampus.library.LibraryBookMetadata;
import cn.vcampus.library.LibraryCopy;
import cn.vcampus.library.LibraryCopyStatus;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/** 馆员 V4 交互：资料编辑和实体册清单；业务权限始终由服务器决定。 */
final class LibraryCatalogV4Actions {
    /** 先读取最新资料，后台任务结束后才弹出编辑表单。 */
    static void editSelected(LibraryViewState v) {
        String id = LibraryCatalogActions.selectedBookId(v);
        if (!v.manager || id == null) {
            LibraryRequestRunner.showStatus(v, "请先选择需要编辑的图书", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在读取图书资料…", service -> service.detail(v.session.getToken(), id), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response) || !(response.getPayload() instanceof Book)) return;
            Book expected = (Book) response.getPayload();
            SwingUtilities.invokeLater(() -> edit(v, expected));
        });
    }
    /** 不允许改变图书号和库存；冲突时保留服务器资料并提示重新读取。 */
    private static void edit(LibraryViewState v, Book expected) {
        LibraryBookEditForm form = new LibraryBookEditForm(expected);
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        content.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        content.add(LibraryWidgets.sectionHeading("编辑《" + expected.getTitle() + "》",
                "图书号 " + expected.getBookId() + " 不可修改；库存请用增加库存。历史书名和已生成赔偿单不会随本次编辑改变。"),
                BorderLayout.NORTH);
        content.add(form.panel, BorderLayout.CENTER);
        while (LibraryBookDetails.showScrollableDialog(v, content, "编辑图书资料", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            final Book replacement;
            try {
                replacement = form.replacement();
                LibraryBookMetadata.validateEdit(v.session.getUser().getUserId(), expected, replacement);
            } catch (IllegalArgumentException | NullPointerException invalid) {
                JOptionPane.showMessageDialog(v.panel, "请填写书名和作者，使用有限非负价格，并检查字段长度。\n" + invalid.getMessage(),
                        "请检查输入", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            LibraryRequestRunner.runRequest(v, "正在保存图书资料…",
                    service -> service.updateBook(v.session.getToken(), expected, replacement), response -> {
                        if (!LibraryRequestRunner.isSuccessful(v, response)) return;
                        LibraryRequestRunner.showStatus(v, "资料已保存，正在刷新馆藏与借阅快照…", VCampusTheme.SUCCESS);
                        SwingUtilities.invokeLater(() -> LibraryCirculationActions.refreshCatalogAndHistory(v));
                    }, "无法确认保存结果，请刷新核对资料；未自动重试编辑。");
            return;
        }
    }
    /** 加载实体册清单，不提供绕过借还事务的状态编辑。 */
    static void copiesSelected(LibraryViewState v) {
        String id = LibraryCatalogActions.selectedBookId(v);
        if (!v.manager || id == null) {
            LibraryRequestRunner.showStatus(v, "请先选择需要查看实体册的图书", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在查询实体册…", service -> service.copies(v.session.getToken(), id), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response)) return;
            if (!LibraryViewData.isListOf(response, LibraryCopy.class)) {
                LibraryRequestRunner.showStatus(v, "服务器返回的实体册格式不正确", VCampusTheme.DANGER);
                return;
            }
            List<LibraryCopy> copies = new ArrayList<LibraryCopy>();
            for (Object item : (List<?>) response.getPayload()) copies.add((LibraryCopy) item);
            LibraryBookDetails.showScrollableDialog(v, copiesPanel(id, copies), "实体副本", JOptionPane.DEFAULT_OPTION);
        });
    }
    /** 长编号用固定列宽及水平滚动呈现，不与状态文字重叠。 */
    static JPanel copiesPanel(String bookId, List<LibraryCopy> copies) {
        JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        content.add(LibraryWidgets.sectionHeading("实体册 · " + bookId,
                "每册独立编号；借阅自动分配，归还恢复原册，遗失册不因赔偿恢复库存。共 " + copies.size() + " 册记录。"),
                BorderLayout.NORTH);
        BatchTableModel model = new BatchTableModel(new Object[] {"实体册编号", "图书号", "状态"});
        List<Object[]> rows = new ArrayList<Object[]>();
        for (LibraryCopy copy : copies) rows.add(new Object[] {copy.getCopyId(), copy.getBookId(), status(copy.getStatus())});
        model.replaceRows(rows);
        JTable table = new JTable(model);
        table.setName("libraryCopyTable");
        LibraryTableStyles.configureTable(table, ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        LibraryTableStyles.applyColumnWidths(table, new int[] {360, 100, 150});
        content.add(VCampusTheme.scrollPane(table), BorderLayout.CENTER);
        return content;
    }
    /** 旧库不明占用明确标记待核对，不假标为可借。 */
    static String status(LibraryCopyStatus status) {
        switch (status) {
            case AVAILABLE: return "在馆可借";
            case BORROWED: return "已借出";
            case LOST: return "已遗失";
            default: return "旧库存待核对";
        }
    }
}

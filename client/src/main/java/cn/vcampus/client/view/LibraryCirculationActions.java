package cn.vcampus.client.view;

import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowStatus;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/** 借阅记录加载、归还与多个关联视图的顺序刷新。 */
final class LibraryCirculationActions {
    /** 首次显示页面时加载馆藏和借阅信息。 */
    static void loadInitialData(LibraryViewState v) {
        LibraryCirculationActions.refreshCatalogAndHistory(v);
    }

    /** 顺序刷新馆藏和借阅记录，避免多个后台任务争用状态。 */
    static void refreshCatalogAndHistory(LibraryViewState v) {
        final String keyword = v.keywordField.getText().trim();
        final String category = LibraryCatalogActions.selectedCategory(v);
        LibraryRequestRunner.runRequest(v, "正在刷新馆藏与借阅期限…", service -> service.search(v.session.getToken(), keyword, category), response -> {
            LibraryViewData.showBooks(v, response);
            SwingUtilities.invokeLater(() -> LibraryCirculationActions.loadHistory(v, v.manager));
        });
    }

    /** 加载赔偿与余额；忙碌时排队一次只读刷新。 */
    static void loadCompensations(LibraryViewState v) {
        if (v.requestInProgress) {
            v.compensationRefreshQueued = true;
            return;
        }
        v.compensationRefreshQueued = false;
        if (v.requestInProgress) return;
        LibraryCompensationActions.prepareCompensationRefresh(v);
        final LibraryReadRequests.CompensationRequest request = new LibraryReadRequests.CompensationRequest(v);
        LibraryRequestRunner.runRequest(v, "正在查询赔偿记录与校园钱包…", request, ignored -> {
            boolean recordsOk = LibraryViewData.showCompensations(v, request.compensations);
            boolean walletOk = v.manager || LibraryViewData.showWalletBalance(v, request.wallet);
            LibraryRequestRunner.showStatus(v, recordsOk && walletOk ? "赔偿记录已更新" + (v.manager ? "。" : "，校园钱包余额已刷新。")
                    : "赔偿记录或余额暂不可用，请刷新重试。", recordsOk && walletOk ? VCampusTheme.SUCCESS : VCampusTheme.ACCENT);
        });
    }

    /** 在一个后台任务中刷新借阅、馆藏、赔偿和钱包的关联视图。 */
    static void refreshLibrary(LibraryViewState v, boolean all) {
        if (v.requestInProgress) return;
        LibraryCompensationActions.prepareCompensationRefresh(v);
        final LibraryReadRequests.RefreshRequest request = new LibraryReadRequests.RefreshRequest(v, all, v.keywordField.getText().trim(), LibraryCatalogActions.selectedCategory(v));
        LibraryRequestRunner.runRequest(v, "正在刷新馆藏、借阅、赔偿与校园钱包…", request, ignored -> {
            List<String> unavailable = new ArrayList<String>();
            if (!LibraryViewData.cacheBookTitles(v, request.completeCatalog, true)) unavailable.add("完整图书名称");
            if (LibraryViewData.isListOf(request.catalog, Book.class)) LibraryViewData.showBooks(v, request.catalog);
            else unavailable.add("馆藏");
            if (!LibraryViewData.showHistory(v, request.history, all ? "全部借阅记录" : "我的借阅记录")) unavailable.add("借阅记录");
            if (!LibraryViewData.showCompensations(v, request.compensations)) unavailable.add("赔偿记录（暂不可支付）");
            if (!v.manager && !LibraryViewData.showWalletBalance(v, request.wallet)) unavailable.add("校园钱包余额");
            if (unavailable.isEmpty()) {
                LibraryRequestRunner.showStatus(v, "馆藏、借阅及赔偿记录已同步" + (v.manager ? "。" : "，校园钱包余额已刷新。"), VCampusTheme.SUCCESS);
            } else {
                LibraryRequestRunner.showStatus(v, "部分查询暂不可用：" + String.join("、", unavailable)
                        + "。其余结果已更新，请刷新重试；旧记录仅供参考。", VCampusTheme.ACCENT);
            }
        });
    }

    /** 加载本人借阅记录和书名映射。 */
    static void loadOwnHistory(LibraryViewState v) {
        LibraryCirculationActions.loadHistory(v, false);
    }

    /** 加载管理员可见的全部借阅记录。 */
    static void loadAllHistory(LibraryViewState v) {
        LibraryCirculationActions.loadHistory(v, true);
    }

    /** 根据角色范围读取借阅记录，并独立尝试补充书名。 */
    static void loadHistory(LibraryViewState v, boolean all) {
        final LibraryReadRequests.HistoryRequest request = new LibraryReadRequests.HistoryRequest(v, all);
        LibraryRequestRunner.runRequest(v, "正在查询借阅记录和图书名称…", request, response -> {
            boolean titlesLoaded = LibraryViewData.cacheBookTitles(v, request.catalog, true);
            if (LibraryViewData.showHistory(v, response, all ? "全部借阅记录" : "我的借阅记录") && !titlesLoaded) {
                LibraryRequestRunner.showStatus(v, "借阅记录已加载，部分图书名称暂不可用；仍可按记录办理归还。", VCampusTheme.ACCENT);
            }
        });
    }

    /** 按选中记录编号提交归还，成功后刷新相关视图。 */
    static void returnSelected(LibraryViewState v) {
        int selected = v.historyTable.getSelectedRow();
        if (selected < 0) {
            LibraryRequestRunner.showStatus(v, "请先选择一条借阅记录", VCampusTheme.DANGER);
            return;
        }
        int modelRow = v.historyTable.convertRowIndexToModel(selected);
        final String recordId = String.valueOf(v.historyModel.getValueAt(modelRow, 0));
        String recordStatus = String.valueOf(v.historyModel.getValueAt(modelRow, LibraryViewState.HISTORY_STATUS_COLUMN));
        if (!BorrowStatus.BORROWED.name().equals(recordStatus)) {
            LibraryRequestRunner.showStatus(v, "只有借阅中的记录可以归还；已归还、已遗失或已赔偿的记录不能重复办理归还。", VCampusTheme.DANGER);
            return;
        }
        LibraryRequestRunner.runRequest(v, "正在办理归还…", service -> service.returnBook(v.session.getToken(), recordId), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response)) return;
            LibraryRequestRunner.showStatus(v, "归还成功，正在刷新馆藏与到期提醒…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(() -> LibraryCirculationActions.refreshCatalogAndHistory(v));
        });
    }
}

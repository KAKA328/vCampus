package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.library.LibraryLoanSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** 将已校验的服务器响应投影到图书馆视图状态。 */
final class LibraryViewData {
    /** 仅展示授权范围的有效账单，异常响应使付款不可用。 */
    static boolean showCompensations(LibraryViewState v, Message response) {
        v.compensationsLoaded = false;
        if (!LibraryViewData.isListOf(response, LibraryCompensation.class)) {
            LibraryRequestRunner.updateButtonState(v);
            return false;
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        Map<String, LibraryCompensation> items = new HashMap<String, LibraryCompensation>();
        for (Object value : (List<?>) response.getPayload()) {
            LibraryCompensation item = (LibraryCompensation) value;
            // 服务端必须限制范围；客户端也不展示意外混入的其他读者账单。
            if (!v.manager && !v.session.getUser().getUserId().equals(item.getUserId())) continue;
            rows.add(LibraryRowMapper.compensationRow(item));
            items.put(item.getCompensationId(), item);
        }
        v.currentCompensations.clear();
        v.currentCompensations.putAll(items);
        v.compensationModel.replaceRows(rows);
        v.compensationsLoaded = true;
        LibraryRequestRunner.updateButtonState(v);
        return true;
    }

    /** 仅接受有效的非负整数分余额，否则提示重新刷新。 */
    static boolean showWalletBalance(LibraryViewState v, Message response) {
        v.currentWalletBalance = null;
        if (v.manager || response == null || response.getStatusCode() != StatusCode.OK
                || !(response.getPayload() instanceof Long) || ((Long) response.getPayload()).longValue() < 0) {
            v.walletBalanceLabel.setText("校园钱包余额：暂不可用，请刷新（与商店共用）。");
            return false;
        }
        v.currentWalletBalance = (Long) response.getPayload();
        v.walletBalanceLabel.setText("校园钱包余额：" + LibraryRowMapper.formatCents(v.currentWalletBalance.longValue()) + "（与商店共用）");
        return true;
    }

    /** 检查成功响应是否为指定类型元素的列表。 */
    static boolean isListOf(Message response, Class<?> itemType) {
        if (response == null || response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) return false;
        for (Object item : (List<?>) response.getPayload()) if (!itemType.isInstance(item)) return false;
        return true;
    }

    /** 校验馆藏响应后替换列表、补充分类并更新数量概览。 */
    static void showBooks(LibraryViewState v, Message response) {
        if (!LibraryRequestRunner.isSuccessful(v, response)) return;
        if (!(response.getPayload() instanceof List<?>)) {
            LibraryRequestRunner.showStatus(v, "服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> books = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        int availableCopies = 0;
        for (Object item : books) {
            if (!(item instanceof Book)) {
                LibraryRequestRunner.showStatus(v, "服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            Book book = (Book) item;
            LibraryWidgets.addCategoryChoice(v.categoryField, book.getCategory());
            rows.add(LibraryRowMapper.bookRow(book));
            availableCopies += book.getAvailableCopies();
        }
        v.bookModel.replaceRows(rows);
        LibraryViewData.cacheBookTitles(v, response, false);
        v.catalogCountValue.setText(books.size() + " 种");
        v.availableCountValue.setText(availableCopies + " 册");
        LibraryRequestRunner.showStatus(v, "已显示符合条件的图书，共 " + books.size() + " 种", VCampusTheme.SUCCESS);
    }

    /** 校验借阅响应后更新列表、在借数量和到期提醒。 */
    static boolean showHistory(LibraryViewState v, Message response, String scope) {
        if (!LibraryRequestRunner.isSuccessful(v, response)) return false;
        if (!(response.getPayload() instanceof List<?>)) {
            LibraryRequestRunner.showStatus(v, "服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
            return false;
        }
        List<?> records = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        List<BorrowRecord> borrowingRecords = new ArrayList<BorrowRecord>();
        int activeLoans = 0;
        for (Object item : records) {
            if (!(item instanceof BorrowRecord) && !(item instanceof LibraryLoanSnapshot)) {
                LibraryRequestRunner.showStatus(v, "服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
                return false;
            }
            LibraryLoanSnapshot snapshot = item instanceof LibraryLoanSnapshot ? (LibraryLoanSnapshot) item : null;
            BorrowRecord record = snapshot == null ? (BorrowRecord) item : snapshot.getRecord();
            if (!v.manager && !v.session.getUser().getUserId().equals(record.getUserId())) continue;
            borrowingRecords.add(record);
            Object[] row = java.util.Arrays.copyOf(LibraryRowMapper.historyRow(record,
                    snapshot == null ? v.bookTitles.get(record.getBookId()) : snapshot.getBookTitle()), 11);
            row[9] = snapshot == null || snapshot.getCopyId() == null ? "历史未记录" : snapshot.getCopyId();
            row[10] = snapshot == null ? "当前馆藏（兼容显示）" : snapshot.isHistoricalBackfill() ? "迁移回填" : "借阅时快照";
            rows.add(row);
            if (record.getStatus() == BorrowStatus.BORROWED) {
                activeLoans++;
            }
        }
        v.historyModel.replaceRows(rows);
        v.activeLoanCountValue.setText(activeLoans + " 条");
        v.currentRecords = borrowingRecords;
        v.historyLoaded = true;
        LibraryReminderController.updateDueReminder(v, borrowingRecords);
        LibraryRequestRunner.showStatus(v, "已显示" + scope + "，共 " + records.size() + " 条", VCampusTheme.SUCCESS);
        return true;
    }

    /** 维护当前馆藏名称缓存；它不是借阅时的历史名称快照。 */
    static boolean cacheBookTitles(LibraryViewState v, Message response, boolean completeCatalog) {
        if (response == null || response.getStatusCode() != StatusCode.OK
                || !(response.getPayload() instanceof List<?>)) return false;
        Map<String, String> names = new HashMap<String, String>();
        for (Object item : (List<?>) response.getPayload()) {
            if (!(item instanceof Book)) return false;
            Book book = (Book) item;
            names.put(book.getBookId(), book.getTitle());
        }
        if (completeCatalog) v.bookTitles.clear();
        v.bookTitles.putAll(names);
        return true;
    }
}

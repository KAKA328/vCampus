package cn.vcampus.client.view;

import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** 遗失确认及读者本人支付交互；写请求失败时不自动重试。 */
final class LibraryCompensationActions {
    /** 刷新前使旧赔偿和余额状态失效，避免使用旧数据继续付款。 */
    static void prepareCompensationRefresh(LibraryViewState v) {
        v.compensationsLoaded = false;
        v.currentWalletBalance = null;
        if (!v.manager) v.walletBalanceLabel.setText("校园钱包余额：等待刷新结果（与商店共用）。");
        LibraryRequestRunner.updateButtonState(v);
    }

    /** 馆员核对图书、借阅人和服务器原价后确认遗失。 */
    static void declareSelectedLoss(LibraryViewState v) {
        if (!v.manager || v.requestInProgress) return;
        int selected = v.historyTable.getSelectedRow();
        if (selected < 0) {
            LibraryRequestRunner.showStatus(v, "请先在流通管理中选择一条借阅中的记录", VCampusTheme.DANGER);
            return;
        }
        int row = v.historyTable.convertRowIndexToModel(selected);
        if (!BorrowStatus.BORROWED.name().equals(String.valueOf(v.historyModel.getValueAt(row, LibraryViewState.HISTORY_STATUS_COLUMN)))) {
            LibraryRequestRunner.showStatus(v, "只能为借阅中的记录确认遗失，已有赔偿单请到“遗失赔偿”查看。", VCampusTheme.DANGER);
            return;
        }
        final String recordId = String.valueOf(v.historyModel.getValueAt(row, 0));
        final String userId = String.valueOf(v.historyModel.getValueAt(row, 2));
        final String bookId = String.valueOf(v.historyModel.getValueAt(row, 3));
        LibraryRequestRunner.runRequest(v, "正在读取服务器馆藏原价…", service -> service.detail(v.session.getToken(), bookId), response -> {
            if (!LibraryRequestRunner.isSuccessful(v, response) || !(response.getPayload() instanceof Book)) return;
            Book book = (Book) response.getPayload();
            JPanel confirmation = LibraryWidgets.confirmationPanel("确认遗失并按原价赔偿",
                    "借阅人：" + userId + "\n图书：《" + book.getTitle() + "》（" + bookId + "）\n借阅记录：" + recordId
                            + "\n服务器当前原价：" + LibraryRowMapper.formatPrice(book.getPrice())
                            + "\n此金额仅供核对，最终赔偿金额由服务器按确认时原价生成并锁定。"
                            + "\n确认后该记录转为遗失，不再办理普通归还；由借阅人本人确认支付，管理员不会直接扣款。");
            if (LibraryBookDetails.showScrollableDialog(v, confirmation, "确认遗失", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            SwingUtilities.invokeLater(() -> LibraryRequestRunner.runRequest(v, "正在确认遗失并生成赔偿单…",
                    service -> service.declareLoss(v.session.getToken(), recordId), result -> {
                        if (!LibraryRequestRunner.isSuccessful(v, result)) return;
                        v.workspaceTabs.setSelectedIndex(2);
                        LibraryRequestRunner.showStatus(v, "遗失已确认，赔偿单已生成，等待借阅人本人支付。", VCampusTheme.SUCCESS);
                        SwingUtilities.invokeLater(() -> LibraryCirculationActions.refreshLibrary(v, v.manager));
                    }, "尚未确认遗失请求结果，请先刷新借阅和赔偿记录核对；本次不会自动重新提交。"));
        });
    }

    /** 确认选中账单属于本人且待支付，再显示扣款确认。 */
    static void paySelectedCompensation(LibraryViewState v) {
        if (v.manager || !v.compensationsLoaded || v.requestInProgress) return;
        int selected = v.compensationTable.getSelectedRow();
        if (selected < 0) {
            LibraryRequestRunner.showStatus(v, "请先选择一条待支付的赔偿单", VCampusTheme.DANGER);
            return;
        }
        int row = v.compensationTable.convertRowIndexToModel(selected);
        String id = String.valueOf(v.compensationModel.getValueAt(row, 0));
        final LibraryCompensation item = v.currentCompensations.get(id);
        if (item == null || item.getStatus() != CompensationStatus.PENDING
                || !v.session.getUser().getUserId().equals(item.getUserId())) {
            LibraryRequestRunner.showStatus(v, "只能支付本人待支付的赔偿单；已赔偿记录无需重复支付。", VCampusTheme.DANGER);
            return;
        }
        if (LibraryBookDetails.showScrollableDialog(v, LibraryWidgets.confirmationPanel(item.getAmountCents() == 0 ? "确认零元赔偿结清" : "确认原价赔偿支付",
                LibraryRowMapper.paymentConfirmationText(item, v.currentWalletBalance)),
                item.getAmountCents() == 0 ? "确认结清" : "确认支付", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        LibraryCompensationActions.submitCompensationPayment(v, item);
    }

    /** 用户确认后提交一次赔偿支付；结果不明时先刷新，不自动重扣。 */
    static void submitCompensationPayment(LibraryViewState v, final LibraryCompensation item) {
        if (v.manager || v.requestInProgress || !v.compensationsLoaded || item == null
                || item.getStatus() != CompensationStatus.PENDING
                || !v.session.getUser().getUserId().equals(item.getUserId())) return;
        // 结果未知时必须先读回账单状态，不能靠旧列表连续重提写操作。
        v.compensationsLoaded = false;
        LibraryRequestRunner.runRequest(v, "正在使用校园钱包支付赔偿…", service -> service.payCompensation(v.session.getToken(), item.getCompensationId()), response -> {
            if (response != null && response.getStatusCode() == StatusCode.PAYMENT_REQUIRED) {
                v.currentWalletBalance = null;
                v.walletBalanceLabel.setText("校园钱包余额需刷新；余额不足，请到商店 → 钱包充值。");
                LibraryRequestRunner.showStatus(v, "校园钱包余额不足，未支付。请到商店的校园钱包充值，再回到此页刷新并确认支付。", VCampusTheme.DANGER);
                return;
            }
            if (!LibraryRequestRunner.isSuccessful(v, response)) return;
            LibraryRequestRunner.showStatus(v, item.getAmountCents() == 0 ? "零元赔偿已结清，正在同步借阅、馆藏和校园钱包。"
                    : "赔偿支付成功，正在同步借阅、馆藏和校园钱包。", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(() -> LibraryCirculationActions.refreshLibrary(v, v.manager));
        }, "支付结果暂未确认，请先刷新赔偿单和校园钱包核对；不会自动重扣，请勿连续重复支付。");
    }
}

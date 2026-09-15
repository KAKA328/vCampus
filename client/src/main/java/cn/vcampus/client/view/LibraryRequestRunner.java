package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import java.awt.Color;
import java.io.IOException;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** 统一后台请求生命周期、按钮状态和错误提示。 */
final class LibraryRequestRunner {
    /** 统一执行单个后台请求，管理延迟提示和按钮恢复，不自动重试写操作。 */
    static void runRequest(LibraryViewState v, String loadingMessage, final LibraryRequestRunner.LibraryRequest request,
            final LibraryRequestRunner.ResponseHandler responseHandler) {
        LibraryRequestRunner.runRequest(v, loadingMessage, request, responseHandler, "无法连接图书馆服务器，请确认服务器已启动");
    }

    /** 统一执行单个后台请求，管理延迟提示和按钮恢复，不自动重试写操作。 */
    static void runRequest(LibraryViewState v, String loadingMessage, final LibraryRequestRunner.LibraryRequest request,
            final LibraryRequestRunner.ResponseHandler responseHandler, final String failureMessage) {
        if (v.requestInProgress) return;
        v.catalogRefresh.foregroundStarted();
        final int requestId = v.requestLifecycle.begin();
        v.requestInProgress = true;
        LibraryRequestRunner.updateButtonState(v);
        final Timer loadingStatus = DelayedUiUpdate.once(() -> {
            if (v.requestLifecycle.isCurrent(requestId) && v.requestInProgress) {
                LibraryRequestRunner.showStatus(v, loadingMessage, VCampusTheme.MUTED);
            }
        });
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                try (RemoteLibraryService service = new RemoteLibraryService(v.host, v.port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    LibraryRequestRunner.showStatus(v, failureMessage, VCampusTheme.DANGER);
                } finally {
                    loadingStatus.stop();
                    if (v.requestLifecycle.isCurrent(requestId)) {
                        v.requestInProgress = false;
                        LibraryRequestRunner.updateButtonState(v);
                        SwingUtilities.invokeLater(() -> {
                            if (v.compensationRefreshQueued && v.panel.isShowing()
                                    && v.workspaceTabs.getSelectedIndex() == 2) LibraryCirculationActions.loadCompensations(v);
                            v.catalogRefresh.drain();
                        });
                    }
                }
            }
        }.execute();
    }

    /** 判断业务响应是否成功，并显示服务器失败说明。 */
    static boolean isSuccessful(LibraryViewState v, Message response) {
        if (response == null) {
            LibraryRequestRunner.showStatus(v, "服务器响应暂不可用，请刷新重试", VCampusTheme.DANGER);
            return false;
        }
        if (response.getStatusCode() == StatusCode.OK) return true;
        if (response.getPayload() instanceof String) {
            LibraryRequestRunner.showStatus(v, (String) response.getPayload(), VCampusTheme.DANGER);
        } else {
            LibraryRequestRunner.showStatus(v, LibraryRowMapper.statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
        }
        return false;
    }

    /** 按真实角色、忙碌状态、账单归属和提醒状态更新操作按钮。 */
    static void updateButtonState(LibraryViewState v) {
        v.searchButton.setEnabled(!v.requestInProgress);
        v.detailButton.setEnabled(!v.requestInProgress);
        v.borrowButton.setEnabled(!v.manager && !v.requestInProgress);
        v.historyButton.setEnabled(!v.manager && !v.requestInProgress);
        v.returnButton.setEnabled(!v.requestInProgress);
        v.allHistoryButton.setEnabled(v.manager && !v.requestInProgress);
        v.addBookButton.setEnabled(v.manager && !v.requestInProgress);
        v.restockButton.setEnabled(v.manager && !v.requestInProgress);
        v.editBookButton.setEnabled(v.manager && !v.requestInProgress);
        v.copiesButton.setEnabled(v.manager && !v.requestInProgress);
        v.declareLossButton.setEnabled(v.manager && !v.requestInProgress);
        v.refreshCompensationsButton.setEnabled(!v.requestInProgress);
        LibraryCompensation selected = LibraryRequestRunner.selectedCompensation(v);
        v.payCompensationButton.setText(selected != null && selected.getAmountCents() == 0 ? "确认结清" : "确认支付");
        v.payCompensationButton.setEnabled(!v.manager && !v.requestInProgress && v.compensationsLoaded && selected != null
                && selected.getStatus() == CompensationStatus.PENDING
                && v.session.getUser().getUserId().equals(selected.getUserId()));
        v.rechargeHelpButton.setEnabled(!v.manager && !v.requestInProgress);
        v.handleReminderButton.setEnabled(!v.requestInProgress && !v.unreadReminders.isEmpty());
        v.acknowledgeReminderButton.setEnabled(!v.manager && !v.requestInProgress && !v.unreadReminders.isEmpty());
        v.resetSearchButton.setEnabled(!v.requestInProgress);
        v.keywordField.setEnabled(!v.requestInProgress);
        v.categoryField.setEnabled(!v.requestInProgress);
    }

    /** 根据当前选中行取得对应赔偿快照。 */
    static LibraryCompensation selectedCompensation(LibraryViewState v) {
        int row = v.compensationTable.getSelectedRow();
        if (row < 0 || row >= v.compensationTable.getRowCount()) return null;
        int modelRow = v.compensationTable.convertRowIndexToModel(row);
        return v.currentCompensations.get(String.valueOf(v.compensationModel.getValueAt(modelRow, 0)));
    }

    /** 更新可读操作提示及状态颜色。 */
    static void showStatus(LibraryViewState v, String message, Color color) {
        v.status.setText(message);
        VCampusTheme.statusPill(v.status, color);
    }

    /** 一次远程操作；写操作不得交给自动重试逻辑。 */
    interface LibraryRequest {
        /** 执行当前协作者封装的单次任务，保持既有调用顺序与事务边界。 */
        Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException;
    }

    /** 后台任务结束后，在 Swing 事件线程处理响应。 */
    interface ResponseHandler {
        /** 处理现有图书馆消息，校验载荷、会话及权限后执行业务。 */
        void handle(Message response);
    }
}

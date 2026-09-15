package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import java.awt.Dialog;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** 馆藏页的低优先级只读轮询；写操作绝不自动重试，所有状态只在EDT访问。 */
final class LibraryCatalogRefresh {
    /** 正常网络下可见馆藏页的轮询间隔。 */
    static final int INTERVAL_MS = 5000;
    /** 本页面状态，不能跨账号或窗口共享。 */
    private final LibraryViewState v;
    /** 仅在馆藏页实际显示时运行。 */
    final Timer timer;
    /** 每页最多一个后台馆藏查询；前台请求不受其阻塞。 */
    private SwingWorker<Message, Void> worker;
    /** 前台请求或页面生命周期变化会作废旧响应。 */
    private long generation;
    /** 忙碌期间的多次刷新意图合并成一次。 */
    private boolean pending;
    /** 已经成功展示过馆藏，允许刷新其已提交的条件。 */
    private boolean loaded;
    /** 当前馆藏页是否实际可见。 */
    private boolean active;
    /** 会话拒绝后暂停自动请求，直到手动查询成功。 */
    private boolean authorizationFailed;
    /** 上次成功展示的查询关键词，不读取尚未提交的输入草稿。 */
    private String keyword = "";
    /** 上次成功展示的查询分类。 */
    private String category = "";

    /** 创建计时器但不开始联网，由实际页面可见性管理。 */
    LibraryCatalogRefresh(LibraryViewState v) {
        this.v = v;
        timer = new Timer(INTERVAL_MS, event -> requestRefresh());
        timer.setCoalesce(true);
    }

    /** 显示馆藏页时恢复刷新；切换模块、页签或关闭窗口时停表并作废旧响应。 */
    void visibilityChanged() {
        boolean showing = v.panel.isShowing() && v.workspaceTabs.getSelectedIndex() == 0;
        if (showing == active) return;
        active = showing;
        generation++;
        if (!active) timer.stop();
        else if (!authorizationFailed) {
            timer.start();
            if (loaded) requestRefresh();
        }
    }

    /** 移除页面时立即停止轮询；已发出的查询完成后只能被丢弃。 */
    void stop() {
        active = false;
        generation++;
        timer.stop();
    }

    /** 任何前台请求都优先于旧的后台库存快照。 */
    void foregroundStarted() { generation++; }

    /** 借阅提交前登记一次结果核对，成功、冲突或网络异常后都只补查、不重借。 */
    void borrowStarted() { pending = true; }

    /** 手动或业务联动查询成功时记住真正展示的条件。 */
    void catalogShown() {
        keyword = v.keywordField.getText().trim();
        category = LibraryCatalogActions.selectedCategory(v);
        loaded = true;
        authorizationFailed = false;
        generation++;
        if (active) timer.start();
        showSynchronized();
    }

    /** 合并定时器与返回馆藏页的刷新请求，不影响当前表单或业务请求。 */
    void requestRefresh() {
        pending = true;
        drain();
    }

    /** 前台请求结束后调用；只有可见、空闲且没有弹出式交互时才发起只读查询。 */
    void drain() {
        if (!pending || !loaded || !active || authorizationFailed || worker != null
                || v.requestInProgress || !canUpdate()) return;
        pending = false;
        final long expected = generation;
        final String query = keyword, filter = category;
        worker = new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteLibraryService service = new RemoteLibraryService(v.host, v.port)) {
                    return service.search(v.session.getToken(), query, filter);
                }
            }

            @Override protected void done() {
                try {
                    Message response = get();
                    if (!mayApply(expected)) return;
                    if (!canUpdate()) { pending = true; return; }
                    if (LibraryViewData.showBooksQuietly(v, response)) showSynchronized();
                    else {
                        if (response != null && (response.getStatusCode() == StatusCode.UNAUTHORIZED
                                || response.getStatusCode() == StatusCode.FORBIDDEN)) {
                            authorizationFailed = true;
                            timer.stop();
                            v.catalogSyncStatus.setText("自动刷新已暂停：会话或权限已变化，请重新登录。");
                        } else showUnavailable();
                    }
                } catch (Exception failure) {
                    if (mayApply(expected)) showUnavailable();
                } finally {
                    worker = null;
                    if (pending) SwingUtilities.invokeLater(() -> drain());
                }
            }
        };
        worker.execute();
    }

    /** 写请求、手动查询及隐藏/重开页面后，旧响应不得覆盖更新后的列表。 */
    private boolean mayApply(long expected) {
        return active && expected == generation && !v.requestInProgress;
    }

    /** 选择多行、展开分类或使用模态表单时不重绘表格。 */
    private boolean canUpdate() {
        Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return !v.bookTable.getSelectionModel().getValueIsAdjusting() && !v.categoryField.isPopupVisible()
                && !(window instanceof Dialog && ((Dialog) window).isModal());
    }

    /** 独立展示同步时间，不覆盖借阅成功/失败等重要操作提示。 */
    private void showSynchronized() {
        v.catalogSyncStatus.setText("库存已同步 " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                + " · 本页每5秒自动刷新");
    }

    /** 网络或格式异常保留现有列表，等待下一次只读刷新。 */
    private void showUnavailable() {
        v.catalogSyncStatus.setText("库存刷新暂不可用，当前为旧数据；稍后自动重试，也可点击查询图书。");
    }
}

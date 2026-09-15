package cn.vcampus.client.view;

import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.user.Session;
import java.time.LocalDate;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** 图书馆页面入口：负责生命周期，布局、请求和业务交互由独立协作者承担。 */
public final class LibraryPanel extends JPanel {

    /** 本页面独立的视图状态。 */
    final LibraryViewState state;

    /**
     * 创建正式图书馆页面，实际身份由服务端返回的会话决定。
     * @param host 服务器地址
     * @param port 服务器端口
     * @param session 已认证会话
     */
    public LibraryPanel(String host, int port, Session session) {
        this(host, port, session, null);
    }

    /** 测试可注入独立提醒存储，避免读写真实用户配置。 */
    LibraryPanel(String host, int port, Session session, LibraryReminderState reminderState) {
        state = new LibraryViewState(this, host, port, session, reminderState);
        LibraryPageLayout.build(state);
    }

    /** 显示时启动本地日期检查，并且只安排一次首次查询。 */
    @Override
    public void addNotify() {
        super.addNotify();
        LibraryReminderController.refreshReminderDate(state, LocalDate.now());
        state.reminderDateTimer.start();
        if (!state.initialLoadStarted) {
            state.initialLoadStarted = true;
            SwingUtilities.invokeLater(() -> LibraryCirculationActions.loadInitialData(state));
        }
    }

    /** 页面退出时停止提醒计时器，不关闭其他客户端或服务端。 */
    @Override
    public void removeNotify() {
        state.reminderDateTimer.stop();
        state.catalogRefresh.stop();
        super.removeNotify();
    }

    /** 判断当前角色是否使用馆员界面，服务端仍独立校验权限。 */
    static boolean canManage(Role role) {
        return LibraryRowMapper.canManage(role);
    }

    /** 把馆藏快照转换为表格列值，数量和价格只作展示。 */
    static Object[] bookRow(Book book) {
        return LibraryRowMapper.bookRow(book);
    }

    /** 把以元表示的图书价格格式化为两位小数。 */
    static String formatPrice(double price) {
        return LibraryRowMapper.formatPrice(price);
    }

    /** 将借阅记录和当前查询的书名组合成一行。 */
    static Object[] historyRow(BorrowRecord record, String bookTitle) {
        return LibraryRowMapper.historyRow(record, bookTitle);
    }

    /** 把借阅状态代码转换为读者可见的中文文字。 */
    static String borrowStatusLabel(Object value) {
        return LibraryRowMapper.borrowStatusLabel(value);
    }

    /** 把赔偿单快照转换为赔偿表格列值。 */
    static Object[] compensationRow(LibraryCompensation item) {
        return LibraryRowMapper.compensationRow(item);
    }

    /** 把整数分金额转换为以元显示的两位小数文字。 */
    static String formatCents(long cents) {
        return LibraryRowMapper.formatCents(cents);
    }

    /** 生成支付前的书名、赔偿金额和账户余额确认说明。 */
    static String paymentConfirmationText(LibraryCompensation item, Long balance) {
        return LibraryRowMapper.paymentConfirmationText(item, balance);
    }

    /** 创建可用于滚动详情对话框的馆藏信息面板。 */
    static JPanel bookDetailPanel(Book book) {
        return LibraryBookDetails.bookDetailPanel(book);
    }

    /** 创建分类下拉选项；检索时可包含不限分类选项。 */
    static JComboBox<String> categoryChoices(boolean includeAll) {
        return LibraryWidgets.categoryChoices(includeAll);
    }

    /** 将有效馆藏响应投影到表格和数量概览。 */
    void showBooks(Message response) {
        LibraryViewData.showBooks(state, response);
    }

    /** 显示已授权范围的借阅记录，并重新计算到期提醒。 */
    boolean showHistory(Message response, String scope) {
        return LibraryViewData.showHistory(state, response, scope);
    }

    /** 显示赔偿记录并更新可操作的选中账单。 */
    boolean showCompensations(Message response) {
        return LibraryViewData.showCompensations(state, response);
    }

    /** 显示最近一次查询的本人钱包余额。 */
    boolean showWalletBalance(Message response) {
        return LibraryViewData.showWalletBalance(state, response);
    }

    /** 委托单次支付请求和结果刷新，不自动重试扣款。 */
    void submitCompensationPayment(final LibraryCompensation item) {
        LibraryCompensationActions.submitCompensationPayment(state, item);
    }

    /** 按本机日期刷新今日已读状态和到期提醒。 */
    void refreshReminderDate(LocalDate today) {
        LibraryReminderController.refreshReminderDate(state, today);
    }
}

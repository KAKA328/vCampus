package cn.vcampus.client.view;

import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;

/** 图书馆页面的独立视图状态；仅在 Swing 事件线程上更新控件。 */
final class LibraryViewState {
    /** 拥有这些控件的图书馆页面。 */
    final LibraryPanel panel;
    /** 借阅数据模型中的书名列位置。 */
    static final int HISTORY_TITLE_COLUMN = 4;
    /** 借阅数据模型中的状态列位置。 */
    static final int HISTORY_STATUS_COLUMN = 8;
    /** 服务器地址。 */
    final String host;
    /** 服务器端口。 */
    final int port;
    /** 服务端验证后返回的登录会话。 */
    final Session session;
    /** 是否使用馆员管理界面。 */
    final boolean manager;

    /** 馆藏表格的数据模型。 */
    final BatchTableModel bookModel = new BatchTableModel(new Object[] {
            "图书号", "书名", "作者", "参考价格", "分类", "ISBN", "出版社", "总量", "可借", "位置"
    });
    /** 借阅历史表格的数据模型。 */
    final BatchTableModel historyModel = new BatchTableModel(new Object[] {
            "记录号", "批次号", "用户", "图书号", "图书名称", "借阅日", "应还日", "归还日", "状态", "实体册编号", "书名来源"
    });
    /** 馆藏查询表格。 */
    final JTable bookTable = new JTable(bookModel);
    /** 借阅流通记录表格。 */
    final JTable historyTable = new JTable(historyModel);
    /** 赔偿账单的数据模型。 */
    final BatchTableModel compensationModel = new BatchTableModel(new Object[] {
            "赔偿单号", "图书名称", "图书号", "借阅人", "原价赔偿金额", "状态", "确认遗失时间", "支付时间"
    });
    /** 赔偿账单表格。 */
    final JTable compensationTable = new JTable(compensationModel);
    /** 按账单编号索引的当前赔偿记录。 */
    final Map<String, LibraryCompensation> currentCompensations = new HashMap<String, LibraryCompensation>();
    /** 忙碌期间是否收到待执行的赔偿刷新请求。 */
    boolean compensationRefreshQueued;
    /** 用于历史列表显示的当前馆藏书名缓存。 */
    final Map<String, String> bookTitles = new HashMap<String, String>();
    /** 关键词输入框。 */
    final JTextField keywordField = new JTextField(16);
    /** 分类下拉框。 */
    final JComboBox<String> categoryField = LibraryWidgets.categoryChoices(true);
    /** 业务状态或操作提示。 */
    final JLabel status = new LibraryWrappingLabel("输入查询条件，或直接点击“查询图书”查看全部馆藏");
    /** 查询图书按钮。 */
    final JButton searchButton = new JButton("查询图书");
    /** 查看详情按钮。 */
    final JButton detailButton = new JButton("查看详情");
    /** 批量借阅按钮。 */
    final JButton borrowButton = new JButton("借阅选中图书");
    /** 查询本人借阅按钮。 */
    final JButton historyButton = new JButton("我的借阅记录");
    /** 查询全部借阅按钮。 */
    final JButton allHistoryButton = new JButton("全部借阅记录");
    /** 归还选中记录按钮。 */
    final JButton returnButton = new JButton("归还选中记录");
    /** 管理员确认遗失按钮。 */
    final JButton declareLossButton = new JButton("确认遗失");
    /** 刷新赔偿和余额按钮。 */
    final JButton refreshCompensationsButton = new JButton("刷新赔偿与余额");
    /** 本人确认赔偿支付按钮。 */
    final JButton payCompensationButton = new JButton("确认支付");
    /** 查看钱包充值说明按钮。 */
    final JButton rechargeHelpButton = new JButton("如何充值");
    /** 当前校园钱包余额说明。 */
    final JLabel walletBalanceLabel = new LibraryWrappingLabel("校园钱包余额：等待查询");
    /** 最近成功查询的钱包余额，单位为分。 */
    Long currentWalletBalance;
    /** 赔偿记录是否完成有效加载。 */
    boolean compensationsLoaded;
    /** 新增馆藏按钮。 */
    final JButton addBookButton = new JButton("新增图书");
    /** 补充已有图书库存按钮。 */
    final JButton restockButton = new JButton("增加库存");
    /** 编辑已有馆藏的资料，不修改库存。 */
    final JButton editBookButton = new JButton("编辑资料");
    /** 查看实体册编号和流转状态。 */
    final JButton copiesButton = new JButton("实体副本");
    /** 定位待归还记录按钮。 */
    final JButton handleReminderButton = new JButton("去归还");
    /** 将本机提醒标记为今日已读的按钮。 */
    final JButton acknowledgeReminderButton = new JButton("今日已读");
    /** 清空筛选按钮。 */
    final JButton resetSearchButton = new JButton("清空筛选");
    /** 当前查询的馆藏种类数量。 */
    final JLabel catalogCountValue = LibraryWidgets.metricValue();
    /** 当前查询的可借册数合计。 */
    final JLabel availableCountValue = LibraryWidgets.metricValue();
    /** 独立的库存同步提示，不覆盖业务操作结果。 */
    final JLabel catalogSyncStatus = new LibraryWrappingLabel("本页库存每5秒自动刷新；借阅后立即核对余量。");
    /** 本页面的只读馆藏轮询与迟到响应控制。 */
    final LibraryCatalogRefresh catalogRefresh;
    /** 当前查看范围内的在借数量。 */
    final JLabel activeLoanCountValue = LibraryWidgets.metricValue();
    /** 临期及逾期提醒内容。 */
    final JLabel dueReminder = new LibraryWrappingLabel("正在检查借阅期限…");
    /** 到期提醒区域。 */
    final JPanel reminderCard = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
    /** 按本机服务器与用户隔离的今日已读状态。 */
    final LibraryReminderState reminderState;
    /** 馆藏、流通和赔偿工作区。 */
    final JTabbedPane workspaceTabs = new JTabbedPane();
    /** 当前借阅记录集合。 */
    List<BorrowRecord> currentRecords = new ArrayList<BorrowRecord>();
    /** 尚未标记今日已读的提醒记录。 */
    List<BorrowRecord> unreadReminders = new ArrayList<BorrowRecord>();
    /** 借阅记录是否已成功加载。 */
    boolean historyLoaded;
    /** 上次计算提醒的日期。 */
    LocalDate reminderDate = LocalDate.now();
    /** 跨午夜刷新本机提醒的计时器。 */
    final Timer reminderDateTimer = new Timer(60000,
            event -> LibraryReminderController.refreshReminderDate(this, LocalDate.now()));

    /** 是否已有一个后台请求正在进行。 */
    boolean requestInProgress;
    /** 是否已安排首次数据加载。 */
    boolean initialLoadStarted;
    /** 后台请求编号与延迟提示的生命周期。 */
    final RequestLifecycle requestLifecycle = new RequestLifecycle();


    /** 每个页面拥有独立控件与会话状态，不保存任何全局登录身份。 */
    LibraryViewState(LibraryPanel panel, String host, int port, Session session, LibraryReminderState reminders) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.panel = panel;
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.manager = LibraryRowMapper.canManage(session.getUser().getRole());
        this.reminderState = reminders == null
                ? LibraryReminderState.forReader(this.host, port, session.getUser().getUserId()) : reminders;
        this.catalogRefresh = new LibraryCatalogRefresh(this);
    }
}

package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.library.CompensationStatus;
import cn.vcampus.library.LibraryCompensation;
import cn.vcampus.user.Session;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.Toolkit;
import java.io.IOException;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;

/** 图书馆页面：目录查询、批量借阅、归还、借阅记录和馆藏维护。 */
public final class LibraryPanel extends JPanel {
    private static final int HISTORY_TITLE_COLUMN = 4;
    private static final int HISTORY_STATUS_COLUMN = 8;
    private final String host;
    private final int port;
    private final Session session;
    private final boolean manager;

    private final BatchTableModel bookModel = new BatchTableModel(new Object[] {
            "图书号", "书名", "作者", "参考价格", "分类", "ISBN", "出版社", "总量", "可借", "位置"
    });
    private final BatchTableModel historyModel = new BatchTableModel(new Object[] {
            "记录号", "批次号", "用户", "图书号", "图书名称", "借阅日", "应还日", "归还日", "状态"
    });
    private final JTable bookTable = new JTable(bookModel);
    private final JTable historyTable = new JTable(historyModel);
    private final BatchTableModel compensationModel = new BatchTableModel(new Object[] {
            "赔偿单号", "图书名称", "图书号", "借阅人", "原价赔偿金额", "状态", "确认遗失时间", "支付时间"
    });
    private final JTable compensationTable = new JTable(compensationModel);
    private final Map<String, LibraryCompensation> currentCompensations = new HashMap<String, LibraryCompensation>();
    private boolean compensationRefreshQueued;
    private final Map<String, String> bookTitles = new HashMap<String, String>();
    private final JTextField keywordField = new JTextField(16);
    private final JComboBox<String> categoryField = categoryChoices(true);
    private final JLabel status = new LibraryWrappingLabel("输入查询条件，或直接点击“查询图书”查看全部馆藏");
    private final JButton searchButton = new JButton("查询图书");
    private final JButton detailButton = new JButton("查看详情");
    private final JButton borrowButton = new JButton("借阅选中图书");
    private final JButton historyButton = new JButton("我的借阅记录");
    private final JButton allHistoryButton = new JButton("全部借阅记录");
    private final JButton returnButton = new JButton("归还选中记录");
    private final JButton declareLossButton = new JButton("确认遗失");
    private final JButton refreshCompensationsButton = new JButton("刷新赔偿与余额");
    private final JButton payCompensationButton = new JButton("确认支付");
    private final JButton rechargeHelpButton = new JButton("如何充值");
    private final JLabel walletBalanceLabel = new LibraryWrappingLabel("校园钱包余额：等待查询");
    private Long currentWalletBalance;
    private boolean compensationsLoaded;
    private final JButton addBookButton = new JButton("新增图书");
    private final JButton restockButton = new JButton("增加库存");
    private final JButton handleReminderButton = new JButton("去归还");
    private final JButton acknowledgeReminderButton = new JButton("今日已读");
    private final JButton resetSearchButton = new JButton("清空筛选");
    private final JLabel catalogCountValue = metricValue();
    private final JLabel availableCountValue = metricValue();
    private final JLabel activeLoanCountValue = metricValue();
    private final JLabel dueReminder = new LibraryWrappingLabel("正在检查借阅期限…");
    private final JPanel reminderCard = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
    private final LibraryReminderState reminderState;
    private final JTabbedPane workspaceTabs = new JTabbedPane();
    private List<BorrowRecord> currentRecords = new ArrayList<BorrowRecord>();
    private List<BorrowRecord> unreadReminders = new ArrayList<BorrowRecord>();
    private boolean historyLoaded;
    private LocalDate reminderDate = LocalDate.now();
    private final Timer reminderDateTimer = new Timer(60000,
            event -> refreshReminderDate(LocalDate.now()));

    private boolean requestInProgress;
    private boolean initialLoadStarted;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public LibraryPanel(String host, int port, Session session) {
        this(host, port, session, null);
    }

    LibraryPanel(String host, int port, Session session, LibraryReminderState reminderState) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.manager = canManage(session.getUser().getRole());
        this.reminderState = reminderState == null
                ? LibraryReminderState.forReader(this.host, port, session.getUser().getUserId()) : reminderState;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, UiMetrics.px(16)));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);
        add(statusPanel(), BorderLayout.SOUTH);

        searchButton.addActionListener(event -> loadBooks());
        detailButton.addActionListener(event -> loadSelectedDetail());
        borrowButton.addActionListener(event -> borrowSelected());
        historyButton.addActionListener(event -> loadOwnHistory());
        allHistoryButton.addActionListener(event -> loadAllHistory());
        returnButton.addActionListener(event -> returnSelected());
        declareLossButton.addActionListener(event -> declareSelectedLoss());
        refreshCompensationsButton.addActionListener(event -> refreshLibrary(manager));
        payCompensationButton.addActionListener(event -> paySelectedCompensation());
        rechargeHelpButton.addActionListener(event -> JOptionPane.showMessageDialog(this,
                "请在主界面切换到商店，进入“钱包”页并完成充值，再回到此页点击“刷新赔偿与余额”。\n图书馆与商店使用同一登录账号的校园钱包。",
                "校园钱包充值", JOptionPane.INFORMATION_MESSAGE));
        addBookButton.addActionListener(event -> showAddBookDialog());
        restockButton.addActionListener(event -> showRestockDialog());
        handleReminderButton.addActionListener(event -> goToReminderRecord());
        acknowledgeReminderButton.addActionListener(event -> acknowledgeReminders());
        resetSearchButton.addActionListener(event -> resetSearch());
        keywordField.addActionListener(event -> loadBooks());
        compensationTable.getSelectionModel().addListSelectionListener(event -> updateButtonState());
        workspaceTabs.addChangeListener(event -> {
            // 仅在实际展示后进入赔偿页时查询，不干扰初始借阅加载或纯布局测试。
            if (isShowing() && workspaceTabs.getSelectedIndex() == 2) {
                loadCompensations();
            }
        });
        configureAccessibility();
        updateButtonState();
    }

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
        panel.setOpaque(false);
        panel.add(overviewPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = tabs();
        tabs.setMinimumSize(UiMetrics.dimension(0, 340));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshReminderDate(LocalDate.now());
        reminderDateTimer.start();
        if (!initialLoadStarted) {
            initialLoadStarted = true;
            SwingUtilities.invokeLater(this::loadInitialData);
        }
    }

    private JPanel overviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        panel.setOpaque(false);
        panel.add(summaryCards(), BorderLayout.NORTH);
        panel.add(reminderPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel reminderPanel() {
        JPanel panel = reminderCard;
        panel.setName("libraryReminderCard");
        VCampusTheme.panel(panel);
        JLabel heading = new JLabel(manager ? "全校流通提醒" : "我的到期提醒");
        heading.setFont(VCampusTheme.font(Font.BOLD, 13));
        heading.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(dueReminder, VCampusTheme.MUTED);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(dueReminder, BorderLayout.CENTER);
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(8), UiMetrics.px(4)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(handleReminderButton);
        VCampusTheme.secondaryButton(acknowledgeReminderButton);
        handleReminderButton.setText(manager ? "去处理归还" : "去归还");
        actions.add(handleReminderButton);
        if (!manager) actions.add(acknowledgeReminderButton);
        acknowledgeReminderButton.setToolTipText("收起本机今日已读提醒，不改变借阅状态；次日或出现新提醒时重新提示");
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel statusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        VCampusTheme.panel(statusPanel);
        JLabel label = new JLabel("操作状态");
        label.setFont(VCampusTheme.font(Font.BOLD, 13));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(status, VCampusTheme.MUTED);
        statusPanel.add(label, BorderLayout.NORTH);
        statusPanel.add(status, BorderLayout.CENTER);
        return statusPanel;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(8)));
        panel.setOpaque(false);
        JPanel text = sectionHeading(manager ? "图书管理工作台" : "图书馆",
                manager ? "维护馆藏与全校借阅流转记录" : "查找馆藏、办理借阅并管理个人借阅记录");
        String capabilities = manager
                ? "管理员 · " + session.getUser().getDisplayName()
                : "读者 · " + session.getUser().getDisplayName();
        JLabel role = new LibraryWrappingLabel(capabilities);
        VCampusTheme.statusPill(role, manager ? VCampusTheme.PRIMARY : VCampusTheme.SUCCESS);
        panel.add(text, BorderLayout.CENTER);
        panel.add(role, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel summaryCards() {
        ResponsiveCardRowPanel row = new ResponsiveCardRowPanel(UiMetrics.px(210), UiMetrics.px(12));
        row.add(metricCard(manager ? "馆藏种类" : "检索结果", catalogCountValue,
                "本次查询返回的图书种类", VCampusTheme.PRIMARY));
        row.add(metricCard("可借册数", availableCountValue,
                "当前结果中的可借总量", VCampusTheme.SUCCESS));
        row.add(metricCard("借阅中", activeLoanCountValue,
                manager ? "当前查看范围内未归还" : "本人尚未归还的记录", VCampusTheme.ACCENT));
        return row;
    }

    private static JPanel metricCard(String title, JLabel value, String hint, Color accent) {
        JPanel card = new JPanel(new BorderLayout(UiMetrics.px(12), 0));
        VCampusTheme.panel(card);
        card.setMinimumSize(UiMetrics.dimension(160, 112));

        JPanel rail = new JPanel();
        rail.setBackground(accent);
        rail.setPreferredSize(UiMetrics.dimension(4, 0));

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(VCampusTheme.font(Font.BOLD, 13));
        titleLabel.setForeground(VCampusTheme.MUTED);
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setFont(VCampusTheme.font(Font.PLAIN, 12));
        hintLabel.setForeground(VCampusTheme.MUTED);
        copy.add(titleLabel);
        copy.add(Box.createVerticalStrut(UiMetrics.px(4)));
        copy.add(value);
        copy.add(Box.createVerticalStrut(UiMetrics.px(3)));
        copy.add(hintLabel);

        card.add(rail, BorderLayout.WEST);
        card.add(copy, BorderLayout.CENTER);
        return card;
    }

    private static JLabel metricValue() {
        JLabel label = new JLabel("—");
        label.setFont(VCampusTheme.font(Font.BOLD, 22));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    private JTabbedPane tabs() {
        JTabbedPane tabs = workspaceTabs;
        VCampusTheme.tabs(tabs);
        tabs.addTab(manager ? "馆藏管理" : "找书借阅", catalogPanel());
        tabs.addTab(manager ? "流通管理" : "我的借阅", historyPanel());
        tabs.addTab("遗失赔偿", compensationPanel());
        tabs.setToolTipTextAt(0, manager ? "查询馆藏并录入新书" : "查询馆藏并选择一本或多本图书借阅");
        tabs.setToolTipTextAt(1, manager ? "查看全部借阅流水并办理归还" : "查看本人记录并归还图书");
        tabs.setToolTipTextAt(2, manager ? "查看全校遗失赔偿单，不能代扣读者余额" : "核对原价赔偿单并使用校园钱包支付");
        tabs.getAccessibleContext().setAccessibleName(manager ? "图书管理功能" : "读者图书馆功能");
        return tabs;
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);

        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(sectionHeading(manager ? "馆藏维护" : "馆藏检索",
                manager ? "按书名、作者、ISBN 或分类定位馆藏，也可录入新书。"
                        : "支持书名、作者、ISBN 等关键词搜索；可多选后一次借阅。"), BorderLayout.NORTH);
        JPanel search = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        search.setName("librarySearchActions");
        search.setOpaque(false);
        VCampusTheme.field(keywordField);
        VCampusTheme.field(categoryField);
        keywordField.setFont(VCampusTheme.font(Font.PLAIN, 14));
        categoryField.setFont(VCampusTheme.font(Font.PLAIN, 14));
        search.add(searchField("关键词", keywordField));
        search.add(searchField("分类", categoryField));
        VCampusTheme.secondaryButton(searchButton);
        VCampusTheme.secondaryButton(resetSearchButton);
        search.add(searchButton);
        search.add(resetSearchButton);
        top.add(search, BorderLayout.CENTER);

        configureBookTable();
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(detailButton);
        actions.add(detailButton);
        if (manager) {
            VCampusTheme.primaryButton(addBookButton);
            actions.add(addBookButton);
            VCampusTheme.secondaryButton(restockButton);
            actions.add(restockButton);
        } else {
            VCampusTheme.primaryButton(borrowButton);
            actions.add(borrowButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(bookTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel historyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);
        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(sectionHeading(manager ? "借阅流通记录" : "我的借阅记录",
                manager ? "查看全校借阅流水，选择未归还记录可代为办理归还。"
                        : "查看本人借阅历史，选择借阅中的记录即可归还。"), BorderLayout.NORTH);
        configureHistoryTable();
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(historyButton);
        VCampusTheme.primaryButton(returnButton);
        if (manager) {
            VCampusTheme.secondaryButton(allHistoryButton);
            actions.add(allHistoryButton);
        } else {
            actions.add(historyButton);
        }
        actions.add(returnButton);
        if (manager) {
            VCampusTheme.secondaryButton(declareLossButton);
            actions.add(declareLossButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(historyTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel compensationPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(12)));
        VCampusTheme.panel(panel);
        JPanel top = new JPanel(new BorderLayout(0, UiMetrics.px(10)));
        top.setOpaque(false);
        top.add(sectionHeading(manager ? "全校遗失赔偿" : "我的遗失赔偿",
                manager ? "在流通管理中确认遗失，按服务器馆藏原价生成赔偿单；由借阅人本人确认支付，管理员不能代扣。"
                        : "核对馆员确认的遗失记录和原价金额后，再使用与商店共用的校园钱包支付。"), BorderLayout.NORTH);
        if (!manager) {
            walletBalanceLabel.setName("libraryWalletBalance");
            VCampusTheme.statusPill(walletBalanceLabel, VCampusTheme.MUTED);
            top.add(walletBalanceLabel, BorderLayout.CENTER);
        }
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(10), UiMetrics.px(6)));
        actions.setOpaque(false);
        if (manager) refreshCompensationsButton.setText("刷新赔偿记录");
        VCampusTheme.secondaryButton(refreshCompensationsButton);
        actions.add(refreshCompensationsButton);
        if (!manager) {
            VCampusTheme.primaryButton(payCompensationButton);
            VCampusTheme.secondaryButton(rechargeHelpButton);
            actions.add(payCompensationButton);
            actions.add(rechargeHelpButton);
        }
        top.add(actions, BorderLayout.SOUTH);
        configureTable(compensationTable, ListSelectionModel.SINGLE_SELECTION);
        compensationTable.setName("libraryCompensationTable");
        compensationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        applyColumnWidths(compensationTable, new int[] {135, 190, 88, 110, 120, 88, 160, 160});
        compensationTable.getColumnModel().getColumn(5).setCellRenderer(new CompensationStatusRenderer());
        compensationTable.moveColumn(1, 0);
        // Keep the book, amount and payment state visible together on a narrow window.
        compensationTable.moveColumn(4, 1);
        compensationTable.moveColumn(5, 2);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(compensationTable), BorderLayout.CENTER);
        return panel;
    }

    private void configureBookTable() {
        configureTable(bookTable, manager
                ? ListSelectionModel.SINGLE_SELECTION
                : ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        bookTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {88, 180, 120, 92, 100, 135, 130, 64, 64, 100};
        applyColumnWidths(bookTable, widths);
    }

    private void configureHistoryTable() {
        configureTable(historyTable, ListSelectionModel.SINGLE_SELECTION);
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {110, 110, 100, 88, 220, 100, 100, 100, 140};
        applyColumnWidths(historyTable, widths);
        historyTable.getColumnModel().getColumn(HISTORY_STATUS_COLUMN).setCellRenderer(new BorrowStatusRenderer());
        // 书名放在最前面方便阅读；底层记录号仍保持第 0 列，归还继续使用记录号。
        historyTable.moveColumn(HISTORY_TITLE_COLUMN, 0);
    }

    private static void configureTable(JTable table, int selectionMode) {
        VCampusTheme.table(table);
        table.setPreferredScrollableViewportSize(UiMetrics.dimension(0, 260));
        table.setSelectionMode(selectionMode);
        table.setAutoCreateRowSorter(true);
    }

    private static void applyColumnWidths(JTable table, int[] widths) {
        for (int column = 0; column < widths.length && column < table.getColumnCount(); column++) {
            int width = UiMetrics.px(widths[column]);
            table.getColumnModel().getColumn(column).setMinWidth(width);
            table.getColumnModel().getColumn(column).setPreferredWidth(width);
        }
    }

    private static JPanel sectionHeading(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, UiMetrics.px(3)));
        panel.setOpaque(false);
        JLabel titleLabel = new LibraryWrappingLabel(title);
        titleLabel.setFont(VCampusTheme.font(Font.BOLD, 18));
        titleLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitleLabel = new LibraryWrappingLabel(subtitle);
        subtitleLabel.setFont(VCampusTheme.font(Font.PLAIN, 13));
        subtitleLabel.setForeground(VCampusTheme.MUTED);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(subtitleLabel, BorderLayout.SOUTH);
        return panel;
    }

    private static JLabel fieldLabel(String text, Component target) {
        JLabel label = new JLabel(text);
        label.setFont(VCampusTheme.font(Font.BOLD, 13));
        label.setForeground(VCampusTheme.TEXT);
        label.setLabelFor(target);
        return label;
    }

    private static JPanel searchField(String label, JComponent field) {
        JPanel group = new JPanel(new BorderLayout(UiMetrics.px(8), 0));
        group.setOpaque(false);
        group.add(fieldLabel(label, field), BorderLayout.WEST);
        group.add(field, BorderLayout.CENTER);
        return group;
    }

    private void configureAccessibility() {
        keywordField.getAccessibleContext().setAccessibleName("图书关键词");
        keywordField.getAccessibleContext().setAccessibleDescription("输入书名、作者、ISBN 或图书编号");
        keywordField.setToolTipText("可输入书名、作者、ISBN 或图书编号");
        categoryField.getAccessibleContext().setAccessibleName("图书分类");
        categoryField.getAccessibleContext().setAccessibleDescription("选择文学、科幻等分类，全部分类表示不限制分类");
        categoryField.setToolTipText("选择图书分类后点击查询图书");
        bookTable.getAccessibleContext().setAccessibleName(manager ? "馆藏管理列表" : "可借图书列表");
        bookTable.getAccessibleContext().setAccessibleDescription(
                manager ? "选择一行可查看图书详情" : "可选择一行或多行后批量借阅");
        historyTable.getAccessibleContext().setAccessibleName(manager ? "全部借阅记录" : "我的借阅记录");
        historyTable.getAccessibleContext().setAccessibleDescription("选择借阅中的记录可办理归还");
        compensationTable.getAccessibleContext().setAccessibleName(manager ? "全部遗失赔偿单" : "我的遗失赔偿单");
        compensationTable.getAccessibleContext().setAccessibleDescription(manager ? "只可查看，不能代扣余额" : "选择待支付记录，核对金额后确认支付");
        dueReminder.getAccessibleContext().setAccessibleName(manager ? "全校借阅到期提醒" : "个人借阅到期提醒");
        JButton[] actions = {searchButton, resetSearchButton, detailButton, borrowButton,
                historyButton, allHistoryButton, returnButton, addBookButton, restockButton,
                handleReminderButton, acknowledgeReminderButton, declareLossButton,
                refreshCompensationsButton, payCompensationButton, rechargeHelpButton};
        for (JButton action : actions) {
            action.setFocusable(true);
            action.setRequestFocusEnabled(true);
            action.setFocusPainted(true);
        }
    }

    private void resetSearch() {
        keywordField.setText("");
        categoryField.setSelectedIndex(0);
        showStatus("筛选条件已清空，可重新查询全部馆藏", VCampusTheme.MUTED);
        keywordField.requestFocusInWindow();
    }

    private void loadBooks() {
        final String keyword = keywordField.getText().trim();
        final String category = selectedCategory();
        runRequest("正在查询馆藏…", service -> service.search(session.getToken(), keyword, category),
                this::showBooks);
    }

    private void loadInitialData() {
        refreshCatalogAndHistory();
    }

    private void refreshCatalogAndHistory() {
        final String keyword = keywordField.getText().trim();
        final String category = selectedCategory();
        runRequest("正在刷新馆藏与借阅期限…", service -> service.search(session.getToken(), keyword, category), response -> {
            showBooks(response);
            SwingUtilities.invokeLater(() -> loadHistory(manager));
        });
    }

    private void loadCompensations() {
        if (requestInProgress) {
            compensationRefreshQueued = true;
            return;
        }
        compensationRefreshQueued = false;
        if (requestInProgress) return;
        prepareCompensationRefresh();
        final CompensationRequest request = new CompensationRequest();
        runRequest("正在查询赔偿记录与校园钱包…", request, ignored -> {
            boolean recordsOk = showCompensations(request.compensations);
            boolean walletOk = manager || showWalletBalance(request.wallet);
            showStatus(recordsOk && walletOk ? "赔偿记录已更新" + (manager ? "。" : "，校园钱包余额已刷新。")
                    : "赔偿记录或余额暂不可用，请刷新重试。", recordsOk && walletOk ? VCampusTheme.SUCCESS : VCampusTheme.ACCENT);
        });
    }

    /** 一次后台任务完整刷新关联视图，不并行抢占请求状态或遗漏赔偿/余额刷新。 */
    private void refreshLibrary(boolean all) {
        if (requestInProgress) return;
        prepareCompensationRefresh();
        final RefreshRequest request = new RefreshRequest(all, keywordField.getText().trim(), selectedCategory());
        runRequest("正在刷新馆藏、借阅、赔偿与校园钱包…", request, ignored -> {
            List<String> unavailable = new ArrayList<String>();
            if (!cacheBookTitles(request.completeCatalog, true)) unavailable.add("完整图书名称");
            if (isListOf(request.catalog, Book.class)) showBooks(request.catalog);
            else unavailable.add("馆藏");
            if (!showHistory(request.history, all ? "全部借阅记录" : "我的借阅记录")) unavailable.add("借阅记录");
            if (!showCompensations(request.compensations)) unavailable.add("赔偿记录（暂不可支付）");
            if (!manager && !showWalletBalance(request.wallet)) unavailable.add("校园钱包余额");
            if (unavailable.isEmpty()) {
                showStatus("馆藏、借阅及赔偿记录已同步" + (manager ? "。" : "，校园钱包余额已刷新。"), VCampusTheme.SUCCESS);
            } else {
                showStatus("部分查询暂不可用：" + String.join("、", unavailable)
                        + "。其余结果已更新，请刷新重试；旧记录仅供参考。", VCampusTheme.ACCENT);
            }
        });
    }

    private void loadSelectedDetail() {
        final String bookId = selectedBookId();
        if (bookId == null) {
            showStatus("请先选择一本图书", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在查询图书详情…", service -> service.detail(session.getToken(), bookId), response -> {
            if (!isSuccessful(response) || !(response.getPayload() instanceof Book)) {
                if (response.getStatusCode() == StatusCode.OK) {
                    showStatus("服务器返回的图书详情格式不正确", VCampusTheme.DANGER);
                }
                return;
            }
            Book book = (Book) response.getPayload();
            showScrollableDialog(bookDetailPanel(book), "图书详情", JOptionPane.DEFAULT_OPTION);
            showStatus("已加载《" + book.getTitle() + "》的详情", VCampusTheme.SUCCESS);
        });
    }

    private void borrowSelected() {
        final List<String> bookIds = selectedBookIds();
        if (bookIds.isEmpty()) {
            showStatus("请先选择一本或多本图书", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在提交借阅请求…", service -> service.borrow(session.getToken(), bookIds), response -> {
            if (!isSuccessful(response)) return;
            int count = response.getPayload() instanceof List<?> ? ((List<?>) response.getPayload()).size() : bookIds.size();
            showStatus("借阅成功，共 " + count + " 本；正在刷新馆藏与到期提醒…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::refreshCatalogAndHistory);
        });
    }

    private void loadOwnHistory() {
        loadHistory(false);
    }

    private void loadAllHistory() {
        loadHistory(true);
    }

    private void loadHistory(boolean all) {
        final HistoryRequest request = new HistoryRequest(all);
        runRequest("正在查询借阅记录和图书名称…", request, response -> {
            boolean titlesLoaded = cacheBookTitles(request.catalog, true);
            if (showHistory(response, all ? "全部借阅记录" : "我的借阅记录") && !titlesLoaded) {
                showStatus("借阅记录已加载，部分图书名称暂不可用；仍可按记录办理归还。", VCampusTheme.ACCENT);
            }
        });
    }

    private void returnSelected() {
        int selected = historyTable.getSelectedRow();
        if (selected < 0) {
            showStatus("请先选择一条借阅记录", VCampusTheme.DANGER);
            return;
        }
        int modelRow = historyTable.convertRowIndexToModel(selected);
        final String recordId = String.valueOf(historyModel.getValueAt(modelRow, 0));
        String recordStatus = String.valueOf(historyModel.getValueAt(modelRow, HISTORY_STATUS_COLUMN));
        if (!BorrowStatus.BORROWED.name().equals(recordStatus)) {
            showStatus("只有借阅中的记录可以归还；已归还、已遗失或已赔偿的记录不能重复办理归还。", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在办理归还…", service -> service.returnBook(session.getToken(), recordId), response -> {
            if (!isSuccessful(response)) return;
            showStatus("归还成功，正在刷新馆藏与到期提醒…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::refreshCatalogAndHistory);
        });
    }

    private void showAddBookDialog() {
        final JTextField id = new JTextField();
        final JTextField title = new JTextField();
        final JTextField author = new JTextField();
        final JTextField isbn = new JTextField();
        final JComboBox<String> category = categoryChoices(false);
        for (int index = 1; index < categoryField.getItemCount(); index++) {
            addCategoryChoice(category, categoryField.getItemAt(index));
        }
        final JTextField publisher = new JTextField();
        final JTextField price = new JTextField();
        final JTextField copies = new JTextField("1");
        final JTextField location = new JTextField();
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addField(form, "图书号*", id);
        addField(form, "书名*", title);
        addField(form, "作者*", author);
        addField(form, "ISBN", isbn);
        addField(form, "分类*", category);
        addField(form, "出版社", publisher);
        addField(form, "参考价格（元）*", price);
        addField(form, "初始册数*", copies);
        addField(form, "馆藏位置", location);
        JPanel dialog = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
        dialog.setBackground(VCampusTheme.PANEL);
        dialog.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        dialog.add(sectionHeading("录入馆藏", "带 * 的字段为必填项；新书初始可借册数与总册数一致。"),
                BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        while (showScrollableDialog(dialog, "新增图书", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            try {
                int total = Integer.parseInt(copies.getText().trim());
                double referencePrice = Double.parseDouble(price.getText().trim());
                if (total <= 0 || !Double.isFinite(referencePrice) || referencePrice <= 0.0d) {
                    throw new IllegalArgumentException("price and copies must be positive");
                }
                final Book book = new Book(id.getText(), title.getText(), author.getText(), isbn.getText(),
                        String.valueOf(category.getSelectedItem()), publisher.getText(), referencePrice,
                        total, total, location.getText());
                runRequest("正在新增图书…", service -> service.addBook(session.getToken(), book), response -> {
                    if (!isSuccessful(response)) return;
                    showStatus("新增图书成功，正在刷新馆藏…", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadBooks);
                });
                return;
            } catch (NumberFormatException invalidNumber) {
                JOptionPane.showMessageDialog(this, "参考价格必须是数字，初始册数必须是正整数",
                        "请检查输入", JOptionPane.WARNING_MESSAGE);
            } catch (IllegalArgumentException invalidBook) {
                JOptionPane.showMessageDialog(this, "请填写图书号、书名、作者，并检查价格和册数",
                        "请检查输入", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    @Override
    public void removeNotify() {
        reminderDateTimer.stop();
        super.removeNotify();
    }

    private static void addField(JPanel form, String label, JComponent field) {
        VCampusTheme.field(field);
        field.setFont(VCampusTheme.font(Font.PLAIN, 14));
        field.getAccessibleContext().setAccessibleName(label.replace("*", ""));
        addFormRow(form, fieldLabel(label, field), field);
    }

    static JPanel bookDetailPanel(Book book) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(18)));
        panel.setName("libraryBookDetails");
        panel.setBackground(VCampusTheme.PANEL);
        panel.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        panel.add(sectionHeading(book.getTitle(), book.getAuthor() + " · " + book.getCategory()),
                BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setName("libraryDetailFields");
        fields.setOpaque(false);
        addDetailRow(fields, "图书编号", book.getBookId());
        addDetailRow(fields, "ISBN", book.getIsbn());
        addDetailRow(fields, "出版社", book.getPublisher());
        addDetailRow(fields, "参考价格", formatPrice(book.getPrice()));
        addDetailRow(fields, "馆藏位置", book.getLocation());
        addDetailRow(fields, "总册数", String.valueOf(book.getTotalCopies()));
        addDetailRow(fields, "当前可借", String.valueOf(book.getAvailableCopies()));
        panel.add(fields, BorderLayout.CENTER);

        JLabel availability = new LibraryWrappingLabel(book.getAvailableCopies() > 0
                ? "当前可借，可在馆藏列表选择后办理借阅"
                : "当前已无可借库存，请稍后再查询");
        VCampusTheme.statusPill(availability,
                book.getAvailableCopies() > 0 ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
        panel.add(availability, BorderLayout.SOUTH);
        return panel;
    }

    private static void addDetailRow(JPanel panel, String label, String value) {
        JLabel key = new JLabel(label);
        key.setFont(VCampusTheme.font(Font.BOLD, 13));
        key.setForeground(VCampusTheme.MUTED);
        JLabel content = new LibraryWrappingLabel(value == null || value.trim().isEmpty() ? "—" : value);
        content.setFont(VCampusTheme.font(Font.PLAIN, 13));
        content.setForeground(VCampusTheme.TEXT);
        addFormRow(panel, key, content);
    }

    /** 每行根据内容计算高度，标签列不参与等宽分割，长值可以正常换行。 */
    private static void addFormRow(JPanel panel, JLabel key, JComponent content) {
        int row = panel.getComponentCount() / 2;
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.gridy = row;
        label.anchor = GridBagConstraints.NORTHWEST;
        label.insets = UiMetrics.insets(8, 0, 8, 18);
        panel.add(key, label);
        GridBagConstraints value = new GridBagConstraints();
        value.gridx = 1;
        value.gridy = row;
        value.weightx = 1.0d;
        value.fill = GridBagConstraints.HORIZONTAL;
        value.anchor = GridBagConstraints.NORTHWEST;
        value.insets = UiMetrics.insets(8, 0, 8, 0);
        panel.add(content, value);
    }

    /** 详情与表单使用可缩放窗口和滚动容器，不以固定高度压缩内容。 */
    private int showScrollableDialog(JPanel content, String title, int options) {
        JScrollPane scroller = VCampusTheme.pageScroll(content);
        scroller.setPreferredSize(UiMetrics.dimension(520, 480));
        JOptionPane pane = new JOptionPane(scroller, JOptionPane.PLAIN_MESSAGE, options);
        JDialog dialog = pane.createDialog(this, title);
        dialog.setResizable(true);
        Rectangle screen = dialog.getGraphicsConfiguration().getBounds();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(dialog.getGraphicsConfiguration());
        int availableWidth = Math.max(1, screen.width - screenInsets.left - screenInsets.right - UiMetrics.px(24));
        int availableHeight = Math.max(1, screen.height - screenInsets.top - screenInsets.bottom - UiMetrics.px(24));
        Dimension packed = dialog.getSize();
        dialog.setMinimumSize(new Dimension(Math.min(UiMetrics.px(360), availableWidth),
                Math.min(UiMetrics.px(300), availableHeight)));
        dialog.setSize(Math.min(packed.width, availableWidth), Math.min(packed.height, availableHeight));
        dialog.setLocationRelativeTo(this);
        try {
            dialog.setVisible(true);
            Object selected = pane.getValue();
            return selected instanceof Integer ? ((Integer) selected).intValue() : JOptionPane.CLOSED_OPTION;
        } finally {
            dialog.dispose();
        }
    }

    static JComboBox<String> categoryChoices(boolean includeAll) {
        JComboBox<String> choices = new JComboBox<String>();
        if (includeAll) choices.addItem("全部分类");
        for (String category : new String[] {"文学", "科幻", "计算机", "历史", "教材", "哲学", "艺术", "经济", "自然科学"}) {
            choices.addItem(category);
        }
        choices.setEditable(false);
        choices.setPrototypeDisplayValue("自然科学分类");
        return choices;
    }

    private static void addCategoryChoice(JComboBox<String> choices, String category) {
        if (category == null || category.trim().isEmpty()) return;
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (choices.getItemAt(index).equals(category.trim())) return;
        }
        choices.addItem(category.trim());
    }

    private String selectedCategory() {
        return categoryField.getSelectedIndex() == 0 ? "" : String.valueOf(categoryField.getSelectedItem());
    }

    private void showRestockDialog() {
        final String bookId = selectedBookId();
        if (bookId == null) {
            showStatus("请先选择需要补充库存的图书", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在读取当前库存…", service -> service.detail(session.getToken(), bookId), response -> {
            if (!isSuccessful(response) || !(response.getPayload() instanceof Book)) return;
            Book book = (Book) response.getPayload();
            JTextField copies = new JTextField("1", 10);
            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            addField(form, "新增册数*", copies);
            JPanel content = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(14)));
            content.add(sectionHeading("为《" + book.getTitle() + "》增加库存",
                    "图书号 " + bookId + "；当前总量 " + book.getTotalCopies() + " 册，可借 "
                            + book.getAvailableCopies() + " 册。只填写本次增加数量，不是新的总量；借出记录不变。"), BorderLayout.NORTH);
            content.add(form, BorderLayout.CENTER);
            while (showScrollableDialog(content, "增加库存", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                final int count;
                try {
                    count = Integer.parseInt(copies.getText().trim());
                    if (count <= 0) throw new NumberFormatException();
                } catch (NumberFormatException invalid) {
                    JOptionPane.showMessageDialog(this, "新增册数必须是大于 0 的整数", "请检查输入", JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                SwingUtilities.invokeLater(() -> runRequest("正在增加库存…", service -> service.restock(session.getToken(), bookId, count), result -> {
                    if (!isSuccessful(result)) return;
                    showStatus("库存已增加 " + count + " 册，正在刷新馆藏…", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadBooks);
                }, "未能确认增加库存的结果，请先查询该书核对总量，再决定是否重试，避免重复补充。"));
                return;
            }
        });
    }

    private void prepareCompensationRefresh() {
        compensationsLoaded = false;
        currentWalletBalance = null;
        if (!manager) walletBalanceLabel.setText("校园钱包余额：等待刷新结果（与商店共用）。");
        updateButtonState();
    }

    private void declareSelectedLoss() {
        if (!manager || requestInProgress) return;
        int selected = historyTable.getSelectedRow();
        if (selected < 0) {
            showStatus("请先在流通管理中选择一条借阅中的记录", VCampusTheme.DANGER);
            return;
        }
        int row = historyTable.convertRowIndexToModel(selected);
        if (!BorrowStatus.BORROWED.name().equals(String.valueOf(historyModel.getValueAt(row, HISTORY_STATUS_COLUMN)))) {
            showStatus("只能为借阅中的记录确认遗失，已有赔偿单请到“遗失赔偿”查看。", VCampusTheme.DANGER);
            return;
        }
        final String recordId = String.valueOf(historyModel.getValueAt(row, 0));
        final String userId = String.valueOf(historyModel.getValueAt(row, 2));
        final String bookId = String.valueOf(historyModel.getValueAt(row, 3));
        runRequest("正在读取服务器馆藏原价…", service -> service.detail(session.getToken(), bookId), response -> {
            if (!isSuccessful(response) || !(response.getPayload() instanceof Book)) return;
            Book book = (Book) response.getPayload();
            JPanel confirmation = confirmationPanel("确认遗失并按原价赔偿",
                    "借阅人：" + userId + "\n图书：《" + book.getTitle() + "》（" + bookId + "）\n借阅记录：" + recordId
                            + "\n服务器当前原价：" + formatPrice(book.getPrice())
                            + "\n此金额仅供核对，最终赔偿金额由服务器按确认时原价生成并锁定。"
                            + "\n确认后该记录转为遗失，不再办理普通归还；由借阅人本人确认支付，管理员不会直接扣款。");
            if (showScrollableDialog(confirmation, "确认遗失", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
            SwingUtilities.invokeLater(() -> runRequest("正在确认遗失并生成赔偿单…",
                    service -> service.declareLoss(session.getToken(), recordId), result -> {
                        if (!isSuccessful(result)) return;
                        workspaceTabs.setSelectedIndex(2);
                        showStatus("遗失已确认，赔偿单已生成，等待借阅人本人支付。", VCampusTheme.SUCCESS);
                        SwingUtilities.invokeLater(() -> refreshLibrary(manager));
                    }, "尚未确认遗失请求结果，请先刷新借阅和赔偿记录核对；本次不会自动重新提交。"));
        });
    }

    private void paySelectedCompensation() {
        if (manager || !compensationsLoaded || requestInProgress) return;
        int selected = compensationTable.getSelectedRow();
        if (selected < 0) {
            showStatus("请先选择一条待支付的赔偿单", VCampusTheme.DANGER);
            return;
        }
        int row = compensationTable.convertRowIndexToModel(selected);
        String id = String.valueOf(compensationModel.getValueAt(row, 0));
        final LibraryCompensation item = currentCompensations.get(id);
        if (item == null || item.getStatus() != CompensationStatus.PENDING
                || !session.getUser().getUserId().equals(item.getUserId())) {
            showStatus("只能支付本人待支付的赔偿单；已赔偿记录无需重复支付。", VCampusTheme.DANGER);
            return;
        }
        if (showScrollableDialog(confirmationPanel(item.getAmountCents() == 0 ? "确认零元赔偿结清" : "确认原价赔偿支付",
                paymentConfirmationText(item, currentWalletBalance)),
                item.getAmountCents() == 0 ? "确认结清" : "确认支付", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        submitCompensationPayment(item);
    }

    /** 只在用户完成确认后调用；包内可测试传输失败及重复按键而无需模拟系统对话框。 */
    void submitCompensationPayment(final LibraryCompensation item) {
        if (manager || requestInProgress || !compensationsLoaded || item == null
                || item.getStatus() != CompensationStatus.PENDING
                || !session.getUser().getUserId().equals(item.getUserId())) return;
        // 结果未知时必须先读回账单状态，不能靠旧列表连续重提写操作。
        compensationsLoaded = false;
        runRequest("正在使用校园钱包支付赔偿…", service -> service.payCompensation(session.getToken(), item.getCompensationId()), response -> {
            if (response != null && response.getStatusCode() == StatusCode.PAYMENT_REQUIRED) {
                currentWalletBalance = null;
                walletBalanceLabel.setText("校园钱包余额需刷新；余额不足，请到商店 → 钱包充值。");
                showStatus("校园钱包余额不足，未支付。请到商店的校园钱包充值，再回到此页刷新并确认支付。", VCampusTheme.DANGER);
                return;
            }
            if (!isSuccessful(response)) return;
            showStatus(item.getAmountCents() == 0 ? "零元赔偿已结清，正在同步借阅、馆藏和校园钱包。"
                    : "赔偿支付成功，正在同步借阅、馆藏和校园钱包。", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(() -> refreshLibrary(manager));
        }, "支付结果暂未确认，请先刷新赔偿单和校园钱包核对；不会自动重扣，请勿连续重复支付。");
    }

    private static JPanel confirmationPanel(String title, String text) {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, UiMetrics.px(12)));
        panel.setOpaque(false);
        panel.add(sectionHeading(title, text), BorderLayout.NORTH);
        return panel;
    }

    static String paymentConfirmationText(LibraryCompensation item, Long balance) {
        return "图书：《" + item.getBookTitle() + "》（" + item.getBookId() + "）\n赔偿单：" + item.getCompensationId()
                + "\n原价赔偿金额：" + formatCents(item.getAmountCents())
                + (item.getAmountCents() == 0 ? "\n此单金额为零，确认后结清，不扣除钱包余额。"
                    : "\n将从本人校园钱包扣款（与商店共用同一余额）。")
                + "\n上次查询余额：" + (balance == null ? "暂不可用，以服务器校验为准" : formatCents(balance.longValue()))
                + (item.getAmountCents() == 0 ? "\n确认结清后将同步借阅与赔偿状态。"
                    : "\n确认后提交支付；余额不足不会扣款，可先去商店的校园钱包充值。");
    }

    boolean showCompensations(Message response) {
        compensationsLoaded = false;
        if (!isListOf(response, LibraryCompensation.class)) {
            updateButtonState();
            return false;
        }
        List<Object[]> rows = new ArrayList<Object[]>();
        Map<String, LibraryCompensation> items = new HashMap<String, LibraryCompensation>();
        for (Object value : (List<?>) response.getPayload()) {
            LibraryCompensation item = (LibraryCompensation) value;
            // 服务端必须限制范围；客户端也不展示意外混入的其他读者账单。
            if (!manager && !session.getUser().getUserId().equals(item.getUserId())) continue;
            rows.add(compensationRow(item));
            items.put(item.getCompensationId(), item);
        }
        currentCompensations.clear();
        currentCompensations.putAll(items);
        compensationModel.replaceRows(rows);
        compensationsLoaded = true;
        updateButtonState();
        return true;
    }

    boolean showWalletBalance(Message response) {
        currentWalletBalance = null;
        if (manager || response == null || response.getStatusCode() != StatusCode.OK
                || !(response.getPayload() instanceof Long) || ((Long) response.getPayload()).longValue() < 0) {
            walletBalanceLabel.setText("校园钱包余额：暂不可用，请刷新（与商店共用）。");
            return false;
        }
        currentWalletBalance = (Long) response.getPayload();
        walletBalanceLabel.setText("校园钱包余额：" + formatCents(currentWalletBalance.longValue()) + "（与商店共用）");
        return true;
    }

    static Object[] compensationRow(LibraryCompensation item) {
        return new Object[] {item.getCompensationId(), item.getBookTitle(), item.getBookId(), item.getUserId(),
                formatCents(item.getAmountCents()), item.getStatus().name(),
                StoreRowMapper.formatDateTime(item.getCreatedAt()), StoreRowMapper.formatDateTime(item.getPaidAt())};
    }

    static String formatCents(long cents) {
        return "￥" + BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static boolean isListOf(Message response, Class<?> itemType) {
        if (response == null || response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) return false;
        for (Object item : (List<?>) response.getPayload()) if (!itemType.isInstance(item)) return false;
        return true;
    }

    void showBooks(Message response) {
        if (!isSuccessful(response)) return;
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> books = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        int availableCopies = 0;
        for (Object item : books) {
            if (!(item instanceof Book)) {
                showStatus("服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            Book book = (Book) item;
            addCategoryChoice(categoryField, book.getCategory());
            rows.add(bookRow(book));
            availableCopies += book.getAvailableCopies();
        }
        bookModel.replaceRows(rows);
        cacheBookTitles(response, false);
        catalogCountValue.setText(books.size() + " 种");
        availableCountValue.setText(availableCopies + " 册");
        showStatus("已显示符合条件的图书，共 " + books.size() + " 种", VCampusTheme.SUCCESS);
    }

    boolean showHistory(Message response, String scope) {
        if (!isSuccessful(response)) return false;
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
            return false;
        }
        List<?> records = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        List<BorrowRecord> borrowingRecords = new ArrayList<BorrowRecord>();
        int activeLoans = 0;
        for (Object item : records) {
            if (!(item instanceof BorrowRecord)) {
                showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
                return false;
            }
            BorrowRecord record = (BorrowRecord) item;
            borrowingRecords.add(record);
            rows.add(historyRow(record, bookTitles.get(record.getBookId())));
            if (record.getStatus() == BorrowStatus.BORROWED) {
                activeLoans++;
            }
        }
        historyModel.replaceRows(rows);
        activeLoanCountValue.setText(activeLoans + " 条");
        currentRecords = borrowingRecords;
        historyLoaded = true;
        updateDueReminder(borrowingRecords);
        showStatus("已显示" + scope + "，共 " + records.size() + " 条", VCampusTheme.SUCCESS);
        return true;
    }

    private boolean cacheBookTitles(Message response, boolean completeCatalog) {
        if (response == null || response.getStatusCode() != StatusCode.OK
                || !(response.getPayload() instanceof List<?>)) return false;
        Map<String, String> names = new HashMap<String, String>();
        for (Object item : (List<?>) response.getPayload()) {
            if (!(item instanceof Book)) return false;
            Book book = (Book) item;
            names.put(book.getBookId(), book.getTitle());
        }
        if (completeCatalog) bookTitles.clear();
        bookTitles.putAll(names);
        return true;
    }

    private void updateDueReminder(List<BorrowRecord> records) {
        updateDueReminder(records, LocalDate.now());
    }

    private void updateDueReminder(List<BorrowRecord> records, LocalDate today) {
        reminderDate = today;
        unreadReminders = manager ? new ArrayList<BorrowRecord>() : reminderState.unread(records, today);
        if (manager) {
            for (BorrowRecord record : records) {
                if (record.getStatus() == BorrowStatus.BORROWED
                        && !record.getDueDate().isAfter(today.plusDays(LibraryDueReminder.WARNING_DAYS))) {
                    unreadReminders.add(record);
                }
            }
        }
        LibraryDueReminder.Summary summary = LibraryDueReminder.summarize(unreadReminders, today);
        dueReminder.setText(LibraryDueReminder.message(summary, manager));
        Color color = summary.getOverdueCount() > 0 ? VCampusTheme.DANGER
                : summary.getDueSoonCount() > 0 ? VCampusTheme.ACCENT : VCampusTheme.SUCCESS;
        VCampusTheme.statusPill(dueReminder, color);
        reminderCard.setVisible(!unreadReminders.isEmpty());
        updateButtonState();
        revalidate();
        repaint();
    }

    /** 窗口跨午夜保持打开时，也按新日期恢复提醒；不额外发送网络请求。 */
    void refreshReminderDate(LocalDate today) {
        if (historyLoaded && !today.equals(reminderDate)) updateDueReminder(currentRecords, today);
    }

    private void acknowledgeReminders() {
        if (manager || unreadReminders.isEmpty()) return;
        reminderState.acknowledge(unreadReminders, LocalDate.now());
        updateDueReminder(currentRecords);
        showStatus("已收起今日已读提醒，图书仍需归还；次日或出现新提醒时会再次提示。", VCampusTheme.SUCCESS);
    }

    private void goToReminderRecord() {
        workspaceTabs.setSelectedIndex(1);
        BorrowRecord nearest = null;
        for (BorrowRecord record : unreadReminders) {
            if (nearest == null || record.getDueDate().isBefore(nearest.getDueDate())) nearest = record;
        }
        if (nearest == null) return;
        for (int row = 0; row < historyModel.getRowCount(); row++) {
            if (nearest.getRecordId().equals(historyModel.getValueAt(row, 0))) {
                int visibleRow = historyTable.convertRowIndexToView(row);
                historyTable.setRowSelectionInterval(visibleRow, visibleRow);
                historyTable.scrollRectToVisible(historyTable.getCellRect(visibleRow, 0, true));
                historyTable.requestFocusInWindow();
                showStatus("已定位最早到期的借阅记录，核对后点击“归还选中记录”办理归还。", VCampusTheme.MUTED);
                return;
            }
        }
    }

    private String selectedBookId() {
        int selected = bookTable.getSelectedRow();
        if (selected < 0) return null;
        return String.valueOf(bookModel.getValueAt(bookTable.convertRowIndexToModel(selected), 0));
    }

    private List<String> selectedBookIds() {
        List<String> ids = new ArrayList<String>();
        for (int selected : bookTable.getSelectedRows()) {
            ids.add(String.valueOf(bookModel.getValueAt(bookTable.convertRowIndexToModel(selected), 0)));
        }
        return ids;
    }

    private void runRequest(String loadingMessage, final LibraryRequest request,
            final ResponseHandler responseHandler) {
        runRequest(loadingMessage, request, responseHandler, "无法连接图书馆服务器，请确认服务器已启动");
    }

    private void runRequest(String loadingMessage, final LibraryRequest request,
            final ResponseHandler responseHandler, final String failureMessage) {
        if (requestInProgress) return;
        final int requestId = requestLifecycle.begin();
        requestInProgress = true;
        updateButtonState();
        final Timer loadingStatus = DelayedUiUpdate.once(() -> {
            if (requestLifecycle.isCurrent(requestId) && requestInProgress) {
                showStatus(loadingMessage, VCampusTheme.MUTED);
            }
        });
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                try (RemoteLibraryService service = new RemoteLibraryService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus(failureMessage, VCampusTheme.DANGER);
                } finally {
                    loadingStatus.stop();
                    if (requestLifecycle.isCurrent(requestId)) {
                        requestInProgress = false;
                        updateButtonState();
                        SwingUtilities.invokeLater(() -> {
                            if (compensationRefreshQueued && isShowing()
                                    && workspaceTabs.getSelectedIndex() == 2) loadCompensations();
                        });
                    }
                }
            }
        }.execute();
    }

    private boolean isSuccessful(Message response) {
        if (response == null) {
            showStatus("服务器响应暂不可用，请刷新重试", VCampusTheme.DANGER);
            return false;
        }
        if (response.getStatusCode() == StatusCode.OK) return true;
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
        } else {
            showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
        }
        return false;
    }

    private void updateButtonState() {
        searchButton.setEnabled(!requestInProgress);
        detailButton.setEnabled(!requestInProgress);
        borrowButton.setEnabled(!manager && !requestInProgress);
        historyButton.setEnabled(!manager && !requestInProgress);
        returnButton.setEnabled(!requestInProgress);
        allHistoryButton.setEnabled(manager && !requestInProgress);
        addBookButton.setEnabled(manager && !requestInProgress);
        restockButton.setEnabled(manager && !requestInProgress);
        declareLossButton.setEnabled(manager && !requestInProgress);
        refreshCompensationsButton.setEnabled(!requestInProgress);
        LibraryCompensation selected = selectedCompensation();
        payCompensationButton.setText(selected != null && selected.getAmountCents() == 0 ? "确认结清" : "确认支付");
        payCompensationButton.setEnabled(!manager && !requestInProgress && compensationsLoaded && selected != null
                && selected.getStatus() == CompensationStatus.PENDING
                && session.getUser().getUserId().equals(selected.getUserId()));
        rechargeHelpButton.setEnabled(!manager && !requestInProgress);
        handleReminderButton.setEnabled(!requestInProgress && !unreadReminders.isEmpty());
        acknowledgeReminderButton.setEnabled(!manager && !requestInProgress && !unreadReminders.isEmpty());
        resetSearchButton.setEnabled(!requestInProgress);
        keywordField.setEnabled(!requestInProgress);
        categoryField.setEnabled(!requestInProgress);
    }

    private LibraryCompensation selectedCompensation() {
        int row = compensationTable.getSelectedRow();
        if (row < 0 || row >= compensationTable.getRowCount()) return null;
        int modelRow = compensationTable.convertRowIndexToModel(row);
        return currentCompensations.get(String.valueOf(compensationModel.getValueAt(modelRow, 0)));
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        VCampusTheme.statusPill(status, color);
    }

    static boolean canManage(Role role) {
        return role == Role.ADMIN || role == Role.LIBRARIAN;
    }

    static Object[] bookRow(Book book) {
        return new Object[] {book.getBookId(), book.getTitle(), book.getAuthor(), formatPrice(book.getPrice()),
                book.getCategory(), book.getIsbn(), book.getPublisher(),
                Integer.valueOf(book.getTotalCopies()), Integer.valueOf(book.getAvailableCopies()),
                book.getLocation()};
    }

    static String formatPrice(double price) {
        return String.format(Locale.CHINA, "￥%.2f", Double.valueOf(price));
    }

    static Object[] historyRow(BorrowRecord record, String bookTitle) {
        return new Object[] {record.getRecordId(), record.getOrderId(), record.getUserId(), record.getBookId(),
                bookTitle == null || bookTitle.trim().isEmpty() ? "书名暂不可用" : bookTitle,
                record.getBorrowDate(), record.getDueDate(), record.getReturnDate() == null ? "" : record.getReturnDate(),
                record.getStatus().name()};
    }

    private static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST) return "请求数据不正确，或所选图书库存不足";
        if (statusCode == StatusCode.UNAUTHORIZED) return "登录状态已失效，请重新登录";
        if (statusCode == StatusCode.FORBIDDEN) return "当前账号没有执行该图书馆操作的权限";
        if (statusCode == StatusCode.NOT_FOUND) return "图书或借阅记录不存在";
        if (statusCode == StatusCode.CONFLICT) return "图书或借阅记录状态已发生变化，请刷新后重试";
        return "服务器处理图书馆请求失败";
    }

    static String borrowStatusLabel(Object value) {
        if (BorrowStatus.BORROWED.name().equals(String.valueOf(value))) return "借阅中";
        if (BorrowStatus.RETURNED.name().equals(String.valueOf(value))) return "已归还";
        if (BorrowStatus.LOST.name().equals(String.valueOf(value))) return "已遗失·待赔偿";
        if (BorrowStatus.COMPENSATED.name().equals(String.valueOf(value))) return "已赔偿";
        return String.valueOf(value);
    }

    static final class CompensationStatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            boolean paid = CompensationStatus.PAID.name().equals(String.valueOf(value));
            setText(paid ? "已结清" : "待支付");
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!selected) {
                Color color = paid ? VCampusTheme.SUCCESS : VCampusTheme.ACCENT;
                setForeground(color);
                setBackground(VCampusTheme.tintOf(color, 12));
            }
            return this;
        }
    }

    /** Keeps the technical enum in the model while presenting a readable, non-color-only status. */
    static final class BorrowStatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value,
                boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            String label = borrowStatusLabel(value);
            setText(label);
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(VCampusTheme.font(Font.BOLD, 12));
            setBorder(VCampusTheme.padding(5, 8, 5, 8));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if ("借阅中".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 12));
                setForeground(VCampusTheme.PRIMARY);
            } else if ("已归还".equals(label) || "已赔偿".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.SUCCESS, 12));
                setForeground(VCampusTheme.SUCCESS);
            } else if ("已遗失·待赔偿".equals(label)) {
                setBackground(VCampusTheme.tintOf(VCampusTheme.ACCENT, 12));
                setForeground(VCampusTheme.ACCENT);
            } else {
                setBackground(row % 2 == 0 ? VCampusTheme.PANEL : VCampusTheme.TABLE_STRIPE);
                setForeground(VCampusTheme.TEXT);
            }
            return this;
        }
    }

    private interface LibraryRequest {
        Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException;
    }

    /** 部分只读查询失败仍保留其他已得到的结果；绝不用于写操作或自动重试。 */
    private static Message readSafely(RemoteLibraryService service, LibraryRequest read) {
        try {
            return read.execute(service);
        } catch (IOException | ClassNotFoundException unavailable) {
            return null;
        }
    }

    private final class CompensationRequest implements LibraryRequest {
        private Message compensations;
        private Message wallet;

        @Override public Message execute(RemoteLibraryService service) {
            compensations = readSafely(service, remote -> remote.compensations(session.getToken(), manager));
            if (!manager) wallet = readSafely(service, remote -> remote.walletBalance(session.getToken()));
            return compensations;
        }
    }

    /** 赔偿后依次同步所有关联视图，避免多个独立后台任务相互覆盖 busy 状态。 */
    private final class RefreshRequest implements LibraryRequest {
        private final boolean all;
        private final String keyword;
        private final String category;
        private Message history;
        private Message completeCatalog;
        private Message catalog;
        private Message compensations;
        private Message wallet;

        RefreshRequest(boolean all, String keyword, String category) {
            this.all = all;
            this.keyword = keyword;
            this.category = category;
        }

        @Override public Message execute(RemoteLibraryService service) {
            history = readSafely(service, remote -> all ? remote.allHistory(session.getToken()) : remote.ownHistory(session.getToken()));
            completeCatalog = readSafely(service, remote -> remote.search(session.getToken(), "", ""));
            catalog = keyword.isEmpty() && category.isEmpty() ? completeCatalog
                    : readSafely(service, remote -> remote.search(session.getToken(), keyword, category));
            compensations = readSafely(service, remote -> remote.compensations(session.getToken(), manager));
            if (!manager) wallet = readSafely(service, remote -> remote.walletBalance(session.getToken()));
            return history;
        }
    }

    /** 两个已有协议请求在同一后台任务内完成；目录异常不丢弃已查到的借阅记录。 */
    private final class HistoryRequest implements LibraryRequest {
        private final boolean all;
        private Message catalog;

        HistoryRequest(boolean all) { this.all = all; }

        @Override public Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException {
            Message history = all ? service.allHistory(session.getToken()) : service.ownHistory(session.getToken());
            if (history.getStatusCode() == StatusCode.OK) {
                try {
                    catalog = service.search(session.getToken(), "", "");
                } catch (IOException | ClassNotFoundException unavailable) {
                    catalog = null;
                }
            }
            return history;
        }
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}

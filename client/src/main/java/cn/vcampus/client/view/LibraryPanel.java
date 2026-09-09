package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.library.BorrowRecord;
import cn.vcampus.library.BorrowStatus;
import cn.vcampus.user.Session;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.BorderFactory;
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
    private final String host;
    private final int port;
    private final Session session;
    private final boolean manager;

    private final BatchTableModel bookModel = new BatchTableModel(new Object[] {
            "图书号", "书名", "作者", "参考价格", "分类", "ISBN", "出版社", "总量", "可借", "位置"
    });
    private final BatchTableModel historyModel = new BatchTableModel(new Object[] {
            "记录号", "批次号", "用户", "图书号", "借阅日", "应还日", "归还日", "状态"
    });
    private final JTable bookTable = new JTable(bookModel);
    private final JTable historyTable = new JTable(historyModel);
    private final JTextField keywordField = new JTextField(16);
    private final JTextField categoryField = new JTextField(10);
    private final JLabel status = new JLabel("输入查询条件，或直接点击“查询图书”查看全部馆藏");
    private final JButton searchButton = new JButton("查询图书");
    private final JButton detailButton = new JButton("查看详情");
    private final JButton borrowButton = new JButton("借阅选中图书");
    private final JButton historyButton = new JButton("我的借阅记录");
    private final JButton allHistoryButton = new JButton("全部借阅记录");
    private final JButton returnButton = new JButton("归还选中记录");
    private final JButton addBookButton = new JButton("新增图书");
    private final JButton resetSearchButton = new JButton("清空筛选");
    private final JLabel catalogCountValue = metricValue();
    private final JLabel availableCountValue = metricValue();
    private final JLabel activeLoanCountValue = metricValue();
    private final JLabel dueReminder = new JLabel("正在检查借阅期限…");

    private boolean requestInProgress;
    private boolean initialLoadStarted;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public LibraryPanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.manager = canManage(session.getUser().getRole());
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 16));
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
        addBookButton.addActionListener(event -> showAddBookDialog());
        resetSearchButton.addActionListener(event -> resetSearch());
        keywordField.addActionListener(event -> loadBooks());
        categoryField.addActionListener(event -> loadBooks());
        configureAccessibility();
        updateButtonState();
    }

    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, 14));
        panel.setOpaque(false);
        panel.add(overviewPanel(), BorderLayout.NORTH);

        JTabbedPane tabs = tabs();
        tabs.setPreferredSize(UiMetrics.dimension(0, 500));
        tabs.setMinimumSize(UiMetrics.dimension(0, 340));
        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!initialLoadStarted) {
            initialLoadStarted = true;
            SwingUtilities.invokeLater(this::loadInitialData);
        }
    }

    private JPanel overviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.add(summaryCards(), BorderLayout.NORTH);
        panel.add(reminderPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel reminderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        VCampusTheme.panel(panel);
        JLabel heading = new JLabel(manager ? "全校流通提醒" : "我的到期提醒");
        heading.setFont(VCampusTheme.font(Font.BOLD, 13));
        heading.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(dueReminder, VCampusTheme.MUTED);
        panel.add(heading, BorderLayout.WEST);
        panel.add(dueReminder, BorderLayout.CENTER);
        return panel;
    }

    private JPanel statusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout(14, 0));
        VCampusTheme.panel(statusPanel);
        JLabel label = new JLabel("操作状态");
        label.setFont(VCampusTheme.font(Font.BOLD, 13));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        VCampusTheme.statusPill(status, VCampusTheme.MUTED);
        statusPanel.add(label, BorderLayout.WEST);
        statusPanel.add(status, BorderLayout.CENTER);
        return statusPanel;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(18, 0));
        panel.setOpaque(false);
        JPanel text = sectionHeading(manager ? "图书管理工作台" : "图书馆",
                manager ? "维护馆藏与全校借阅流转记录" : "查找馆藏、办理借阅并管理个人借阅记录");
        String capabilities = manager
                ? "管理员 · " + session.getUser().getDisplayName()
                : "读者 · " + session.getUser().getDisplayName();
        JLabel role = new JLabel(capabilities);
        VCampusTheme.statusPill(role, manager ? VCampusTheme.PRIMARY : VCampusTheme.SUCCESS);
        panel.add(text, BorderLayout.CENTER);
        panel.add(role, BorderLayout.EAST);
        return panel;
    }

    private JPanel summaryCards() {
        ResponsiveCardRowPanel row = new ResponsiveCardRowPanel(UiMetrics.px(170), UiMetrics.px(12));
        row.add(metricCard(manager ? "馆藏种类" : "检索结果", catalogCountValue,
                "本次查询返回的图书种类", VCampusTheme.PRIMARY));
        row.add(metricCard("可借册数", availableCountValue,
                "当前结果中的可借总量", VCampusTheme.SUCCESS));
        row.add(metricCard("借阅中", activeLoanCountValue,
                manager ? "当前查看范围内未归还" : "本人尚未归还的记录", VCampusTheme.ACCENT));
        return row;
    }

    private static JPanel metricCard(String title, JLabel value, String hint, Color accent) {
        JPanel card = new JPanel(new BorderLayout(12, 0));
        VCampusTheme.panel(card);
        card.setPreferredSize(UiMetrics.dimension(210, 92));
        card.setMinimumSize(UiMetrics.dimension(160, 92));

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
        copy.add(Box.createVerticalStrut(4));
        copy.add(value);
        copy.add(Box.createVerticalStrut(3));
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
        JTabbedPane tabs = new JTabbedPane();
        VCampusTheme.tabs(tabs);
        tabs.addTab(manager ? "馆藏管理" : "找书借阅", catalogPanel());
        tabs.addTab(manager ? "流通管理" : "我的借阅", historyPanel());
        tabs.setToolTipTextAt(0, manager ? "查询馆藏并录入新书" : "查询馆藏并选择一本或多本图书借阅");
        tabs.setToolTipTextAt(1, manager ? "查看全部借阅流水并办理归还" : "查看本人记录并归还图书");
        tabs.getAccessibleContext().setAccessibleName(manager ? "图书管理功能" : "读者图书馆功能");
        return tabs;
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(sectionHeading(manager ? "馆藏维护" : "馆藏检索",
                manager ? "按书名、作者、ISBN 或分类定位馆藏，也可录入新书。"
                        : "支持书名、作者、ISBN 等关键词搜索；可多选后一次借阅。"), BorderLayout.NORTH);
        JPanel search = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 6));
        search.setOpaque(false);
        VCampusTheme.field(keywordField);
        VCampusTheme.field(categoryField);
        search.add(fieldLabel("关键词", keywordField));
        search.add(keywordField);
        search.add(fieldLabel("分类", categoryField));
        search.add(categoryField);
        VCampusTheme.primaryButton(searchButton);
        VCampusTheme.secondaryButton(resetSearchButton);
        search.add(searchButton);
        search.add(resetSearchButton);
        top.add(search, BorderLayout.CENTER);

        configureBookTable();
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 6));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(detailButton);
        actions.add(detailButton);
        if (manager) {
            VCampusTheme.primaryButton(addBookButton);
            actions.add(addBookButton);
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
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(sectionHeading(manager ? "借阅流通记录" : "我的借阅记录",
                manager ? "查看全校借阅流水，选择未归还记录可代为办理归还。"
                        : "查看本人借阅历史，选择借阅中的记录即可归还。"), BorderLayout.NORTH);
        configureHistoryTable();
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, 10, 6));
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
        top.add(actions, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(VCampusTheme.scrollPane(historyTable), BorderLayout.CENTER);
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
        int[] widths = {110, 110, 100, 88, 100, 100, 100, 82};
        applyColumnWidths(historyTable, widths);
        historyTable.getColumnModel().getColumn(7).setCellRenderer(new BorrowStatusRenderer());
    }

    private static void configureTable(JTable table, int selectionMode) {
        VCampusTheme.table(table);
        table.setSelectionMode(selectionMode);
        table.setAutoCreateRowSorter(true);
    }

    private static void applyColumnWidths(JTable table, int[] widths) {
        for (int column = 0; column < widths.length && column < table.getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(widths[column]);
        }
    }

    private static JPanel sectionHeading(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(VCampusTheme.font(Font.BOLD, 18));
        titleLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitleLabel = new JLabel(subtitle);
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

    private void configureAccessibility() {
        keywordField.getAccessibleContext().setAccessibleName("图书关键词");
        keywordField.getAccessibleContext().setAccessibleDescription("输入书名、作者、ISBN 或图书编号");
        keywordField.setToolTipText("可输入书名、作者、ISBN 或图书编号");
        categoryField.getAccessibleContext().setAccessibleName("图书分类");
        categoryField.getAccessibleContext().setAccessibleDescription("输入分类名称以缩小查询范围");
        categoryField.setToolTipText("例如：计算机、文学、历史");
        bookTable.getAccessibleContext().setAccessibleName(manager ? "馆藏管理列表" : "可借图书列表");
        bookTable.getAccessibleContext().setAccessibleDescription(
                manager ? "选择一行可查看图书详情" : "可选择一行或多行后批量借阅");
        historyTable.getAccessibleContext().setAccessibleName(manager ? "全部借阅记录" : "我的借阅记录");
        historyTable.getAccessibleContext().setAccessibleDescription("选择借阅中的记录可办理归还");
        dueReminder.getAccessibleContext().setAccessibleName(manager ? "全校借阅到期提醒" : "个人借阅到期提醒");
        JButton[] actions = {searchButton, resetSearchButton, detailButton, borrowButton,
                historyButton, allHistoryButton, returnButton, addBookButton};
        for (JButton action : actions) {
            action.setFocusable(true);
            action.setRequestFocusEnabled(true);
            action.setFocusPainted(true);
        }
    }

    private void resetSearch() {
        keywordField.setText("");
        categoryField.setText("");
        showStatus("筛选条件已清空，可重新查询全部馆藏", VCampusTheme.MUTED);
        keywordField.requestFocusInWindow();
    }

    private void loadBooks() {
        final String keyword = keywordField.getText().trim();
        final String category = categoryField.getText().trim();
        runRequest("正在查询馆藏…", service -> service.search(session.getToken(), keyword, category),
                this::showBooks);
    }

    private void loadInitialData() {
        refreshCatalogAndHistory();
    }

    private void refreshCatalogAndHistory() {
        final String keyword = keywordField.getText().trim();
        final String category = categoryField.getText().trim();
        runRequest("正在刷新馆藏与借阅期限…",
                service -> service.search(session.getToken(), keyword, category), response -> {
                    showBooks(response);
                    if (response.getStatusCode() == StatusCode.OK) {
                        SwingUtilities.invokeLater(manager ? this::loadAllHistory : this::loadOwnHistory);
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
            JOptionPane.showMessageDialog(this, bookDetailPanel(book), "图书详情",
                    JOptionPane.INFORMATION_MESSAGE);
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
        runRequest("正在查询我的借阅记录…", service -> service.ownHistory(session.getToken()),
                response -> showHistory(response, "我的借阅记录"));
    }

    private void loadAllHistory() {
        runRequest("正在查询全部借阅记录…", service -> service.allHistory(session.getToken()),
                response -> showHistory(response, "全部借阅记录"));
    }

    private void returnSelected() {
        int selected = historyTable.getSelectedRow();
        if (selected < 0) {
            showStatus("请先选择一条借阅记录", VCampusTheme.DANGER);
            return;
        }
        int modelRow = historyTable.convertRowIndexToModel(selected);
        final String recordId = String.valueOf(historyModel.getValueAt(modelRow, 0));
        String recordStatus = String.valueOf(historyModel.getValueAt(modelRow, 7));
        if (!BorrowStatus.BORROWED.name().equals(recordStatus)) {
            showStatus("该记录已经归还，无需重复操作", VCampusTheme.DANGER);
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
        final JTextField category = new JTextField();
        final JTextField publisher = new JTextField();
        final JTextField price = new JTextField();
        final JTextField copies = new JTextField("1");
        final JTextField location = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, UiMetrics.px(10), UiMetrics.px(10)));
        form.setOpaque(false);
        addField(form, "图书号*", id);
        addField(form, "书名*", title);
        addField(form, "作者*", author);
        addField(form, "ISBN", isbn);
        addField(form, "分类", category);
        addField(form, "出版社", publisher);
        addField(form, "参考价格（元）*", price);
        addField(form, "初始册数*", copies);
        addField(form, "馆藏位置", location);
        JPanel dialog = new JPanel(new BorderLayout(0, 14));
        dialog.setBackground(VCampusTheme.PANEL);
        dialog.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        dialog.setPreferredSize(UiMetrics.dimension(470, 390));
        dialog.add(sectionHeading("录入馆藏", "带 * 的字段为必填项；新书初始可借册数与总册数一致。"),
                BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, dialog, "新增图书",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int total = Integer.parseInt(copies.getText().trim());
            double referencePrice = Double.parseDouble(price.getText().trim());
            if (!Double.isFinite(referencePrice) || referencePrice <= 0.0d) {
                throw new IllegalArgumentException("price must be positive");
            }
            final Book book = new Book(id.getText(), title.getText(), author.getText(), isbn.getText(),
                    category.getText(), publisher.getText(), referencePrice, total, total, location.getText());
            runRequest("正在新增图书…", service -> service.addBook(session.getToken(), book), response -> {
                if (!isSuccessful(response)) return;
                showStatus("新增图书成功，正在刷新馆藏…", VCampusTheme.SUCCESS);
                SwingUtilities.invokeLater(this::loadBooks);
            });
        } catch (NumberFormatException invalidNumber) {
            showStatus("参考价格必须是数字，初始册数必须是整数", VCampusTheme.DANGER);
        } catch (IllegalArgumentException invalidBook) {
            showStatus("请填写图书号、书名、作者，并检查价格和册数", VCampusTheme.DANGER);
        }
    }

    private static void addField(JPanel form, String label, JTextField field) {
        VCampusTheme.field(field);
        field.getAccessibleContext().setAccessibleName(label.replace("*", ""));
        form.add(fieldLabel(label, field));
        form.add(field);
    }

    private static JPanel bookDetailPanel(Book book) {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(VCampusTheme.PANEL);
        panel.setBorder(VCampusTheme.padding(8, 8, 8, 8));
        panel.setPreferredSize(UiMetrics.dimension(440, 285));
        panel.add(sectionHeading(book.getTitle(), book.getAuthor() + " · " + book.getCategory()),
                BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridLayout(0, 2, UiMetrics.px(12), UiMetrics.px(10)));
        fields.setOpaque(false);
        addDetailRow(fields, "图书编号", book.getBookId());
        addDetailRow(fields, "ISBN", book.getIsbn());
        addDetailRow(fields, "出版社", book.getPublisher());
        addDetailRow(fields, "参考价格", formatPrice(book.getPrice()));
        addDetailRow(fields, "馆藏位置", book.getLocation());
        addDetailRow(fields, "总册数", String.valueOf(book.getTotalCopies()));
        addDetailRow(fields, "当前可借", String.valueOf(book.getAvailableCopies()));
        panel.add(fields, BorderLayout.CENTER);

        JLabel availability = new JLabel(book.getAvailableCopies() > 0
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
        JLabel content = new JLabel(value == null || value.trim().isEmpty() ? "—" : value);
        content.setFont(VCampusTheme.font(Font.PLAIN, 13));
        content.setForeground(VCampusTheme.TEXT);
        panel.add(key);
        panel.add(content);
    }

    private void showBooks(Message response) {
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
            rows.add(bookRow(book));
            availableCopies += book.getAvailableCopies();
        }
        bookModel.replaceRows(rows);
        catalogCountValue.setText(books.size() + " 种");
        availableCountValue.setText(availableCopies + " 册");
        showStatus("已显示符合条件的图书，共 " + books.size() + " 种", VCampusTheme.SUCCESS);
    }

    private void showHistory(Message response, String scope) {
        if (!isSuccessful(response)) return;
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> records = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        List<BorrowRecord> borrowingRecords = new ArrayList<BorrowRecord>();
        int activeLoans = 0;
        for (Object item : records) {
            if (!(item instanceof BorrowRecord)) {
                showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
                return;
            }
            BorrowRecord record = (BorrowRecord) item;
            borrowingRecords.add(record);
            rows.add(historyRow(record));
            if (record.getStatus() == BorrowStatus.BORROWED) {
                activeLoans++;
            }
        }
        historyModel.replaceRows(rows);
        activeLoanCountValue.setText(activeLoans + " 条");
        updateDueReminder(borrowingRecords);
        showStatus("已显示" + scope + "，共 " + records.size() + " 条", VCampusTheme.SUCCESS);
    }

    private void updateDueReminder(List<BorrowRecord> records) {
        LibraryDueReminder.Summary summary = LibraryDueReminder.summarize(records, LocalDate.now());
        dueReminder.setText(LibraryDueReminder.message(summary, manager));
        Color color = summary.getOverdueCount() > 0 ? VCampusTheme.DANGER
                : summary.getDueSoonCount() > 0 ? VCampusTheme.ACCENT : VCampusTheme.SUCCESS;
        VCampusTheme.statusPill(dueReminder, color);
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
                    showStatus("无法连接图书馆服务器，请确认服务器已启动", VCampusTheme.DANGER);
                } finally {
                    loadingStatus.stop();
                    if (requestLifecycle.isCurrent(requestId)) {
                        requestInProgress = false;
                        updateButtonState();
                    }
                }
            }
        }.execute();
    }

    private boolean isSuccessful(Message response) {
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
        resetSearchButton.setEnabled(!requestInProgress);
        keywordField.setEnabled(!requestInProgress);
        categoryField.setEnabled(!requestInProgress);
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

    static Object[] historyRow(BorrowRecord record) {
        return new Object[] {record.getRecordId(), record.getOrderId(), record.getUserId(), record.getBookId(),
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
        return String.valueOf(value);
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
            setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else if ("借阅中".equals(label)) {
                setBackground(new Color(239, 246, 255));
                setForeground(VCampusTheme.PRIMARY);
            } else if ("已归还".equals(label)) {
                setBackground(new Color(240, 253, 244));
                setForeground(VCampusTheme.SUCCESS);
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

    private interface ResponseHandler {
        void handle(Message response);
    }
}

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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** 图书馆页面：目录查询、批量借阅、归还、借阅记录和馆藏维护。 */
public final class LibraryPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final boolean manager;

    private final BatchTableModel bookModel = new BatchTableModel(new Object[] {
            "图书号", "书名", "作者", "ISBN", "分类", "出版社", "总量", "可借", "位置"
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

    private boolean requestInProgress;
    private boolean allHistoryVisible;
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
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(tabs(), BorderLayout.CENTER);
        status.setForeground(VCampusTheme.MUTED);
        add(status, BorderLayout.SOUTH);

        searchButton.addActionListener(event -> loadBooks());
        detailButton.addActionListener(event -> loadSelectedDetail());
        borrowButton.addActionListener(event -> borrowSelected());
        historyButton.addActionListener(event -> loadOwnHistory());
        allHistoryButton.addActionListener(event -> loadAllHistory());
        returnButton.addActionListener(event -> returnSelected());
        addBookButton.addActionListener(event -> showAddBookDialog());
        keywordField.addActionListener(event -> loadBooks());
        categoryField.addActionListener(event -> loadBooks());
        updateButtonState();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel(manager ? "图书管理" : "图书馆");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        String capabilities = manager
                ? "可查询馆藏、新增图书并查看全部借阅记录。"
                : "可查询馆藏、批量借阅、归还并查看本人借阅记录。";
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName() + "；" + capabilities);
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(VCampusTheme.font(Font.PLAIN, 14));
        tabs.addTab("馆藏目录", catalogPanel());
        tabs.addTab("借阅记录", historyPanel());
        return tabs;
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        search.setOpaque(false);
        search.add(new JLabel("关键词"));
        search.add(keywordField);
        search.add(new JLabel("分类"));
        search.add(categoryField);
        VCampusTheme.secondaryButton(searchButton);
        search.add(searchButton);

        configureTable(bookTable, ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(detailButton);
        VCampusTheme.primaryButton(borrowButton);
        actions.add(detailButton);
        actions.add(borrowButton);
        if (manager) {
            VCampusTheme.secondaryButton(addBookButton);
            actions.add(addBookButton);
        }
        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(bookTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel historyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        configureTable(historyTable, ListSelectionModel.SINGLE_SELECTION);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(historyButton);
        VCampusTheme.primaryButton(returnButton);
        actions.add(historyButton);
        if (manager) {
            VCampusTheme.secondaryButton(allHistoryButton);
            actions.add(allHistoryButton);
        }
        actions.add(returnButton);
        panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private static void configureTable(JTable table, int selectionMode) {
        table.setSelectionMode(selectionMode);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
    }

    private void loadBooks() {
        final String keyword = keywordField.getText().trim();
        final String category = categoryField.getText().trim();
        runRequest("正在查询馆藏…", service -> service.search(session.getToken(), keyword, category),
                this::showBooks);
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
            JOptionPane.showMessageDialog(this, detailText(book), "图书详情",
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
            showStatus("借阅成功，共 " + count + " 本；正在刷新馆藏…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadBooks);
        });
    }

    private void loadOwnHistory() {
        runRequest("正在查询我的借阅记录…", service -> service.ownHistory(session.getToken()),
                response -> showHistory(response, "我的借阅记录", false));
    }

    private void loadAllHistory() {
        runRequest("正在查询全部借阅记录…", service -> service.allHistory(session.getToken()),
                response -> showHistory(response, "全部借阅记录", true));
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
            showStatus("归还成功，正在刷新借阅记录…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(allHistoryVisible ? this::loadAllHistory : this::loadOwnHistory);
        });
    }

    private void showAddBookDialog() {
        final JTextField id = new JTextField();
        final JTextField title = new JTextField();
        final JTextField author = new JTextField();
        final JTextField isbn = new JTextField();
        final JTextField category = new JTextField();
        final JTextField publisher = new JTextField();
        final JTextField copies = new JTextField("1");
        final JTextField location = new JTextField();
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        addField(form, "图书号*", id);
        addField(form, "书名*", title);
        addField(form, "作者*", author);
        addField(form, "ISBN", isbn);
        addField(form, "分类", category);
        addField(form, "出版社", publisher);
        addField(form, "初始册数*", copies);
        addField(form, "馆藏位置", location);
        if (JOptionPane.showConfirmDialog(this, form, "新增图书",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            int total = Integer.parseInt(copies.getText().trim());
            final Book book = new Book(id.getText(), title.getText(), author.getText(), isbn.getText(),
                    category.getText(), publisher.getText(), total, total, location.getText());
            runRequest("正在新增图书…", service -> service.addBook(session.getToken(), book), response -> {
                if (!isSuccessful(response)) return;
                showStatus("新增图书成功，正在刷新馆藏…", VCampusTheme.SUCCESS);
                SwingUtilities.invokeLater(this::loadBooks);
            });
        } catch (NumberFormatException invalidNumber) {
            showStatus("初始册数必须是整数", VCampusTheme.DANGER);
        } catch (IllegalArgumentException invalidBook) {
            showStatus("请填写图书号、书名、作者，并检查册数", VCampusTheme.DANGER);
        }
    }

    private static void addField(JPanel form, String label, JTextField field) {
        form.add(new JLabel(label));
        form.add(field);
    }

    private void showBooks(Message response) {
        if (!isSuccessful(response)) return;
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> books = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : books) {
            if (!(item instanceof Book)) {
                showStatus("服务器返回的馆藏数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            rows.add(bookRow((Book) item));
        }
        bookModel.replaceRows(rows);
        showStatus("已显示符合条件的图书，共 " + books.size() + " 种", VCampusTheme.SUCCESS);
    }

    private void showHistory(Message response, String scope, boolean allUsers) {
        if (!isSuccessful(response)) return;
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> records = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : records) {
            if (!(item instanceof BorrowRecord)) {
                showStatus("服务器返回的借阅记录格式不正确", VCampusTheme.DANGER);
                return;
            }
            rows.add(historyRow((BorrowRecord) item));
        }
        historyModel.replaceRows(rows);
        allHistoryVisible = allUsers;
        showStatus("已显示" + scope + "，共 " + records.size() + " 条", VCampusTheme.SUCCESS);
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
        borrowButton.setEnabled(!requestInProgress);
        historyButton.setEnabled(!requestInProgress);
        returnButton.setEnabled(!requestInProgress);
        allHistoryButton.setEnabled(manager && !requestInProgress);
        addBookButton.setEnabled(manager && !requestInProgress);
        keywordField.setEnabled(!requestInProgress);
        categoryField.setEnabled(!requestInProgress);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    static boolean canManage(Role role) {
        return role == Role.ADMIN || role == Role.LIBRARIAN;
    }

    static Object[] bookRow(Book book) {
        return new Object[] {book.getBookId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                book.getCategory(), book.getPublisher(), Integer.valueOf(book.getTotalCopies()),
                Integer.valueOf(book.getAvailableCopies()), book.getLocation()};
    }

    static Object[] historyRow(BorrowRecord record) {
        return new Object[] {record.getRecordId(), record.getOrderId(), record.getUserId(), record.getBookId(),
                record.getBorrowDate(), record.getDueDate(), record.getReturnDate() == null ? "" : record.getReturnDate(),
                record.getStatus().name()};
    }

    private static String detailText(Book book) {
        return "书名：" + book.getTitle() + "\n作者：" + book.getAuthor() + "\nISBN：" + book.getIsbn()
                + "\n分类：" + book.getCategory() + "\n出版社：" + book.getPublisher()
                + "\n库存：" + book.getAvailableCopies() + " / " + book.getTotalCopies()
                + "\n位置：" + book.getLocation();
    }

    private static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST) return "请求数据不正确，或所选图书库存不足";
        if (statusCode == StatusCode.UNAUTHORIZED) return "登录状态已失效，请重新登录";
        if (statusCode == StatusCode.FORBIDDEN) return "当前账号没有执行该图书馆操作的权限";
        if (statusCode == StatusCode.NOT_FOUND) return "图书或借阅记录不存在";
        if (statusCode == StatusCode.CONFLICT) return "图书或借阅记录状态已发生变化，请刷新后重试";
        return "服务器处理图书馆请求失败";
    }

    private interface LibraryRequest {
        Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}

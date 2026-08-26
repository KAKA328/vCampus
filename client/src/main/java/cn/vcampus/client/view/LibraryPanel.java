package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteLibraryService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.library.Book;
import cn.vcampus.user.Session;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** 图书馆页面：查询、借阅、归还。 */
public final class LibraryPanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JTextField keywordField = new JTextField(18);
    private final JLabel status = new JLabel("可查询图书，并办理本人借阅/归还；图书管理员可为读者办理。");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"图书号", "书名", "作者", "可借数量"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);

    public LibraryPanel(String host, int port, Session session) {
        this.host = host;
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel card = new JPanel(new BorderLayout());
        VCampusTheme.panel(card);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel("图书馆");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("学生和教师作为读者使用，图书管理员负责图书与借阅管理。");
        subtitle.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        JButton query = new JButton("查询图书");
        JButton borrow = new JButton("借阅");
        JButton ret = new JButton("归还");
        VCampusTheme.secondaryButton(query);
        VCampusTheme.primaryButton(borrow);
        VCampusTheme.secondaryButton(ret);
        query.addActionListener(e -> search());
        borrow.addActionListener(e -> borrow());
        ret.addActionListener(e -> returnBook());
        VCampusTheme.field(keywordField);
        buttons.add(new JLabel("关键词"));
        buttons.add(keywordField);
        buttons.add(query);
        buttons.add(borrow);
        buttons.add(ret);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void search() {
        run(new LibraryRequest() {
            @Override public Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException {
                return service.search(session.getToken(), keywordField.getText());
            }
        }, response -> showBooks(response));
    }

    private void borrow() {
        final String bookId = selectedBookId();
        if (bookId == null) {
            showStatus("请先选择一本书", false);
            return;
        }
        run(new LibraryRequest() {
            @Override public Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException {
                return service.borrow(session.getToken(), session.getUser().getUserId(), bookId);
            }
        }, response -> showOperation(response, "借阅成功"));
    }

    private void returnBook() {
        final String bookId = selectedBookId();
        if (bookId == null) {
            showStatus("请先选择一本书", false);
            return;
        }
        run(new LibraryRequest() {
            @Override public Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException {
                return service.returnBook(session.getToken(), session.getUser().getUserId(), bookId);
            }
        }, response -> showOperation(response, "归还成功"));
    }

    private void showBooks(Message response) {
        if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
            showStatus("图书查询失败", false);
            return;
        }
        tableModel.setRowCount(0);
        List<?> books = (List<?>) response.getPayload();
        for (Object item : books) {
            Book book = (Book) item;
            tableModel.addRow(new Object[] {
                    book.getBookId(), book.getTitle(), book.getAuthor(), book.getAvailableCopies()
            });
        }
        showStatus("图书查询完成，共 " + books.size() + " 条", true);
    }

    private void showOperation(Message response, String success) {
        if (response.getStatusCode() == StatusCode.OK) {
            showStatus(success + "，请刷新图书列表", true);
        } else {
            showStatus("操作失败：" + response.getStatusCode(), false);
        }
    }

    private String selectedBookId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : String.valueOf(tableModel.getValueAt(row, 0));
    }

    private void run(final LibraryRequest request, final LibraryResponse responseHandler) {
        showStatus("正在连接图书馆服务…", true);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteLibraryService service = new RemoteLibraryService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接服务器，请确认服务端已启动", false);
                }
            }
        }.execute();
    }

    private void showStatus(String message, boolean ok) {
        status.setText(message);
        status.setForeground(ok ? VCampusTheme.SUCCESS : VCampusTheme.DANGER);
    }

    private interface LibraryRequest {
        Message execute(RemoteLibraryService service) throws IOException, ClassNotFoundException;
    }

    private interface LibraryResponse {
        void handle(Message response);
    }
}

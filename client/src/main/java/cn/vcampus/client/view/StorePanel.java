package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStoreService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.Product;
import cn.vcampus.store.StoreOrder;
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

/** 商店页面：商品查询、购买、订单查询。 */
public final class StorePanel extends JPanel {
    private final String host;
    private final int port;
    private final Session session;
    private final JTextField quantityField = new JTextField("1", 5);
    private final JLabel status = new JLabel("学生和教师作为买家，商店管理员负责商品与订单管理。");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[] {"编号", "名称", "数量/库存", "类型"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);

    public StorePanel(String host, int port, Session session) {
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
        JLabel title = new JLabel("商店");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        JLabel subtitle = new JLabel("商品浏览、购买、个人订单查询；商店管理员可查看全部订单。");
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
        JButton products = new JButton("商品列表");
        JButton purchase = new JButton("购买");
        JButton ownOrders = new JButton("我的订单");
        JButton allOrders = new JButton("全部订单");
        VCampusTheme.secondaryButton(products);
        VCampusTheme.primaryButton(purchase);
        VCampusTheme.secondaryButton(ownOrders);
        VCampusTheme.secondaryButton(allOrders);
        VCampusTheme.field(quantityField);
        products.addActionListener(e -> listProducts());
        purchase.addActionListener(e -> purchase());
        ownOrders.addActionListener(e -> ownOrders());
        allOrders.addActionListener(e -> allOrders());
        allOrders.setEnabled(session.getUser().getRole() == Role.ADMIN
                || session.getUser().getRole() == Role.STORE_MANAGER);
        buttons.add(new JLabel("数量"));
        buttons.add(quantityField);
        buttons.add(products);
        buttons.add(purchase);
        buttons.add(ownOrders);
        buttons.add(allOrders);
        status.setForeground(VCampusTheme.MUTED);
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void listProducts() {
        run(new StoreRequest() {
            @Override public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.listProducts(session.getToken());
            }
        }, response -> showProducts(response));
    }

    private void purchase() {
        final String productId = selectedProductId();
        if (productId == null) {
            showStatus("请先选择商品", false);
            return;
        }
        final int quantity;
        try {
            quantity = Integer.parseInt(quantityField.getText().trim());
        } catch (NumberFormatException invalid) {
            showStatus("购买数量必须是数字", false);
            return;
        }
        run(new StoreRequest() {
            @Override public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.purchase(session.getToken(), session.getUser().getUserId(), productId, quantity);
            }
        }, response -> {
            if (response.getStatusCode() == StatusCode.OK) {
                showStatus("购买成功，请刷新商品或订单列表", true);
            } else {
                showStatus("购买失败：" + response.getStatusCode(), false);
            }
        });
    }

    private void ownOrders() {
        run(new StoreRequest() {
            @Override public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.ownOrders(session.getToken(), session.getUser().getUserId());
            }
        }, response -> showOrders(response, "我的订单"));
    }

    private void allOrders() {
        run(new StoreRequest() {
            @Override public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.allOrders(session.getToken());
            }
        }, response -> showOrders(response, "全部订单"));
    }

    private void showProducts(Message response) {
        if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
            showStatus("商品查询失败", false);
            return;
        }
        tableModel.setRowCount(0);
        List<?> products = (List<?>) response.getPayload();
        for (Object item : products) {
            Product product = (Product) item;
            tableModel.addRow(new Object[] {
                    product.getProductId(), product.getName(), product.getStock(), "商品"
            });
        }
        showStatus("商品查询完成，共 " + products.size() + " 条", true);
    }

    private void showOrders(Message response, String title) {
        if (response.getStatusCode() != StatusCode.OK || !(response.getPayload() instanceof List<?>)) {
            showStatus(title + "查询失败", false);
            return;
        }
        tableModel.setRowCount(0);
        List<?> orders = (List<?>) response.getPayload();
        for (Object item : orders) {
            StoreOrder order = (StoreOrder) item;
            tableModel.addRow(new Object[] {
                    order.getOrderId(), order.getProductName(), order.getQuantity(), "订单：" + order.getBuyerId()
            });
        }
        showStatus(title + "查询完成，共 " + orders.size() + " 条", true);
    }

    private String selectedProductId() {
        int row = table.getSelectedRow();
        if (row < 0 || !"商品".equals(String.valueOf(tableModel.getValueAt(row, 3)))) {
            return null;
        }
        return String.valueOf(tableModel.getValueAt(row, 0));
    }

    private void run(final StoreRequest request, final StoreResponse responseHandler) {
        showStatus("正在连接商店服务…", true);
        new SwingWorker<Message, Void>() {
            @Override protected Message doInBackground() throws Exception {
                try (RemoteStoreService service = new RemoteStoreService(host, port)) {
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

    private interface StoreRequest {
        Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException;
    }

    private interface StoreResponse {
        void handle(Message response);
    }
}

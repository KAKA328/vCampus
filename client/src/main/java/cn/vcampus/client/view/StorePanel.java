package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStoreService;
import cn.vcampus.common.Message;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

/** 商店页面：浏览商品、购买商品并查询当前账号购买记录。 */
public final class StorePanel extends JPanel {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final String host;
    private final int port;
    private final Session session;
    private final BatchTableModel productTableModel = new BatchTableModel(
            new Object[] {"商品号", "名称", "类别", "价格", "库存", "说明"});
    private final BatchTableModel orderTableModel = new BatchTableModel(
            new Object[] {"订单号", "商品", "数量", "单价", "总价", "时间"});
    private final JTable productTable = new JTable(productTableModel);
    private final JTable orderTable = new JTable(orderTableModel);
    private final JLabel status = new JLabel("请点击“刷新商品”查询商店商品");
    private final JButton refreshProductsButton = new JButton("刷新商品");
    private final JButton purchaseButton = new JButton("购买选中商品");
    private final JButton refreshOrdersButton = new JButton("我的订单");
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));

    private boolean requestInProgress;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public StorePanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);

        add(header(), BorderLayout.NORTH);
        add(tabs(), BorderLayout.CENTER);
        add(bottomPanel(), BorderLayout.SOUTH);

        refreshProductsButton.addActionListener(event -> loadProducts());
        purchaseButton.addActionListener(event -> purchaseSelectedProduct());
        refreshOrdersButton.addActionListener(event -> loadOrders());
        updateButtonState();
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JLabel title = new JLabel("商店");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);

        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName()
                + "；可浏览商品、购买商品并查询本人订单。");
        subtitle.setForeground(VCampusTheme.MUTED);

        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitle, BorderLayout.SOUTH);
        return panel;
    }

    private JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(VCampusTheme.font(Font.PLAIN, 14));
        tabs.addTab("商品列表", tablePanel(productTable, productTableModel));
        tabs.addTab("购买记录", tablePanel(orderTable, orderTableModel));
        return tabs;
    }

    private JPanel tablePanel(JTable table, DefaultTableModel model) {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel bottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshProductsButton);
        VCampusTheme.primaryButton(purchaseButton);
        VCampusTheme.secondaryButton(refreshOrdersButton);
        actions.add(refreshProductsButton);
        actions.add(new JLabel("数量"));
        actions.add(quantity);
        actions.add(purchaseButton);
        actions.add(refreshOrdersButton);

        status.setForeground(VCampusTheme.MUTED);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void loadProducts() {
        runRequest("正在查询商品…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.listProducts(session.getToken());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showProducts(response);
            }
        });
    }

    private void loadOrders() {
        runRequest("正在查询我的订单…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.ordersFor(session.getToken());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showOrders(response);
            }
        });
    }

    private void purchaseSelectedProduct() {
        final String productId = selectedProductId();
        if (productId == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        final int count = ((Integer) quantity.getValue()).intValue();
        runRequest("正在提交购买请求…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.purchase(session.getToken(), productId, count);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("购买成功，正在刷新商品列表…", VCampusTheme.SUCCESS);
                loadProducts();
            }
        });
    }

    private String selectedProductId() {
        int selectedRow = productTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return String.valueOf(productTableModel.getValueAt(selectedRow, 0));
    }

    private void showProducts(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的商品数据格式不正确", VCampusTheme.DANGER);
            return;
        }

        List<?> products = (List<?>) response.getPayload();
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<Object[]>();
        for (Object item : products) {
            if (!(item instanceof Product)) {
                showStatus("服务器返回的商品数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            Product product = (Product) item;
            rows.add(new Object[] {
                    product.getProductId(), product.getName(), product.getCategory(),
                    product.getPrice(), product.getStock(), product.getDescription()
            });
        }
        productTableModel.replaceRows(rows);
        showStatus("已显示全部商品，共 " + products.size() + " 个", VCampusTheme.SUCCESS);
    }

    private void showOrders(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的订单数据格式不正确", VCampusTheme.DANGER);
            return;
        }

        List<?> orders = (List<?>) response.getPayload();
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<Object[]>();
        for (Object item : orders) {
            if (!(item instanceof Order)) {
                showStatus("服务器返回的订单数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            Order order = (Order) item;
            rows.add(new Object[] {
                    order.getOrderId(), order.getProductName(), order.getQuantity(),
                    order.getUnitPrice(), order.getTotalPrice(), DATE_TIME.format(order.getOrderDate())
            });
        }
        orderTableModel.replaceRows(rows);
        showStatus("已显示我的订单，共 " + orders.size() + " 条", VCampusTheme.SUCCESS);
    }

    private void runRequest(String loadingMessage, final StoreRequest request,
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
                try (RemoteStoreService service = new RemoteStoreService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    showStatus("无法连接商店服务器，请确认服务器已启动", VCampusTheme.DANGER);
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

    private void updateButtonState() {
        refreshProductsButton.setEnabled(!requestInProgress);
        purchaseButton.setEnabled(!requestInProgress);
        refreshOrdersButton.setEnabled(!requestInProgress);
        quantity.setEnabled(!requestInProgress);
    }

    private void showResponseFailure(Message response) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
            return;
        }
        showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    private static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST) {
            return "请求数据不正确，或库存不足";
        }
        if (statusCode == StatusCode.UNAUTHORIZED) {
            return "登录状态已失效，请重新登录";
        }
        if (statusCode == StatusCode.FORBIDDEN) {
            return "当前账号没有访问商店功能的权限";
        }
        if (statusCode == StatusCode.NOT_FOUND) {
            return "商品或订单不存在";
        }
        return "服务器处理请求失败";
    }

    private interface StoreRequest {
        Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}

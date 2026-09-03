package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStoreService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.CartItem;
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
import javax.swing.JOptionPane;
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
            new Object[] { "商品号", "名称", "类别", "价格", "库存", "说明" });
    private final BatchTableModel orderTableModel = new BatchTableModel(
            new Object[] { "订单号", "商品", "数量", "单价", "总价", "时间" });
    private final BatchTableModel cartTableModel = new BatchTableModel(
            new Object[] { "条目号", "商品", "数量", "加入时间" });
    private final JTable productTable = new JTable(productTableModel);
    private final JTable orderTable = new JTable(orderTableModel);
    private final JTable cartTable = new JTable(cartTableModel);
    private final JLabel status = new JLabel("请点击“刷新商品”查询商店商品");
    private final JLabel balanceLabel = new JLabel("余额：--.-- 元");
    private final JButton refreshProductsButton = new JButton("刷新商品");
    private final JButton purchaseButton = new JButton("购买选中商品");
    private final JButton addToCartButton = new JButton("加入购物车");
    private final JButton refreshOrdersButton = new JButton("我的订单");
    private final JButton rechargeButton = new JButton("充值");
    private final JButton refreshCartButton = new JButton("刷新购物车");
    private final JButton removeFromCartButton = new JButton("移除选中");
    private final JButton checkoutButton = new JButton("去结算");
    private final JButton adjustBalanceButton = new JButton("校正余额");
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
    // 角色 ∈ {ADMIN, STORE_MANAGER} 才显示“校正余额”入口；按钮可见性只是 UX，真正拦截在服务端
    private final boolean canManageBalance;

    private boolean requestInProgress;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public StorePanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.canManageBalance = session.getUser().getRole() == Role.ADMIN
                || session.getUser().getRole() == Role.STORE_MANAGER;
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
        addToCartButton.addActionListener(event -> addSelectedToCart());
        refreshOrdersButton.addActionListener(event -> loadOrders());
        rechargeButton.addActionListener(event -> promptRecharge());
        refreshCartButton.addActionListener(event -> loadCart());
        removeFromCartButton.addActionListener(event -> removeSelectedCartItem());
        checkoutButton.addActionListener(event -> checkoutCart());
        if (canManageBalance) {
            adjustBalanceButton.addActionListener(event -> promptAdjustBalance());
        }
        updateButtonState();
        loadBalance();
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

        balanceLabel.setFont(VCampusTheme.font(Font.BOLD, 14));
        balanceLabel.setForeground(VCampusTheme.PRIMARY_DARK);

        JPanel subtitleRow = new JPanel(new BorderLayout(12, 0));
        subtitleRow.setOpaque(false);
        subtitleRow.add(subtitle, BorderLayout.CENTER);
        subtitleRow.add(balanceLabel, BorderLayout.EAST);

        panel.add(title, BorderLayout.NORTH);
        panel.add(subtitleRow, BorderLayout.SOUTH);
        return panel;
    }

    private JTabbedPane tabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(VCampusTheme.font(Font.PLAIN, 14));
        tabs.addTab("商品列表", tablePanel(productTable, productTableModel));
        tabs.addTab("购买记录", tablePanel(orderTable, orderTableModel));
        tabs.addTab("购物车", cartPanel());
        return tabs;
    }

    private JPanel cartPanel() {
        JPanel panel = tablePanel(cartTable, cartTableModel);
        JPanel cartActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        cartActions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshCartButton);
        VCampusTheme.secondaryButton(removeFromCartButton);
        VCampusTheme.primaryButton(checkoutButton);
        cartActions.add(refreshCartButton);
        cartActions.add(removeFromCartButton);
        cartActions.add(checkoutButton);
        panel.add(cartActions, BorderLayout.SOUTH);
        return panel;
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
        VCampusTheme.secondaryButton(addToCartButton);
        VCampusTheme.secondaryButton(refreshOrdersButton);
        VCampusTheme.primaryButton(rechargeButton);
        VCampusTheme.secondaryButton(adjustBalanceButton);
        actions.add(refreshProductsButton);
        actions.add(new JLabel("数量"));
        actions.add(quantity);
        actions.add(purchaseButton);
        actions.add(addToCartButton);
        actions.add(refreshOrdersButton);
        actions.add(rechargeButton);
        if (canManageBalance) {
            actions.add(adjustBalanceButton);
        }

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
                loadBalance();
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

    private void loadBalance() {
        runRequest("正在查询余额…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.balance(session.getToken());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showBalance(response);
            }
        });
    }

    private void showBalance(Message response) {
        // 服务端返回余额（分，Long）；显示时 /100 转元保留两位
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof Number) {
            long cents = ((Number) response.getPayload()).longValue();
            balanceLabel.setText("余额：" + formatYuan(cents) + " 元");
        } else {
            balanceLabel.setText("余额：--.-- 元");
        }
    }

    // 分转元显示：long 分 /100，保留两位小数（仅用于展示，不参与账本运算）
    private static String formatYuan(long cents) {
        return String.format("%d.%02d", cents / 100, Math.abs(cents % 100));
    }

    private void promptRecharge() {
        String input = JOptionPane.showInputDialog(this, "请输入充值金额（元）：", "充值",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        long parsed;
        try {
            // 元转分：Math.round(元*100) 一次性换算，避免浮点误差进账本
            parsed = Math.round(Double.parseDouble(input.trim()) * 100);
        } catch (NumberFormatException invalid) {
            showStatus("充值金额格式不正确", VCampusTheme.DANGER);
            return;
        }
        if (parsed <= 0) {
            showStatus("充值金额必须大于 0", VCampusTheme.DANGER);
            return;
        }
        final long cents = parsed;
        runRequest("正在充值…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.recharge(session.getToken(), cents);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("充值成功", VCampusTheme.SUCCESS);
                loadBalance();
            }
        });
    }

    private void addSelectedToCart() {
        final String productId = selectedProductId();
        if (productId == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        final int count = ((Integer) quantity.getValue()).intValue();
        runRequest("正在加入购物车…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.addToCart(session.getToken(), productId, count);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("已加入购物车", VCampusTheme.SUCCESS);
                loadCart();
            }
        });
    }

    private void loadCart() {
        runRequest("正在查询购物车…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.cart(session.getToken());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                showCart(response);
            }
        });
    }

    private void showCart(Message response) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            cartTableModel.replaceRows(new java.util.ArrayList<Object[]>());
            return;
        }
        List<?> items = (List<?>) response.getPayload();
        java.util.ArrayList<Object[]> rows = new java.util.ArrayList<Object[]>();
        for (Object item : items) {
            if (!(item instanceof CartItem)) {
                showStatus("服务器返回的购物车数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            CartItem cartItem = (CartItem) item;
            rows.add(new Object[] {
                    cartItem.getCartItemId(), cartItem.getProductId(), cartItem.getQuantity(),
                    DATE_TIME.format(cartItem.getAddedAt())
            });
        }
        cartTableModel.replaceRows(rows);
        showStatus("已显示购物车，共 " + items.size() + " 条", VCampusTheme.SUCCESS);
    }

    private String selectedCartItemId() {
        int selectedRow = cartTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return String.valueOf(cartTableModel.getValueAt(selectedRow, 0));
    }

    private void removeSelectedCartItem() {
        final String cartItemId = selectedCartItemId();
        if (cartItemId == null) {
            showStatus("请先在购物车表中选择一个条目", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在移除购物车条目…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.removeFromCart(session.getToken(), cartItemId);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("已移除购物车条目", VCampusTheme.SUCCESS);
                loadCart();
            }
        });
    }

    private void checkoutCart() {
        runRequest("正在结算购物车…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.checkout(session.getToken());
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("结算成功，正在刷新…", VCampusTheme.SUCCESS);
                loadCart();
                loadBalance();
                loadProducts();
                loadOrders();
            }
        });
    }

    private void promptAdjustBalance() {
        String targetUserId = JOptionPane.showInputDialog(this, "请输入目标用户编号：", "校正余额",
                JOptionPane.PLAIN_MESSAGE);
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            return;
        }
        String input = JOptionPane.showInputDialog(this, "请输入新的余额（元）：", "校正余额",
                JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        long parsed;
        try {
            parsed = Math.round(Double.parseDouble(input.trim()) * 100);
        } catch (NumberFormatException invalid) {
            showStatus("余额金额格式不正确", VCampusTheme.DANGER);
            return;
        }
        if (parsed < 0) {
            showStatus("余额不能为负", VCampusTheme.DANGER);
            return;
        }
        final long cents = parsed;
        final String target = targetUserId.trim();
        runRequest("正在校正余额…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.adjustBalance(session.getToken(), target, cents);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("已校正 " + target + " 的余额", VCampusTheme.SUCCESS);
                loadBalance();
            }
        });
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
        addToCartButton.setEnabled(!requestInProgress);
        refreshOrdersButton.setEnabled(!requestInProgress);
        rechargeButton.setEnabled(!requestInProgress);
        refreshCartButton.setEnabled(!requestInProgress);
        removeFromCartButton.setEnabled(!requestInProgress);
        checkoutButton.setEnabled(!requestInProgress);
        adjustBalanceButton.setEnabled(!requestInProgress && canManageBalance);
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
        if (statusCode == StatusCode.PAYMENT_REQUIRED) {
            return "余额不足，请先充值";
        }
        if (statusCode == StatusCode.CONFLICT) {
            return "操作冲突，库存或余额已变化，请刷新后重试";
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

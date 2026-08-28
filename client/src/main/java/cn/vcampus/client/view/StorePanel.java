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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** 商店页面，提供商品浏览、购买商品和用户订单查询功能 */
public final class StorePanel extends JPanel {
    private final String host;// 主机地址
    private final int port;// 端口号
    private final Session session; // 登录会话

    // 商品表格模型
    private final DefaultTableModel productTableModel = new DefaultTableModel(
            new Object[] { "商品编号", "商品名称", "单价", "库存", "分类", "描述" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    // 订单表格模型
    private final DefaultTableModel orderTableModel = new DefaultTableModel(
            new Object[] { "订单编号", "商品编号", "商品名称", "单价", "数量", "总价", "下单时间" }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    // 商品表格
    private final JTable table = new JTable(productTableModel);
    private final JLabel status = new JLabel("请点击\"刷新商品\"查询商品信息");// 底部状态栏
    private final JButton refreshButton = new JButton("刷新商品");
    private final JButton purchaseButton = new JButton("购买");
    private final JButton modeButton = new JButton("我的订单");

    // 状态开关
    private boolean requestInProgress;// 是否有请求正在处理，防止重复请求
    private boolean orderMode;// 是否正在看订单列表

    private static final DateTimeFormatter ORDER_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 构造函数
    public StorePanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("the host and session cannot be null or empty");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;

        build();// 构建
    }

    // 构建界面
    private void build() {
        setLayout(new BorderLayout(0, 18));// 设定布局规则
        setOpaque(false);// 显示主题
        add(header(), BorderLayout.NORTH);// 添加头部
        add(tablePanel(), BorderLayout.CENTER);// 添加表格面板
        add(bottomPanel(), BorderLayout.SOUTH);
        refreshButton.addActionListener(event -> refresh()); // 添加刷新按钮监听器
        purchaseButton.addActionListener(event -> purchase());// 添加购买按钮监听器
        modeButton.addActionListener(event -> switchMode());// 添加订单按钮监听器
        updateButtonState();// 更新按钮状态
    }

    // 创建顶部面板
    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));// 创建顶部
        panel.setOpaque(false);
        JLabel title = new JLabel("商店");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));// 设置标题字体
        title.setForeground(VCampusTheme.PRIMARY_DARK);// 设置标题颜色
        JLabel currUser = new JLabel("当前用户: " + session.getUser().getDisplayName() + "; 可浏览商品、购买商品、查看订单");
        currUser.setForeground(VCampusTheme.MUTED);
        panel.add(title, BorderLayout.NORTH);
        panel.add(currUser, BorderLayout.SOUTH);
        return panel;
    }

    // 创建表格面板
    private JPanel tablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        VCampusTheme.panel(panel);// 设置面板样式
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);// 一次只能选中一行
        table.setRowHeight(30); // 设置行高
        table.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // 创建底部面板
    private JPanel bottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));// 按钮面板行
        actions.setOpaque(false);
        VCampusTheme.primaryButton(purchaseButton);
        VCampusTheme.secondaryButton(modeButton);
        VCampusTheme.secondaryButton(refreshButton);
        actions.add(refreshButton);
        actions.add(purchaseButton);
        actions.add(modeButton);
        panel.add(actions, BorderLayout.NORTH);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private void refresh() {
        if (orderMode) {
            runRequest("正在查询订单…",
                    new StoreRequest() {
                        @Override
                        public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                            return service.findOrders(session.getToken());
                        }
                    },
                    new ResponseHandler() {
                        @Override
                        public void handle(Message response) {
                            showOrders(response, "已显示订单");
                        }
                    });
        } else {
            runRequest("正在查询商品…",
                    new StoreRequest() {
                        @Override
                        public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                            return service.listProducts(session.getToken());
                        }
                    },
                    new ResponseHandler() {
                        @Override
                        public void handle(Message response) {
                            showProducts(response, "已显示商品");
                        }
                    });
        }
    }

    private void purchase() {
        String productId = selectedProductId();
        if (productId == null) {
            showStatus("请选择要购买的商品", VCampusTheme.DANGER);
            return;
        }
        String input = JOptionPane.showInputDialog(this, "请输入购买数量", "购买商品", JOptionPane.QUESTION_MESSAGE);
        if (input == null || input.trim().isEmpty())
            return;
        int quantity;
        try {
            quantity = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            showStatus("请输入有效的数量", VCampusTheme.DANGER);
            return;
        }
        if (quantity <= 0) {
            showStatus("请输入有效的数量", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在提交购买请求…", new StoreRequest() {
            @Override
            public Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException {
                return service.purchase(session.getToken(), productId, quantity);
            }
        }, new ResponseHandler() {
            @Override
            public void handle(Message response) {
                if (response.getStatusCode() != StatusCode.OK) {
                    showResponseFailure(response);
                    return;
                }
                showStatus("购买成功，正在刷新商品列表…", VCampusTheme.SUCCESS);
                refresh();
            }
        });
    }

    private void switchMode() {
        orderMode = !orderMode;
        table.setModel(orderMode ? orderTableModel : productTableModel);
        modeButton.setText(orderMode ? "返回商品" : "我的订单");
        updateButtonState();
        refresh();
    }

    private void updateButtonState() {
        boolean enabled = !requestInProgress;
        refreshButton.setEnabled(enabled);
        purchaseButton.setEnabled(enabled && !orderMode);
        modeButton.setEnabled(enabled);
    }

    // 运行请求
    // 私有接口
    private interface StoreRequest {
        Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }

    private void runRequest(String loadingMessage, final StoreRequest request,
            final ResponseHandler responseHandler) {
        requestInProgress = true;// 设置请求正在处理中
        updateButtonState();
        status.setText(loadingMessage);
        status.setForeground(VCampusTheme.MUTED);
        // 启动后台线程执行请求
        new SwingWorker<Message, Void>() {
            @Override
            protected Message doInBackground() throws Exception {
                try (RemoteStoreService service = new RemoteStoreService(host, port)) {
                    return request.execute(service);
                }
            }

            @Override
            protected void done() {
                requestInProgress = false;
                updateButtonState();
                try {
                    responseHandler.handle(get());
                } catch (Exception failure) {
                    status.setText("无法连接商店服务器，请确认服务器已启动");
                    status.setForeground(VCampusTheme.DANGER);
                }
            }
        }.execute();
    }

    // 状态栏改字，用于给用户通信时修改状态栏内容和颜色
    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    // 翻译报错
    private void showResponseFailure(Message response) {
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
            return;
        }
        showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
    }

    // 翻译状态码
    private static String statusMessage(StatusCode status) {
        switch (status) {
            case BAD_REQUEST:
                return "请求数据不正确";
            case UNAUTHORIZED:
                return "登录状态已失效，请重新登录";
            case FORBIDDEN:
                return "当前账号没有执行此操作的权限";
            case NOT_FOUND:
                return "商品或订单不存在";
            default:
                return "服务器处理请求失败";
        }
    }

    // 选中的商品
    private String selectedProductId() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }
        return String.valueOf(productTableModel.getValueAt(selectedRow, 0));
    }

    // 将商品列表填入表格
    private void showProducts(Message response, String successMessage) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        // 如果返回的数据不是列表类型
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("商品列表格式错误", VCampusTheme.DANGER);
            return;
        }
        productTableModel.setRowCount(0);
        List<?> products = (List<?>) response.getPayload();
        for (Object product : products) {
            if (!(product instanceof Product)) {
                showStatus("商品列表格式错误", VCampusTheme.DANGER);
                productTableModel.setRowCount(0);
                return;
            }
            Product pro = (Product) product;
            productTableModel.addRow(new Object[] { pro.getProductId(), pro.getName(), pro.getPrice(), pro.getStock(),
                    pro.getCategory(), pro.getDescription() });
        }
        showStatus(successMessage + "，共 " + products.size() + " 件", VCampusTheme.SUCCESS);
    }

    // 将订单列表填入表格
    private void showOrders(Message response, String successMessage) {
        if (response.getStatusCode() != StatusCode.OK) {
            showResponseFailure(response);
            return;
        }
        // 如果返回的数据不是列表类型
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("订单列表格式错误", VCampusTheme.DANGER);
            return;
        }
        orderTableModel.setRowCount(0);
        List<?> orders = (List<?>) response.getPayload();
        for (Object order : orders) {
            if (!(order instanceof Order)) {
                showStatus("订单列表格式错误", VCampusTheme.DANGER);
                orderTableModel.setRowCount(0);
                return;
            }
            Order ord = (Order) order;
            orderTableModel.addRow(
                    new Object[] { ord.getOrderId(), ord.getProductId(), ord.getProductName(),
                            ord.getUnitPrice(), ord.getQuantity(), ord.getTotalPrice(),
                            ord.getOrderDate().format(ORDER_TIME_FORMAT) });
        }
        showStatus(successMessage + "，共 " + orders.size() + " 条", VCampusTheme.SUCCESS);
    }
}

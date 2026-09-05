package cn.vcampus.client.view;

import cn.vcampus.client.service.RemoteStoreService;
import cn.vcampus.common.Message;
import cn.vcampus.common.Role;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.CartLine;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.user.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * 商店页面：商品浏览与筛选、直接购买、购物车结算、校园钱包与管理员商品维护。
 * 买家角色与管理员角色共用本面板，管理员专属入口按 manager 标记增删，真正拦截在服务端权限门槛，
 * 界面显隐只是 UX（对齐 LibraryPanel 的角色分化方式）。
 */
public final class StorePanel extends JPanel {
    private static final int HOT_PRODUCT_LIMIT = 10;
    private static final int MAX_QUANTITY = 999;

    private final String host;
    private final int port;
    private final Session session;
    private final boolean manager;

    private final BatchTableModel productModel = new BatchTableModel(new Object[] {
            "商品号", "名称", "类别", "价格", "库存", "状态", "说明"
    });
    private final BatchTableModel cartModel = new BatchTableModel(new Object[] {
            "条目号", "商品", "单价", "数量", "小计", "状态"
    });
    private final BatchTableModel orderModel = new BatchTableModel(new Object[] {
            "订单号", "商品", "数量", "单价", "总价", "时间"
    });
    private final BatchTableModel allOrderModel = new BatchTableModel(new Object[] {
            "订单号", "买家", "商品", "数量", "单价", "总价", "时间"
    });
    private final BatchTableModel ledgerModel = new BatchTableModel(new Object[] {
            "时间", "类型", "金额", "变动后余额", "操作者", "备注"
    });
    private final JTable productTable = new JTable(productModel);
    private final JTable cartTable = new JTable(cartModel);
    private final JTable orderTable = new JTable(orderModel);
    private final JTable allOrderTable = new JTable(allOrderModel);
    private final JTable ledgerTable = new JTable(ledgerModel);

    // 服务端返回的全量商品，与按关键词过滤后实际显示在表里的商品分开存放：
    // 表格 modelRow 必须映射到 visibleProducts 而不是 loadedProducts，否则过滤后选中会取错商品
    private final List<Product> loadedProducts = new ArrayList<Product>();
    private final List<Product> visibleProducts = new ArrayList<Product>();
    private final List<CartLine> cartLines = new ArrayList<CartLine>();
    private long cartTotalCents;

    private final JTextField keywordField = new JTextField(12);
    private final JTextField categoryField = new JTextField(10);
    private final JLabel status = new JLabel("输入关键词或类别查询商品，也可直接点击“查询商品”查看全部在售商品");
    private final JLabel balanceLabel = new JLabel("余额：--.-- 元");
    private final JLabel cartTotalLabel = new JLabel("合计：--.-- 元");
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, MAX_QUANTITY, 1));

    private final JButton searchButton = new JButton("查询商品");
    private final JButton hotButton = new JButton("热销 Top" + HOT_PRODUCT_LIMIT);
    private final JButton purchaseButton = new JButton("购买选中");
    private final JButton addToCartButton = new JButton("加入购物车");
    private final JButton detailButton = new JButton("商品详情");
    private final JButton addProductButton = new JButton("新增商品");
    private final JButton editProductButton = new JButton("编辑选中");
    private final JButton restockButton = new JButton("补货");
    private final JButton deactivateButton = new JButton("下架选中");
    private final JButton reactivateButton = new JButton("重新上架");
    private final JButton refreshCartButton = new JButton("刷新购物车");
    private final JButton updateQuantityButton = new JButton("修改数量");
    private final JButton removeFromCartButton = new JButton("移除选中");
    private final JButton checkoutButton = new JButton("去结算");
    private final JButton refreshOrdersButton = new JButton("刷新我的订单");
    private final JButton rechargeButton = new JButton("充值");
    private final JButton refreshLedgerButton = new JButton("刷新流水");
    private final JButton adjustBalanceButton = new JButton("校正余额");
    private final JButton allOrdersButton = new JButton("刷新全部订单");

    private boolean requestInProgress;
    private boolean hotViewVisible;
    private final RequestLifecycle requestLifecycle = new RequestLifecycle();

    public StorePanel(String host, int port, Session session) {
        if (host == null || host.trim().isEmpty() || session == null) {
            throw new IllegalArgumentException("host and session must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.manager = canManage(session.getUser().getRole());
        build();
    }

    /** 管理员与商店经理可维护商品、查看全部订单和校正余额。 */
    static boolean canManage(Role role) {
        return role == Role.ADMIN || role == Role.STORE_MANAGER;
    }

    private void build() {
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);

        searchButton.addActionListener(event -> loadProducts());
        hotButton.addActionListener(event -> toggleHotView());
        purchaseButton.addActionListener(event -> purchaseSelected());
        addToCartButton.addActionListener(event -> addSelectedToCart());
        detailButton.addActionListener(event -> showSelectedDetail());
        addProductButton.addActionListener(event -> showAddProductDialog());
        editProductButton.addActionListener(event -> showEditProductDialog());
        restockButton.addActionListener(event -> restockSelected());
        deactivateButton.addActionListener(event -> deactivateSelected());
        reactivateButton.addActionListener(event -> reactivateSelected());
        refreshCartButton.addActionListener(event -> loadCart());
        updateQuantityButton.addActionListener(event -> updateSelectedCartQuantity());
        removeFromCartButton.addActionListener(event -> removeSelectedCartItem());
        checkoutButton.addActionListener(event -> checkoutCart());
        refreshOrdersButton.addActionListener(event -> loadOrders());
        rechargeButton.addActionListener(event -> promptRecharge());
        refreshLedgerButton.addActionListener(event -> loadLedger());
        allOrdersButton.addActionListener(event -> loadAllOrders());
        if (manager) {
            adjustBalanceButton.addActionListener(event -> promptAdjustBalance());
        }
        // 回车即查询，与图书馆页的搜索框行为一致
        keywordField.addActionListener(event -> applyKeywordFilter());
        categoryField.addActionListener(event -> loadProducts());
        keywordField.getDocument().addDocumentListener(new KeywordWatcher());
        // 选中商品后按实际库存收窄数量上限，避免提交一个必然被服务端拒绝的数量
        productTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                syncQuantityLimit();
            }
        });

        updateButtonState();
        loadBalance();
    }

    /**
     * 页面主体：把功能页签包进可垂直滚动的 {@link ScrollablePagePanel}，窗口高度不足时整页滚动，
     * 与图书馆/选课等面板共用统一的页面级滚动结构（对齐 PR #41 的前端刷新）。
     */
    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JTabbedPane tabs = tabs();
        tabs.setPreferredSize(new Dimension(0, 430));
        tabs.setMinimumSize(new Dimension(0, 300));
        panel.add(tabs, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout());
        VCampusTheme.panel(statusPanel);
        status.setForeground(VCampusTheme.MUTED);
        statusPanel.add(status, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JLabel title = new JLabel(manager ? "商店管理" : "商店");
        title.setFont(VCampusTheme.font(Font.BOLD, 24));
        title.setForeground(VCampusTheme.PRIMARY_DARK);
        String capabilities = manager
                ? "可维护商品与库存、查看全部订单、校正用户余额，也可自行购买。"
                : "可浏览商品、直接购买、购物车结算，并查看本人订单与钱包流水。";
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName() + "；" + capabilities);
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
        final JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(VCampusTheme.font(Font.PLAIN, 14));
        tabs.addTab("商品列表", catalogPanel());
        tabs.addTab("购物车", cartPanel());
        tabs.addTab("我的订单", orderPanel());
        tabs.addTab("钱包", walletPanel());
        if (manager) {
            tabs.addTab("全部订单", allOrderPanel());
        }
        // 切页签即加载该页数据，用户不必每页都先找“刷新”按钮
        tabs.addChangeListener(event -> onTabSelected(tabs.getSelectedIndex()));
        return tabs;
    }

    /** 页签索引固定为 商品0 / 购物车1 / 我的订单2 / 钱包3 / 全部订单4，其中 4 仅管理员存在。 */
    private void onTabSelected(int index) {
        // 已有请求在飞时不再叠加，否则新代次会顶掉正在回来的响应
        if (requestInProgress) {
            return;
        }
        if (index == 1) {
            loadCart();
        } else if (index == 2) {
            loadOrders();
        } else if (index == 3) {
            loadLedger();
        } else if (index == 4 && manager) {
            loadAllOrders();
        }
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);

        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        search.setOpaque(false);
        search.add(new JLabel("关键词"));
        VCampusTheme.field(keywordField);
        search.add(keywordField);
        search.add(new JLabel("类别"));
        VCampusTheme.field(categoryField);
        search.add(categoryField);
        VCampusTheme.secondaryButton(searchButton);
        VCampusTheme.secondaryButton(hotButton);
        search.add(searchButton);
        search.add(hotButton);

        configureTable(productTable);
        applyMoneyColumns(productTable, MoneyCellRenderer.MoneyFormat.YUAN, 3);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.primaryButton(purchaseButton);
        VCampusTheme.secondaryButton(addToCartButton);
        VCampusTheme.secondaryButton(detailButton);
        actions.add(new JLabel("数量"));
        actions.add(quantity);
        actions.add(purchaseButton);
        actions.add(addToCartButton);
        actions.add(detailButton);
        if (manager) {
            VCampusTheme.secondaryButton(addProductButton);
            VCampusTheme.secondaryButton(editProductButton);
            VCampusTheme.secondaryButton(restockButton);
            VCampusTheme.secondaryButton(deactivateButton);
            VCampusTheme.secondaryButton(reactivateButton);
            actions.add(addProductButton);
            actions.add(editProductButton);
            actions.add(restockButton);
            actions.add(deactivateButton);
            actions.add(reactivateButton);
        }

        panel.add(search, BorderLayout.NORTH);
        panel.add(new JScrollPane(productTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel cartPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        configureTable(cartTable);
        applyMoneyColumns(cartTable, MoneyCellRenderer.MoneyFormat.YUAN, 2, 4);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshCartButton);
        VCampusTheme.secondaryButton(updateQuantityButton);
        VCampusTheme.secondaryButton(removeFromCartButton);
        VCampusTheme.primaryButton(checkoutButton);
        actions.add(refreshCartButton);
        actions.add(updateQuantityButton);
        actions.add(removeFromCartButton);
        actions.add(checkoutButton);

        // 合计只累加服务端给出的 subtotalCents，不在前端用单价×数量重算，否则会与实扣金额差一两分
        cartTotalLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
        cartTotalLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(actions, BorderLayout.WEST);
        footer.add(cartTotalLabel, BorderLayout.EAST);

        panel.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel orderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        configureTable(orderTable);
        applyMoneyColumns(orderTable, MoneyCellRenderer.MoneyFormat.YUAN, 3, 4);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(refreshOrdersButton);
        actions.add(refreshOrdersButton);

        panel.add(new JScrollPane(orderTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel walletPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);

        JPanel balanceBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        balanceBar.setOpaque(false);
        VCampusTheme.primaryButton(rechargeButton);
        VCampusTheme.secondaryButton(refreshLedgerButton);
        balanceBar.add(rechargeButton);
        balanceBar.add(refreshLedgerButton);
        if (manager) {
            VCampusTheme.secondaryButton(adjustBalanceButton);
            balanceBar.add(adjustBalanceButton);
        }

        configureTable(ledgerTable);
        // 金额列是带符号的「分」，余额列是非负的「分」，两种单位不能共用同一渲染分支
        ledgerTable.getColumnModel().getColumn(2)
                .setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.MoneyFormat.SIGNED_CENTS));
        ledgerTable.getColumnModel().getColumn(3)
                .setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.MoneyFormat.CENTS));

        panel.add(balanceBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(ledgerTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel allOrderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        configureTable(allOrderTable);
        applyMoneyColumns(allOrderTable, MoneyCellRenderer.MoneyFormat.YUAN, 4, 5);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        VCampusTheme.secondaryButton(allOrdersButton);
        actions.add(allOrdersButton);

        panel.add(new JScrollPane(allOrderTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private static void configureTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        // 开启列排序后，视图行号与模型行号不再相同，取值一律经 convertRowIndexToModel 换算
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
    }

    private static void applyMoneyColumns(JTable table, MoneyCellRenderer.MoneyFormat format, int... columns) {
        MoneyCellRenderer renderer = new MoneyCellRenderer(format);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    private void loadProducts() {
        final String category = categoryField.getText().trim();
        hotViewVisible = false;
        hotButton.setText("热销 Top" + HOT_PRODUCT_LIMIT);
        runRequest(category.isEmpty() ? "正在查询商品…" : "正在查询「" + category + "」类商品…",
                service -> category.isEmpty()
                        ? service.listProducts(session.getToken())
                        : service.listProducts(session.getToken(), category),
                this::showProducts);
    }

    /** 热销排行不占页签，做成商品表的视图切换，与图书馆「本人/全部借阅记录」的切换方式一致。 */
    private void toggleHotView() {
        if (hotViewVisible) {
            loadProducts();
            return;
        }
        loadHotProducts();
    }

    private void loadHotProducts() {
        runRequest("正在查询热销商品…", service -> service.hotProducts(session.getToken(), HOT_PRODUCT_LIMIT),
                response -> {
                    // 先确认成功再切视图标记，否则查询失败会把按钮错留在「返回全部商品」状态
                    if (!isSuccessful(response)) {
                        return;
                    }
                    hotViewVisible = true;
                    hotButton.setText("返回全部商品");
                    showProducts(response);
                });
    }

    private void showProducts(Message response) {
        if (!isSuccessful(response)) {
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的商品数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> products = (List<?>) response.getPayload();
        List<Product> parsed = new ArrayList<Product>();
        for (Object item : products) {
            if (!(item instanceof Product)) {
                showStatus("服务器返回的商品数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            parsed.add((Product) item);
        }
        // 整体校验通过后才替换缓存，避免半途出错留下与表格不一致的半份数据
        loadedProducts.clear();
        loadedProducts.addAll(parsed);
        applyKeywordFilter();
        showStatus((hotViewVisible ? "已显示热销商品" : "已显示商品") + "，共 " + parsed.size() + " 个",
                VCampusTheme.SUCCESS);
    }

    /** 关键词只在已加载的商品里本地过滤，不额外发请求；过滤结果同步写进 visibleProducts 供选中行回查。 */
    private void applyKeywordFilter() {
        String keyword = keywordField.getText().trim().toLowerCase();
        List<Object[]> rows = new ArrayList<Object[]>();
        visibleProducts.clear();
        for (Product product : loadedProducts) {
            if (!keyword.isEmpty() && !matchesKeyword(product, keyword)) {
                continue;
            }
            visibleProducts.add(product);
            rows.add(StoreRowMapper.productRow(product));
        }
        productModel.replaceRows(rows);
        syncQuantityLimit();
    }

    private static boolean matchesKeyword(Product product, String lowerKeyword) {
        return product.getName().toLowerCase().contains(lowerKeyword)
                || product.getProductId().toLowerCase().contains(lowerKeyword)
                || product.getCategory().toLowerCase().contains(lowerKeyword)
                || (product.getDescription() != null && product.getDescription().toLowerCase().contains(lowerKeyword));
    }

    private Product selectedProduct() {
        int selected = productTable.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        // 表格开了列排序，视图行号必须换算成模型行号，而模型行对应 visibleProducts 而非 loadedProducts
        int modelRow = productTable.convertRowIndexToModel(selected);
        if (modelRow < 0 || modelRow >= visibleProducts.size()) {
            return null;
        }
        return visibleProducts.get(modelRow);
    }

    /** 数量上限跟随选中商品的实际库存，避免提交一个注定被服务端拒绝的数量。 */
    private void syncQuantityLimit() {
        SpinnerNumberModel model = (SpinnerNumberModel) quantity.getModel();
        Product product = selectedProduct();
        int stock = product == null ? MAX_QUANTITY : product.getStock();
        int maximum = Math.max(1, Math.min(stock, MAX_QUANTITY));
        model.setMaximum(Integer.valueOf(maximum));
        if (((Integer) model.getValue()).intValue() > maximum) {
            model.setValue(Integer.valueOf(maximum));
        }
    }

    private void purchaseSelected() {
        final Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        if (!product.isActive()) {
            showStatus("「" + product.getName() + "」已下架，无法购买", VCampusTheme.DANGER);
            return;
        }
        final int count = ((Integer) quantity.getValue()).intValue();
        if (product.getStock() < count) {
            showStatus("「" + product.getName() + "」库存仅剩 " + product.getStock() + " 件，请调整数量",
                    VCampusTheme.DANGER);
            return;
        }
        // 下单前确认：与服务端同式换算（元→分，同走 Money.toCents 唯一入口），把「将扣多少钱」显式摆给用户，避免误点
        final long totalCents = StoreRowMapper.toCents(product.getPrice() * count);
        int confirmed = JOptionPane.showConfirmDialog(this,
                "确认购买「" + product.getName() + "」× " + count + "，将扣款 "
                        + StoreRowMapper.formatYuan(totalCents) + " 元？",
                "确认购买", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirmed != JOptionPane.OK_OPTION) {
            return;
        }
        final String productId = product.getProductId();
        runRequest("正在提交购买请求…", service -> service.purchase(session.getToken(), productId, count),
                response -> {
                    if (!isSuccessful(response)) {
                        // 购买失败最常见的原因是并发下库存或余额已变，刷新后才能让用户看到真实数字
                        SwingUtilities.invokeLater(this::loadProducts);
                        SwingUtilities.invokeLater(this::loadBalance);
                        return;
                    }
                    showStatus("购买成功，正在刷新商品与余额…", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadProducts);
                    SwingUtilities.invokeLater(this::loadBalance);
                });
    }

    private void addSelectedToCart() {
        final Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        if (!product.isActive()) {
            showStatus("「" + product.getName() + "」已下架，无法加入购物车", VCampusTheme.DANGER);
            return;
        }
        final int count = ((Integer) quantity.getValue()).intValue();
        if (product.getStock() < count) {
            showStatus("「" + product.getName() + "」库存仅剩 " + product.getStock() + " 件，请调整数量",
                    VCampusTheme.DANGER);
            return;
        }
        final String productId = product.getProductId();
        runRequest("正在加入购物车…", service -> service.addToCart(session.getToken(), productId, count),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已将 " + count + " 件「" + product.getName() + "」加入购物车", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    private void showSelectedDetail() {
        Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        JOptionPane.showMessageDialog(this, detailText(product), "商品详情", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String detailText(Product product) {
        return "名称：" + product.getName()
                + "\n商品号：" + product.getProductId()
                + "\n类别：" + product.getCategory()
                + "\n单价：" + StoreRowMapper.formatYuan(StoreRowMapper.toCents(product.getPrice())) + " 元"
                + "\n库存：" + product.getStock()
                + "\n状态：" + (product.isActive() ? "在售" : "已下架")
                + "\n说明：" + (product.getDescription() == null || product.getDescription().isEmpty()
                        ? "（无）"
                        : product.getDescription());
    }

    private void showAddProductDialog() {
        final StoreProductForm form = StoreProductForm.showAdd(this);
        if (form == null) {
            return;
        }
        runRequest("正在新增商品…",
                service -> service.addProduct(session.getToken(), form.getName(), form.getPrice(), form.getStock(),
                        form.getDescription(), form.getCategory()),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("新增商品「" + form.getName() + "」成功", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadProducts);
                });
    }

    private void showEditProductDialog() {
        final Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择要编辑的商品", VCampusTheme.DANGER);
            return;
        }
        final StoreProductForm form = StoreProductForm.showEdit(this, product);
        if (form == null) {
            return;
        }
        final String productId = product.getProductId();
        runRequest("正在更新商品…",
                service -> service.updateProduct(session.getToken(), productId, form.getName(), form.getPrice(),
                        form.getDescription(), form.getCategory(), product.getVersion()),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已更新「" + form.getName() + "」", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadProducts);
                });
    }

    private void restockSelected() {
        final Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择要补货的商品", VCampusTheme.DANGER);
            return;
        }
        String input = JOptionPane.showInputDialog(this,
                "「" + product.getName() + "」当前库存 " + product.getStock() + " 件，请输入补货数量：",
                "补货", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(input.trim());
        } catch (NumberFormatException invalidNumber) {
            showStatus("补货数量必须是整数", VCampusTheme.DANGER);
            return;
        }
        if (parsed <= 0) {
            showStatus("补货数量必须大于 0", VCampusTheme.DANGER);
            return;
        }
        final int additional = parsed;
        final String productId = product.getProductId();
        runRequest("正在补货…", service -> service.restock(session.getToken(), productId, additional), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("已为「" + product.getName() + "」补货 " + additional + " 件", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadProducts);
        });
    }

    private void deactivateSelected() {
        final Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择要下架的商品", VCampusTheme.DANGER);
            return;
        }
        if (!product.isActive()) {
            showStatus("「" + product.getName() + "」已经下架，无需重复操作", VCampusTheme.DANGER);
            return;
        }
        // 下架会中断售卖、商品从买家与管理员列表消失（可用「重新上架」凭编号恢复），属破坏性操作，必须二次确认
        if (JOptionPane.showConfirmDialog(this,
                "确定下架「" + product.getName() + "」？\n下架后买家将无法购买，已存在的购物车条目也会标记为失效。",
                "下架确认", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        final String productId = product.getProductId();
        runRequest("正在下架商品…", service -> service.deactivateProduct(session.getToken(), productId), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("已下架「" + product.getName() + "」", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadProducts);
        });
    }

    private void reactivateSelected() {
        // 已下架商品不在商品表内（listProducts 只返回在售商品、且该契约被测试锁定），
        // 故重新上架改为凭商品编号定位目标，与补货的输入框范式一致；恢复后刷新列表即重新出现在表中
        String input = JOptionPane.showInputDialog(this,
                "请输入要重新上架的商品编号：\n（已下架商品不显示在列表中，需凭编号恢复）",
                "重新上架", JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;// 用户取消
        }
        final String productId = input.trim();
        if (productId.isEmpty()) {
            showStatus("商品编号不能为空", VCampusTheme.DANGER);
            return;
        }
        runRequest("正在重新上架…", service -> service.reactivateProduct(session.getToken(), productId), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("已重新上架商品 " + productId, VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadProducts);
        });
    }

    private void loadCart() {
        // 购物车一律走明细接口（服务端读取时联表），才能拿到商品名、单价与小计
        runRequest("正在查询购物车…", service -> service.cartDetail(session.getToken()), this::showCart);
    }

    private void showCart(Message response) {
        if (!isSuccessful(response)) {
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的购物车数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> lines = (List<?>) response.getPayload();
        List<CartLine> parsed = new ArrayList<CartLine>();
        for (Object item : lines) {
            if (!(item instanceof CartLine)) {
                showStatus("服务器返回的购物车数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            parsed.add((CartLine) item);
        }
        cartLines.clear();
        cartLines.addAll(parsed);
        List<Object[]> rows = new ArrayList<Object[]>();
        long total = 0L;
        for (CartLine line : cartLines) {
            rows.add(StoreRowMapper.cartRow(line));
            // 合计只累加 subtotalCents：它与结账实扣同式，用单价×数量重算会因四舍五入差一两分
            total += line.getSubtotalCents();
        }
        cartTotalCents = total;
        cartModel.replaceRows(rows);
        cartTotalLabel.setText("合计：" + StoreRowMapper.formatYuan(cartTotalCents) + " 元");
        showStatus("已显示购物车，共 " + cartLines.size() + " 条", VCampusTheme.SUCCESS);
    }

    private CartLine selectedCartLine() {
        int selected = cartTable.getSelectedRow();
        if (selected < 0) {
            return null;
        }
        int modelRow = cartTable.convertRowIndexToModel(selected);
        if (modelRow < 0 || modelRow >= cartLines.size()) {
            return null;
        }
        return cartLines.get(modelRow);
    }

    private void updateSelectedCartQuantity() {
        final CartLine line = selectedCartLine();
        if (line == null) {
            showStatus("请先在购物车表中选择一个条目", VCampusTheme.DANGER);
            return;
        }
        if (!line.isActive()) {
            showStatus("「" + line.getProductName() + "」已下架，请移除该条目", VCampusTheme.DANGER);
            return;
        }
        String input = JOptionPane.showInputDialog(this,
                "「" + line.getProductName() + "」当前数量 " + line.getQuantity() + "，请输入新数量：",
                "修改数量", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(input.trim());
        } catch (NumberFormatException invalidNumber) {
            showStatus("数量必须是整数", VCampusTheme.DANGER);
            return;
        }
        if (parsed <= 0) {
            showStatus("数量必须大于 0，若要删除请点「移除选中」", VCampusTheme.DANGER);
            return;
        }
        final int newQuantity = parsed;
        final String cartItemId = line.getCartItemId();
        runRequest("正在修改数量…", service -> service.updateCart(session.getToken(), cartItemId, newQuantity),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已将「" + line.getProductName() + "」数量改为 " + newQuantity, VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    private void removeSelectedCartItem() {
        final CartLine line = selectedCartLine();
        if (line == null) {
            showStatus("请先在购物车表中选择一个条目", VCampusTheme.DANGER);
            return;
        }
        final String cartItemId = line.getCartItemId();
        runRequest("正在移除购物车条目…", service -> service.removeFromCart(session.getToken(), cartItemId),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已移除「" + line.getProductName() + "」", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    private void checkoutCart() {
        if (cartLines.isEmpty()) {
            // 本地没明细（可能尚未刷新过购物车），直接交服务端裁决，避免误报“购物车是空的”
            submitCheckout();
            return;
        }
        int inactive = 0;
        for (CartLine line : cartLines) {
            if (!line.isActive()) {
                inactive++;
            }
        }
        if (inactive > 0) {
            showStatus("购物车里有 " + inactive + " 条已下架商品，请先移除再结算", VCampusTheme.DANGER);
            return;
        }
        // 结算会真实扣款，先把件数与合计摆出来让用户确认；真正裁决仍在服务端
        if (JOptionPane.showConfirmDialog(this,
                "本次结算共 " + cartLines.size() + " 条、合计 " + StoreRowMapper.formatYuan(cartTotalCents)
                        + " 元，确认从校园钱包扣款？",
                "结算确认", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        submitCheckout();
    }

    private void submitCheckout() {
        runRequest("正在结算购物车…", service -> service.checkout(session.getToken()), response -> {
            if (!isSuccessful(response)) {
                // 结算失败会触发服务端补偿回滚，刷新后才能让用户看到真实的库存与余额
                SwingUtilities.invokeLater(this::loadCart);
                SwingUtilities.invokeLater(this::loadBalance);
                return;
            }
            showStatus("结算成功，正在刷新…", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadCart);
            SwingUtilities.invokeLater(this::loadBalance);
            SwingUtilities.invokeLater(this::loadProducts);
            SwingUtilities.invokeLater(this::loadOrders);
        });
    }

    private void loadOrders() {
        runRequest("正在查询我的订单…", service -> service.ordersFor(session.getToken()), this::showOrders);
    }

    private void showOrders(Message response) {
        List<Object[]> rows = orderRows(response, false);
        if (rows == null) {
            return;
        }
        orderModel.replaceRows(rows);
        showStatus("已显示我的订单，共 " + rows.size() + " 条", VCampusTheme.SUCCESS);
    }

    private void loadAllOrders() {
        runRequest("正在查询全部订单…", service -> service.allOrders(session.getToken()), this::showAllOrders);
    }

    private void showAllOrders(Message response) {
        List<Object[]> rows = orderRows(response, true);
        if (rows == null) {
            return;
        }
        allOrderModel.replaceRows(rows);
        showStatus("已显示全部订单，共 " + rows.size() + " 条", VCampusTheme.SUCCESS);
    }

    /** 本人订单与全部订单共用解析逻辑，只有行构造多一列买家；返回 null 表示已报错。 */
    private List<Object[]> orderRows(Message response, boolean includeBuyer) {
        if (!isSuccessful(response)) {
            return null;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的订单数据格式不正确", VCampusTheme.DANGER);
            return null;
        }
        List<?> orders = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : orders) {
            if (!(item instanceof Order)) {
                showStatus("服务器返回的订单数据格式不正确", VCampusTheme.DANGER);
                return null;
            }
            Order order = (Order) item;
            rows.add(includeBuyer ? StoreRowMapper.allOrderRow(order) : StoreRowMapper.orderRow(order));
        }
        return rows;
    }

    private void loadBalance() {
        runRequest("正在查询余额…", service -> service.balance(session.getToken()), this::showBalance);
    }

    private void showBalance(Message response) {
        // 余额只是辅助信息，查询失败不抢状态栏（留给主操作），只把标签复位
        if (response.getStatusCode() == StatusCode.OK && response.getPayload() instanceof Number) {
            long cents = ((Number) response.getPayload()).longValue();
            balanceLabel.setText("余额：" + StoreRowMapper.formatYuan(cents) + " 元");
        } else {
            balanceLabel.setText("余额：--.-- 元");
        }
    }

    private void loadLedger() {
        runRequest("正在查询钱包流水…", service -> service.ledger(session.getToken()), this::showLedger);
    }

    private void showLedger(Message response) {
        if (!isSuccessful(response)) {
            return;
        }
        if (!(response.getPayload() instanceof List<?>)) {
            showStatus("服务器返回的流水数据格式不正确", VCampusTheme.DANGER);
            return;
        }
        List<?> entries = (List<?>) response.getPayload();
        List<Object[]> rows = new ArrayList<Object[]>();
        for (Object item : entries) {
            if (!(item instanceof WalletTransaction)) {
                showStatus("服务器返回的流水数据格式不正确", VCampusTheme.DANGER);
                return;
            }
            rows.add(StoreRowMapper.ledgerRow((WalletTransaction) item));
        }
        ledgerModel.replaceRows(rows);
        showStatus("已显示钱包流水，共 " + rows.size() + " 笔", VCampusTheme.SUCCESS);
    }

    private void promptRecharge() {
        String input = JOptionPane.showInputDialog(this, "请输入充值金额（元）：", "充值", JOptionPane.PLAIN_MESSAGE);
        if (input == null || input.trim().isEmpty()) {
            return;
        }
        long parsed;
        try {
            // 元转分只在输入边界做一次 Math.round，不让浮点误差进账本
            parsed = StoreRowMapper.toCents(Double.parseDouble(input.trim()));
        } catch (NumberFormatException invalidNumber) {
            showStatus("充值金额格式不正确", VCampusTheme.DANGER);
            return;
        }
        if (parsed <= 0) {
            showStatus("充值金额必须大于 0", VCampusTheme.DANGER);
            return;
        }
        final long cents = parsed;
        runRequest("正在充值…", service -> service.recharge(session.getToken(), cents), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("充值成功，已入账 " + StoreRowMapper.formatYuan(cents) + " 元", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadBalance);
            SwingUtilities.invokeLater(this::loadLedger);
        });
    }

    private void promptAdjustBalance() {
        String targetInput = JOptionPane.showInputDialog(this, "请输入目标用户编号：", "校正余额",
                JOptionPane.PLAIN_MESSAGE);
        if (targetInput == null || targetInput.trim().isEmpty()) {
            return;
        }
        String balanceInput = JOptionPane.showInputDialog(this, "请输入校正后的余额（元，绝对值）：", "校正余额",
                JOptionPane.PLAIN_MESSAGE);
        if (balanceInput == null || balanceInput.trim().isEmpty()) {
            return;
        }
        long parsed;
        try {
            parsed = StoreRowMapper.toCents(Double.parseDouble(balanceInput.trim()));
        } catch (NumberFormatException invalidNumber) {
            showStatus("余额金额格式不正确", VCampusTheme.DANGER);
            return;
        }
        if (parsed < 0) {
            showStatus("余额不能为负", VCampusTheme.DANGER);
            return;
        }
        final long cents = parsed;
        final String target = targetInput.trim();
        runRequest("正在校正余额…", service -> service.adjustBalance(session.getToken(), target, cents), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("已将 " + target + " 的余额校正为 " + StoreRowMapper.formatYuan(cents) + " 元",
                    VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadBalance);
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
                    // 解包 SwingWorker 的 ExecutionException，区分「网络故障」与「其它异常」，不再一律报“无法连接”
                    Throwable cause = failure instanceof ExecutionException && failure.getCause() != null
                            ? failure.getCause()
                            : failure;
                    if (cause instanceof SocketTimeoutException) {
                        showStatus("商店响应超时，请稍后重试", VCampusTheme.DANGER);
                    } else if (cause instanceof IOException) {
                        showStatus("无法连接商店服务器，请确认服务器已启动", VCampusTheme.DANGER);
                    } else {
                        showStatus(localFailureText(cause), VCampusTheme.DANGER);
                    }
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

    // done() 的本地异常（命令构造/解析等非网络故障）文案：一律中文，绝不把 cause.getMessage() 的内部英文甩给用户
    static String localFailureText(Throwable cause) {
        if (cause instanceof IllegalArgumentException) {
            return "提交的数据不完整或格式有误，请检查后重试";
        }
        return "商店请求失败，请稍后重试";
    }

    /** 统一响应守卫：成功返回 true，失败已顺手把原因写进状态栏，调用方直接 return 即可。 */
    private boolean isSuccessful(Message response) {
        if (response.getStatusCode() == StatusCode.OK) {
            return true;
        }
        if (response.getPayload() instanceof String) {
            showStatus((String) response.getPayload(), VCampusTheme.DANGER);
        } else {
            showStatus(statusMessage(response.getStatusCode()), VCampusTheme.DANGER);
        }
        return false;
    }

    private void updateButtonState() {
        boolean idle = !requestInProgress;
        searchButton.setEnabled(idle);
        hotButton.setEnabled(idle);
        purchaseButton.setEnabled(idle);
        addToCartButton.setEnabled(idle);
        detailButton.setEnabled(idle);
        refreshCartButton.setEnabled(idle);
        updateQuantityButton.setEnabled(idle);
        removeFromCartButton.setEnabled(idle);
        checkoutButton.setEnabled(idle);
        refreshOrdersButton.setEnabled(idle);
        rechargeButton.setEnabled(idle);
        refreshLedgerButton.setEnabled(idle);
        allOrdersButton.setEnabled(idle);
        addProductButton.setEnabled(manager && idle);
        editProductButton.setEnabled(manager && idle);
        restockButton.setEnabled(manager && idle);
        deactivateButton.setEnabled(manager && idle);
        adjustBalanceButton.setEnabled(manager && idle);
        keywordField.setEnabled(idle);
        categoryField.setEnabled(idle);
        quantity.setEnabled(idle);
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
    }

    static String statusMessage(StatusCode statusCode) {
        if (statusCode == StatusCode.BAD_REQUEST)
            return "请求数据不正确，请检查填写的数量或金额";
        if (statusCode == StatusCode.UNAUTHORIZED)
            return "登录状态已失效，请重新登录";
        if (statusCode == StatusCode.FORBIDDEN)
            return "当前账号没有执行该商店操作的权限";
        if (statusCode == StatusCode.NOT_FOUND)
            return "商品、订单或购物车条目不存在";
        if (statusCode == StatusCode.PAYMENT_REQUIRED)
            return "余额不足，请先充值";
        if (statusCode == StatusCode.CONFLICT)
            return "商品、库存或余额已发生变化，请刷新后重试";
        return "服务器处理商店请求失败";
    }

    /** 关键词只用于本地过滤已加载的商品，不额外发请求。 */
    private final class KeywordWatcher implements javax.swing.event.DocumentListener {
        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent event) {
            applyKeywordFilter();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent event) {
            applyKeywordFilter();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent event) {
            applyKeywordFilter();
        }
    }

    private interface StoreRequest {
        Message execute(RemoteStoreService service) throws IOException, ClassNotFoundException;
    }

    private interface ResponseHandler {
        void handle(Message response);
    }
}

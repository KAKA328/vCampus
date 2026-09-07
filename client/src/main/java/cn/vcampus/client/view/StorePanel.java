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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * 商店页面：商品浏览与筛选、直接购买、购物车结算、校园钱包与管理员商品维护。
 * 买家角色与管理员角色共用本面板，管理员专属入口按 manager 标记增删，真正拦截在服务端权限门槛，
 * 界面显隐只是 UX（对齐 LibraryPanel 的角色分化方式）。
 */
public final class StorePanel extends JPanel {
    private static final int HOT_PRODUCT_LIMIT = 10;
    private static final int MAX_QUANTITY = 999;
    // 类别下拉的“全部”占位项（与 category=null 等价）
    private static final String ALL_CATEGORIES = "全部类别";

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
    // 类别从下拉选（来源 = 各次加载结果里出现过的类别并集，免手输易错的裸文本）
    private final JComboBox<String> categoryBox = new JComboBox<String>();
    private final Set<String> knownCategories = new TreeSet<String>();
    private boolean updatingCategoryOptions;// 程序化刷新下拉选项时抑制 ActionListener 触发的重查
    private final JLabel status = new JLabel("输入关键词或类别查询商品，也可直接点击“查询商品”查看全部在售商品");
    private final JLabel balanceLabel = new JLabel("余额：--.-- 元");
    private final JLabel cartTotalLabel = new JLabel("合计：--.-- 元");
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, MAX_QUANTITY, 1));
    // 上次“数量选择器对准的商品”：切换选中到另一个商品时数量复位为 1（数量属于对当前商品的操作意图）
    private String quantityTargetProductId;

    // —— 商品目录视图形态：列表（表格）/ 方块（双列卡片）。默认：买家方块、管理员列表 ——
    private boolean cardViewVisible;// 是否处于方块视图（build() 内按角色设初值）
    private final CardLayout catalogCardLayout = new CardLayout();
    private final JPanel catalogCards = new JPanel(catalogCardLayout);// CENTER 双视图容器
    private final CardLayout catalogSouthLayout = new CardLayout();
    private final JPanel catalogSouth = new JPanel(catalogSouthLayout);// SOUTH：列表操作行 / 方块提示行
    private final JPanel cardHost = new JPanel(new GridLayout(0, 2, 14, 14));// 方块宿主（双列）
    private final JScrollPane cardScroller = new JScrollPane(cardHost);
    private JScrollPane listScroller;// 列表宿主（catalogPanel 内创建）
    private final JToggleButton viewModeButton = new JToggleButton("方块视图");// 显示“要切到的”目标形态
    private static final String LIST_VIEW = "list";
    private static final String CARD_VIEW = "cards";
    private static final String LIST_ACTIONS = "listActions";
    private static final String CARD_HINT = "cardHint";

    private final JButton searchButton = new JButton("查询商品");
    private final JButton hotButton = new JButton("热销 Top" + HOT_PRODUCT_LIMIT);
    // 含下架视图：学生/教师也可开启（浏览已下架陈列，不可购买——服务端购买/加购仍拒绝下架品）
    private final JButton inactiveButton = new JButton("显示已下架");
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
    private boolean inactiveViewVisible;// 管理端「含下架」视图开关；与热销视图互斥
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
        // 默认形态：买家看“方块”更适合浏览，管理员维护选“列表”更高效；都可一键切换
        cardViewVisible = !manager;
        add(header(), BorderLayout.NORTH);
        add(VCampusTheme.pageScroll(body()), BorderLayout.CENTER);

        searchButton.addActionListener(event -> loadProducts());
        hotButton.addActionListener(event -> toggleHotView());
        inactiveButton.addActionListener(event -> toggleInactiveView());
        viewModeButton.addActionListener(event -> setCardView(!cardViewVisible));
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
        // 类别下拉的切换即查询（程序化刷新选项期间被 updatingCategoryOptions 抑制，避免循环触发）
        categoryBox.addActionListener(event -> {
            if (!updatingCategoryOptions) {
                loadProducts();
            }
        });
        keywordField.getDocument().addDocumentListener(new KeywordWatcher());
        // 选中商品后按实际库存收窄数量上限；切到另一个商品时数量复位为默认 1，
        // 避免“给 A 选了 5 件、选 B 时数量框还带着 5”的误操作
        productTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            Product selected = selectedProduct();
            if (selected == null) {
                quantityTargetProductId = null;
            } else if (!selected.getProductId().equals(quantityTargetProductId)) {
                quantity.setValue(Integer.valueOf(1));
                quantityTargetProductId = selected.getProductId();
            }
            syncQuantityLimit();
        });

        updateButtonState();
        loadBalance();
        // 进入商店页自动加载一次商品列表，否则列表保持空白，必须手动点查询/切换视图才出现
        loadProducts();
    }

    /**
     * 页面主体：把功能页签包进可垂直滚动的 {@link ScrollablePagePanel}，窗口高度不足时整页滚动，
     * 与图书馆/选课等面板共用统一的页面级滚动结构（对齐 PR #41 的前端刷新）。
     */
    private JPanel body() {
        JPanel panel = new ScrollablePagePanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        JTabbedPane tabs = tabs();
        // 让目录区占主窗口更大比例：页面高度不足时仍由外层页级滚动兜底（FeaturePanelScrollTest 结构不变）
        tabs.setPreferredSize(new Dimension(0, 600));
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
        categoryBox.setPreferredSize(new Dimension(130, 26));
        search.add(categoryBox);
        themeSecondary(searchButton);
        themeSecondary(hotButton);
        search.add(searchButton);
        search.add(hotButton);
        // 含下架开关对学生也开放：看得到不等于买得到（服务端购买/加购仍拒下架品）
        themeSecondary(inactiveButton);
        search.add(inactiveButton);
        themeSecondary(viewModeButton);
        search.add(viewModeButton);
        viewModeButton.setToolTipText("在“方块（双列卡片）/ 列表（表格）”两种展示间切换");

        configureTable(productTable);
        applyMoneyColumns(productTable, MoneyCellRenderer.MoneyFormat.YUAN, 3);
        styleProductCatalog(productTable);

        // CENTER 双视图：列表宿主与方块宿主（FeaturePanelScrollTest 只锁外层页级滚动结构，内部可换）
        listScroller = new JScrollPane(productTable);
        cardHost.setBackground(VCampusTheme.BACKGROUND);
        cardScroller.setBorder(BorderFactory.createLineBorder(VCampusTheme.BORDER));
        cardScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        catalogCards.add(listScroller, LIST_VIEW);
        catalogCards.add(cardScroller, CARD_VIEW);
        catalogCards.setOpaque(false);

        // SOUTH-列表：选中行操作（数量/购买/加购/详情 + 管理员维护）
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        themePrimary(purchaseButton);
        themeSecondary(addToCartButton);
        themeSecondary(detailButton);
        actions.add(new JLabel("数量"));
        actions.add(quantity);
        actions.add(purchaseButton);
        actions.add(addToCartButton);
        actions.add(detailButton);
        if (manager) {
            themeSecondary(addProductButton);
            themeSecondary(editProductButton);
            themeSecondary(restockButton);
            themeSecondary(deactivateButton);
            themeSecondary(reactivateButton);
            actions.add(addProductButton);
            actions.add(editProductButton);
            actions.add(restockButton);
            actions.add(deactivateButton);
            actions.add(reactivateButton);
        }
        // SOUTH-方块：提示行（浏览选购为主；维护操作在每张卡片上，编辑维护建议切列表）
        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        hint.setOpaque(false);
        JLabel hintLabel = new JLabel("方块视图用于浏览选购；需要“下架/补货/编辑/新增”等管理操作时，请点上方切回“列表视图”。");
        hintLabel.setForeground(VCampusTheme.MUTED);
        hint.add(hintLabel);

        catalogSouth.add(actions, LIST_ACTIONS);
        catalogSouth.add(hint, CARD_HINT);
        catalogSouth.setOpaque(false);

        panel.add(search, BorderLayout.NORTH);
        panel.add(catalogCards, BorderLayout.CENTER);
        panel.add(catalogSouth, BorderLayout.SOUTH);

        applyViewMode();// 按默认形态显示一次（买家方块/管理员列表）
        return panel;
    }

    private JPanel cartPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        VCampusTheme.panel(panel);
        configureTable(cartTable);
        applyMoneyColumns(cartTable, MoneyCellRenderer.MoneyFormat.YUAN, 2, 4);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        themeSecondary(refreshCartButton);
        themeSecondary(updateQuantityButton);
        themeSecondary(removeFromCartButton);
        themePrimary(checkoutButton);
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
        themeSecondary(refreshOrdersButton);
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
        themePrimary(rechargeButton);
        themeSecondary(refreshLedgerButton);
        balanceBar.add(rechargeButton);
        balanceBar.add(refreshLedgerButton);
        if (manager) {
            themeSecondary(adjustBalanceButton);
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
        themeSecondary(allOrdersButton);
        actions.add(allOrdersButton);

        panel.add(new JScrollPane(allOrderTable), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    /** 主题按钮样式 + 去“方角描边”（商店面板专属，圆角底由主题 ReadableButtonUI 自绘）。 */
    private static void themePrimary(AbstractButton button) {
        VCampusTheme.primaryButton(button);
        softenBorder(button);
    }

    private static void themeSecondary(AbstractButton button) {
        VCampusTheme.secondaryButton(button);
        softenBorder(button);
    }

    /** 把主题设置的外框方线换为内边距留白，按钮观感由方角→圆角扁平（底色仍由主题色板控制）。 */
    private static void softenBorder(AbstractButton button) {
        button.setBorder(VCampusTheme.padding(9, 18, 9, 18));
    }

    private static void configureTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(40);
        // 开启列排序后，视图行号与模型行号不再相同，取值一律经 convertRowIndexToModel 换算
        table.setAutoCreateRowSorter(true);
        table.setShowVerticalLines(false);
        table.setGridColor(VCampusTheme.BORDER);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(VCampusTheme.font(Font.BOLD, 13));
        // 交替深浅行（斑马纹）：未自定义渲染器的文本列也一致
        table.setDefaultRenderer(Object.class, new VCampusTheme.ReadableTableCellRenderer());
    }

    private static void applyMoneyColumns(JTable table, MoneyCellRenderer.MoneyFormat format, int... columns) {
        MoneyCellRenderer renderer = new MoneyCellRenderer(format);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(renderer);
        }
    }

    /**
     * 商品目录商用化排版：列宽、重点价格、状态胶囊、类别/说明弱化但可读。
     * 只改展示（cell renderer / 列宽），不改模型数据与既有排序/过滤/取行逻辑。
     */
    private static void styleProductCatalog(JTable table) {
        table.getColumnModel().getColumn(0).setPreferredWidth(96);// 商品号
        table.getColumnModel().getColumn(1).setPreferredWidth(190);// 名称
        table.getColumnModel().getColumn(2).setPreferredWidth(110);// 类别
        table.getColumnModel().getColumn(3).setPreferredWidth(110);// 价格（强调）
        table.getColumnModel().getColumn(4).setPreferredWidth(80);// 库存
        table.getColumnModel().getColumn(5).setPreferredWidth(90);// 状态（圆角胶囊）
        table.getColumnModel().getColumn(6).setPreferredWidth(280);// 说明（悬停看全文）
        table.getColumnModel().getColumn(0).setCellRenderer(new CatalogTextRenderer(
                VCampusTheme.font(Font.PLAIN, 12), VCampusTheme.MUTED, false));
        table.getColumnModel().getColumn(1).setCellRenderer(new CatalogTextRenderer(
                VCampusTheme.font(Font.BOLD, 13), VCampusTheme.PRIMARY_DARK, false));
        table.getColumnModel().getColumn(2).setCellRenderer(new CatalogTextRenderer(
                VCampusTheme.font(Font.PLAIN, 12), new Color(71, 85, 105), false));
        table.getColumnModel().getColumn(3).setCellRenderer(
                new MoneyCellRenderer(MoneyCellRenderer.MoneyFormat.YUAN, true));
        table.getColumnModel().getColumn(4).setCellRenderer(new StockRenderer());
        table.getColumnModel().getColumn(5).setCellRenderer(new RoundedChipCellRenderer());
        table.getColumnModel().getColumn(6).setCellRenderer(new TooltipTextRenderer(VCampusTheme.MUTED));
    }

    private void loadProducts() {
        loadProducts(true);
    }

    /**
     * 加载商品列表。announce=true 时（用户主动操作）成功后在状态栏播报条数；
     * announce=false 时（购买/结算失败后的后台静默刷新）只更新表格数据，
     * 不覆盖状态栏里的错误提示——否则「余额不足」等提示会被刷新成功文案顶掉、一闪即逝。
     */
    private void loadProducts(boolean announce) {
        final String category = selectedCategory();
        // 含下架视图对所有角色开放（服务端已放宽为 STORE_READ）：买家可浏览下架陈列，购买仍被拒
        final boolean includeInactive = inactiveViewVisible;
        hotViewVisible = false;
        hotButton.setText("热销 Top" + HOT_PRODUCT_LIMIT);
        String suffix = includeInactive ? "（含已下架）" : "";
        runRequest((category.isEmpty() ? "正在查询商品" : "正在查询「" + category + "」类商品") + suffix + "…",
                service -> service.listProducts(session.getToken(),
                        category.isEmpty() ? null : category, includeInactive),
                response -> showProducts(response, announce));
    }

    /** 类别下拉当前值：选中“全部类别”或暂无选择时返回空串（等价于 category=null）。 */
    private String selectedCategory() {
        Object selected = categoryBox.getSelectedItem();
        if (selected == null) {
            return "";
        }
        String value = String.valueOf(selected);
        return ALL_CATEGORIES.equals(value) ? "" : value;
    }

    /** 用各次加载结果里出现过的类别并集重建下拉，保留当前选择；程序化刷新期间抑制 ActionListener。 */
    private void refreshCategoryOptions(List<Product> parsed) {
        for (Product product : parsed) {
            if (product.getCategory() != null) {
                knownCategories.add(product.getCategory());
            }
        }
        updatingCategoryOptions = true;
        try {
            Object current = categoryBox.getSelectedItem();
            categoryBox.removeAllItems();
            categoryBox.addItem(ALL_CATEGORIES);
            for (String category : knownCategories) {
                categoryBox.addItem(category);
            }
            categoryBox.setSelectedItem(current != null ? current : ALL_CATEGORIES);
        } finally {
            updatingCategoryOptions = false;
        }
    }

    /** 管理端视图切换：商品列表并入/移出已下架商品；进入时顺带退出热销视图（同一张表两种视图互斥）。 */
    private void toggleInactiveView() {
        inactiveViewVisible = !inactiveViewVisible;
        inactiveButton.setText(inactiveViewVisible ? "隐藏已下架" : "显示已下架");
        loadProducts();
    }

    /** 列表/方块视图切换入口。 */
    private void setCardView(boolean cards) {
        if (cards == cardViewVisible) {
            return;
        }
        cardViewVisible = cards;
        applyViewMode();
    }

    /** 依当前形态切换 CENTER/SOUTH 卡片与按钮文案；进入方块视图时重建卡片。 */
    private void applyViewMode() {
        if (cardViewVisible) {
            catalogCardLayout.show(catalogCards, CARD_VIEW);
            catalogSouthLayout.show(catalogSouth, CARD_HINT);
            viewModeButton.setText("列表视图");
            rebuildCardView();
        } else {
            catalogCardLayout.show(catalogCards, LIST_VIEW);
            catalogSouthLayout.show(catalogSouth, LIST_ACTIONS);
            viewModeButton.setText("方块视图");
        }
        viewModeButton.setSelected(cardViewVisible);
    }

    /** 用当前 visibleProducts 重建双列卡片网格（过滤/加载后数据变化时调用）。 */
    private void rebuildCardView() {
        cardHost.removeAll();
        for (Product product : visibleProducts) {
            cardHost.add(new ProductCard(product));
        }
        cardHost.revalidate();
        cardHost.repaint();
    }

    /** 点击卡片时把对应行选中（与表格选中联动，表格开列排序时经 rowSorter 换算回视图行）。 */
    private void selectProductRow(Product product) {
        int modelIndex = visibleProducts.indexOf(product);
        if (modelIndex < 0) {
            return;
        }
        int viewIndex = modelIndex;
        if (productTable.getRowSorter() != null) {
            try {
                viewIndex = productTable.getRowSorter().convertRowIndexToView(modelIndex);
            } catch (IndexOutOfBoundsException ignored) {
                return;
            }
        }
        productTable.setRowSelectionInterval(viewIndex, viewIndex);
    }

    /** 热销排行不占页签，做成商品表的视图切换，与图书馆「本人/全部借阅记录」的切换方式一致。 */
    private void toggleHotView() {
        if (hotViewVisible) {
            loadProducts();
            return;
        }
        // 进入热销视图时退出含下架视图（互斥），按钮文案同步复位
        inactiveViewVisible = false;
        inactiveButton.setText("显示已下架");
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
        showProducts(response, true);
    }

    /** announce=false（后台静默刷新）时不播报成功条数，避免覆盖状态栏里尚未消除的错误提示。 */
    private void showProducts(Message response, boolean announce) {
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
        // 类别下拉始终以“出现过的类别并集”为准（含下架/过滤也不丢已见类别）
        refreshCategoryOptions(parsed);
        if (!announce) {
            return;
        }
        String summary;
        if (hotViewVisible) {
            summary = "已显示热销商品，共 " + parsed.size() + " 个";
        } else if (inactiveViewVisible) {
            int inactiveCount = 0;
            for (Product product : parsed) {
                if (!product.isActive()) {
                    inactiveCount++;
                }
            }
            summary = "已显示商品，共 " + parsed.size() + " 个（其中 " + inactiveCount + " 个已下架）";
        } else {
            summary = "已显示商品，共 " + parsed.size() + " 个";
        }
        showStatus(summary, VCampusTheme.SUCCESS);
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
        if (cardViewVisible) {
            rebuildCardView();
        }
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

    /** 加购/购买成功后数量回到默认 1（下次操从 1 起，避免把上一个商品的数量误带进来）。 */
    private void resetQuantityToDefault() {
        quantity.setValue(Integer.valueOf(1));
    }

    private void purchaseSelected() {
        Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        purchaseProduct(product, ((Integer) quantity.getValue()).intValue());
    }

    /** 对“指定商品+数量”发起购买（列表选中行与方块卡片共用；含下架/库存守卫与扣款确认）。 */
    private void purchaseProduct(final Product product, final int count) {
        if (!product.isActive()) {
            showStatus("「" + product.getName() + "」已下架，无法购买", VCampusTheme.DANGER);
            return;
        }
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
        final String productName = product.getName();
        runRequest("正在提交购买请求…", service -> service.purchase(session.getToken(), productId, count),
                response -> {
                    if (!isSuccessful(response)) {
                        // 失败（余额不足/库存冲突等）提示已进状态栏；后台静默刷新商品与余额，
                        // 不覆盖错误提示——否则「余额不足」会被刷新成功文案顶掉、一闪即逝
                        SwingUtilities.invokeLater(() -> loadProducts(false));
                        SwingUtilities.invokeLater(this::loadBalance);
                        return;
                    }
                    showStatus("购买成功「" + productName + "」× " + count + "，正在刷新…", VCampusTheme.SUCCESS);
                    resetQuantityToDefault();
                    SwingUtilities.invokeLater(this::loadProducts);
                    SwingUtilities.invokeLater(this::loadBalance);
                });
    }

    private void addSelectedToCart() {
        Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        addProductToCart(product, ((Integer) quantity.getValue()).intValue());
    }

    /** 对“指定商品+数量”加入购物车（列表选中行与方块卡片共用；含下架/库存守卫）。 */
    private void addProductToCart(final Product product, final int count) {
        if (!product.isActive()) {
            showStatus("「" + product.getName() + "」已下架，无法加入购物车", VCampusTheme.DANGER);
            return;
        }
        if (product.getStock() < count) {
            showStatus("「" + product.getName() + "」库存仅剩 " + product.getStock() + " 件，请调整数量",
                    VCampusTheme.DANGER);
            return;
        }
        final String productId = product.getProductId();
        final String productName = product.getName();
        runRequest("正在加入购物车…", service -> service.addToCart(session.getToken(), productId, count),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已将 " + count + " 件「" + productName + "」加入购物车", VCampusTheme.SUCCESS);
                    resetQuantityToDefault();
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    private void showSelectedDetail() {
        Product product = selectedProduct();
        if (product == null) {
            showStatus("请先在商品表中选择一个商品", VCampusTheme.DANGER);
            return;
        }
        showProductDetail(product);
    }

    /** 展示商品详情对话框（列表“详情”按钮与方块卡片共用）。 */
    private void showProductDetail(Product product) {
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
        // 行内优先：含下架视图中选中了已下架商品 → 名称与编号都可见，确认后直接恢复，无需盲输编号
        final Product selected = selectedProduct();
        if (selected != null && !selected.isActive()) {
            if (JOptionPane.showConfirmDialog(this,
                    "确认重新上架「" + selected.getName() + "」（编号 " + selected.getProductId() + "）？",
                    "重新上架确认", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            final String productId = selected.getProductId();
            final String productName = selected.getName();
            runRequest("正在重新上架…", service -> service.reactivateProduct(session.getToken(), productId),
                    response -> {
                        if (!isSuccessful(response)) {
                            return;
                        }
                        showStatus("已重新上架「" + productName + "」", VCampusTheme.SUCCESS);
                        SwingUtilities.invokeLater(this::loadProducts);
                    });
            return;
        }
        // 兜底：未打开含下架视图或选中的是已下架以外的行时，保留原「凭编号输入」用法
        String hint = selected == null
                ? "请输入要重新上架的商品编号：\n（提示：可先点「显示已下架」在列表中选中商品直接恢复）"
                : "当前选中商品在售中；请输入要重新上架的商品编号：";
        String input = JOptionPane.showInputDialog(this, hint,
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
        loadCart(true);
    }

    /** announce=false（结算失败后的后台静默刷新）只更新表格，不覆盖状态栏错误提示。 */
    private void loadCart(boolean announce) {
        // 购物车一律走明细接口（服务端读取时联表），才能拿到商品名、单价与小计
        runRequest("正在查询购物车…", service -> service.cartDetail(session.getToken()),
                response -> showCart(response, announce));
    }

    private void showCart(Message response) {
        showCart(response, true);
    }

    private void showCart(Message response, boolean announce) {
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
        if (announce) {
            showStatus("已显示购物车，共 " + cartLines.size() + " 条", VCampusTheme.SUCCESS);
        }
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
                // 结算失败会触发服务端补偿回滚；静默刷新购物车与余额（不覆盖错误提示，避免一闪即逝）
                SwingUtilities.invokeLater(() -> loadCart(false));
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
        inactiveButton.setEnabled(idle);
        viewModeButton.setEnabled(idle);
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
        reactivateButton.setEnabled(manager && idle);
        adjustBalanceButton.setEnabled(manager && idle);
        keywordField.setEnabled(idle);
        categoryBox.setEnabled(idle);
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

    /** 双列方块视图中的商品卡片：名称/价格/类别与状态胶囊/库存/说明 + 独立数量与操作按钮。 */
    private final class ProductCard extends JPanel {
        private final Product product;
        private final JSpinner cardQuantity;
        private final JButton cartButton = new JButton("加入购物车");
        private final JButton buyButton = new JButton("立即购买");
        private final JButton moreButton = new JButton("详情");

        ProductCard(final Product product) {
            this.product = product;
            boolean active = product.isActive();
            setLayout(new BorderLayout(0, 8));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createLineBorder(VCampusTheme.BORDER));
            // 点击卡片=选中对应表格行，与“列表”操作联动
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    selectProductRow(product);
                }
            });

            JPanel head = new JPanel(new BorderLayout(6, 0));
            head.setOpaque(false);
            JLabel nameLabel = new JLabel(product.getName());
            nameLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
            nameLabel.setForeground(VCampusTheme.PRIMARY_DARK);
            nameLabel.setToolTipText(product.getName());
            head.add(nameLabel, BorderLayout.CENTER);
            JLabel priceLabel = new JLabel(StoreRowMapper.formatYuan(StoreRowMapper.toCents(product.getPrice())) + " 元");
            priceLabel.setFont(VCampusTheme.font(Font.BOLD, 17));
            priceLabel.setForeground(VCampusTheme.PRIMARY);
            head.add(priceLabel, BorderLayout.EAST);

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            chips.setOpaque(false);
            chips.add(new ChipLabel(product.getCategory(), new Color(30, 64, 175)));
            chips.add(new ChipLabel(active ? "在售" : "已下架",
                    active ? VCampusTheme.SUCCESS : VCampusTheme.DANGER));
            JLabel stockLabel = new JLabel(active ? "库存 " + product.getStock() : "已下架，不可购买");
            stockLabel.setFont(VCampusTheme.font(Font.PLAIN, 12));
            stockLabel.setForeground(active && product.getStock() <= 0 ? VCampusTheme.DANGER
                    : (active && product.getStock() <= 5 ? new Color(202, 138, 4) : VCampusTheme.MUTED));
            chips.add(stockLabel);

            JPanel middle = new JPanel(new BorderLayout(0, 4));
            middle.setOpaque(false);
            middle.add(chips, BorderLayout.NORTH);
            String description = product.getDescription();
            if (description != null && !description.isEmpty()) {
                JLabel desc = new JLabel(truncate(description, 48));
                desc.setFont(VCampusTheme.font(Font.PLAIN, 12));
                desc.setForeground(VCampusTheme.MUTED);
                desc.setToolTipText(description);
                middle.add(desc, BorderLayout.CENTER);
            }

            int maxQty = Math.max(1, Math.min(active ? product.getStock() : 1, MAX_QUANTITY));
            cardQuantity = new JSpinner(new SpinnerNumberModel(1, 1, maxQty, 1));

            JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            foot.setOpaque(false);
            if (active) {
                foot.add(new JLabel("数量"));
                foot.add(cardQuantity);
            }
            JButton[] operations = active
                    ? new JButton[] { cartButton, buyButton, moreButton }
                    : new JButton[] { moreButton };
            for (JButton operation : operations) {
                styleCardButton(operation);
                foot.add(operation);
            }
            if (!active) {
                cartButton.setEnabled(false);
                buyButton.setEnabled(false);
            }
            cartButton.addActionListener(event -> addProductToCart(product, qty()));
            buyButton.addActionListener(event -> purchaseProduct(product, qty()));
            moreButton.addActionListener(event -> showProductDetail(product));

            add(head, BorderLayout.NORTH);
            add(middle, BorderLayout.CENTER);
            add(foot, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(430, 150));
        }

        private int qty() {
            return ((Integer) cardQuantity.getValue()).intValue();
        }
    }

    /** 卡片按钮：比行内按钮更小更紧凑（仍走主题圆角底）。 */
    private static void styleCardButton(JButton button) {
        themeSecondary(button);
        button.setFont(VCampusTheme.font(Font.BOLD, 12));
        button.setBorder(VCampusTheme.padding(6, 14, 6, 14));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 目录通用文本渲染：字号/颜色按列定制；未选中行用定制前景色，选中行保留系统选中配色。 */
    private static final class CatalogTextRenderer extends DefaultTableCellRenderer {
        private final Font font;
        private final Color color;
        private final boolean center;

        CatalogTextRenderer(Font font, Color color, boolean center) {
            this.font = font;
            this.color = color;
            this.center = center;
            setHorizontalAlignment(center ? SwingConstants.CENTER : SwingConstants.LEFT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(font);
            setText(value == null ? "" : String.valueOf(value));
            if (!isSelected) {
                setForeground(color);
                setOpaque(true);
                setBackground(row % 2 == 0 ? VCampusTheme.PANEL : VCampusTheme.TABLE_STRIPE);
            }
            return this;
        }
    }

    /** 说明列渲染：正文弱化；内容超出单元格时悬停显示全文 tooltip，不再把长文本当“程序员表”平铺。 */
    private static final class TooltipTextRenderer extends DefaultTableCellRenderer {
        private final Color color;

        TooltipTextRenderer(Color color) {
            this.color = color;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(VCampusTheme.font(Font.PLAIN, 12));
            setText(value == null ? "" : String.valueOf(value));
            if (!isSelected) {
                setForeground(color);
                setOpaque(true);
                setBackground(row % 2 == 0 ? VCampusTheme.PANEL : VCampusTheme.TABLE_STRIPE);
            }
            setToolTipText((value == null || String.valueOf(value).isEmpty())
                    ? null : String.valueOf(value));
            return this;
        }
    }

    /** 库存列：<=0 红字“缺货”、<=5 琥珀提醒、其余默认；居中。 */
    private static final class StockRenderer extends DefaultTableCellRenderer {
        private static final Color LOW_STOCK = new Color(202, 138, 4);

        StockRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int stock = value instanceof Number ? ((Number) value).intValue() : 0;
            if (!isSelected) {
                setOpaque(true);
                setBackground(row % 2 == 0 ? VCampusTheme.PANEL : VCampusTheme.TABLE_STRIPE);
                if (stock <= 0) {
                    setText("缺货");
                    setForeground(VCampusTheme.DANGER);
                    setFont(VCampusTheme.font(Font.BOLD, 12));
                } else if (stock <= 5) {
                    setForeground(LOW_STOCK);
                    setFont(VCampusTheme.font(Font.BOLD, 12));
                }
            }
            return this;
        }
    }

    /** 状态胶囊单元格：真正的圆角小胶囊（在售=绿、已下架=红），不再是整格填色。 */
    private static final class RoundedChipCellRenderer implements TableCellRenderer {
        private static final Color OFF_SHELF = VCampusTheme.DANGER;
        private final ChipLabel chip = new ChipLabel("", VCampusTheme.SUCCESS);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            boolean active = "在售".equals(value);
            chip.setText(value == null ? "" : String.valueOf(value));
            chip.setColor(active ? VCampusTheme.SUCCESS : OFF_SHELF);
            return chip;
        }
    }

    /** 圆角胶囊小标签（自绘圆底圆边），供状态列渲染与方块卡片复用。 */
    static final class ChipLabel extends JLabel {
        private Color color;

        ChipLabel(String text, Color color) {
            super(text, SwingConstants.CENTER);
            this.color = color;
            setOpaque(false);
            setFont(VCampusTheme.font(Font.BOLD, 12));
            setBorder(new EmptyBorder(3, 12, 3, 12));
        }

        void setColor(Color color) {
            this.color = color;
            setForeground(color);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D copy = (Graphics2D) g.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(paleColor(color));
            copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            copy.setColor(color);
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            copy.dispose();
            super.paintComponent(g);
        }
    }

    /** 把主色按 12% 掺入白色得到浅底（供胶囊/标签底色用，不改主题色板）。 */
    private static Color paleColor(Color strong) {
        return new Color(mixChannel(Color.WHITE.getRed(), strong.getRed()),
                mixChannel(Color.WHITE.getGreen(), strong.getGreen()),
                mixChannel(Color.WHITE.getBlue(), strong.getBlue()));
    }

    private static int mixChannel(int base, int overlay) {
        return Math.round((base * 88 + overlay * 12) / 100f);
    }
}

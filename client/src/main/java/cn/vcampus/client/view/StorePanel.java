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
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Window;
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
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

/**
 * 商店页面：商品浏览与筛选、直接购买、购物车结算、校园钱包与管理员商品维护。
 * 买家角色与管理员角色共用本面板，管理员专属入口按 manager 标记增删，真正拦截在服务端权限门槛，
 * 界面显隐只是 UX（对齐 LibraryPanel 的角色分化方式）。
 */
public final class StorePanel extends JPanel {
    /** 商店身份页：CONSUMER=消费者（浏览/购买/购物车/钱包），MANAGER=管理者（商品维护/全部订单/余额校正）。 */
    public enum Mode {
        CONSUMER, MANAGER
    }

    private static final int HOT_PRODUCT_LIMIT = 10;
    private static final int MAX_QUANTITY = 999;
    // 类别下拉的“全部”占位项（与 category=null 等价）
    private static final String ALL_CATEGORIES = "全部类别";

    private final String host;
    private final int port;
    private final Session session;
    private final Mode mode;
    // manager 等价于 mode==MANAGER，保留布尔量供既有显隐判断复用
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
    // 价格区间（元）：留空=该侧不限；与关键词/类别一起发服务端做多字段拼接查询
    private final JTextField minPriceField = new JTextField(6);
    private final JTextField maxPriceField = new JTextField(6);
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
    private final JPanel catalogCards = new VCampusTheme.RoundedClipPanel(catalogCardLayout, 16);// CENTER 双视图容器（圆角裁剪）
    private final JPanel catalogSouth = new JPanel(new BorderLayout());// SOUTH：仅列表操作行（方块视图整行隐藏，给商品区让出空间）
    private JSplitPane catalogSplit;// 商品区/操作行可拖拽分隔条（列表大小可调），catalogPanel 内创建
    private boolean catalogDividerInitialized;
    private final JPanel cardHost = new JPanel(new GridLayout(0, 2, UiMetrics.px(16), UiMetrics.px(16)));// 方块宿主（默认双列，窄窗切单列）
    private final JScrollPane cardScroller = VCampusTheme.scrollPane(cardHost);
    private JScrollPane listScroller;// 列表宿主（catalogPanel 内创建）
    private int cardColumns = 2;// 方块网格列数：可用宽 <720 逻辑像素时切单列
    private final JToggleButton viewModeButton = new JToggleButton("方块视图");// 显示“要切到的”目标形态
    private static final String LIST_VIEW = "list";
    private static final String CARD_VIEW = "cards";

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
    private final JButton manageButton = new JButton("管理商品…");// 维护类操作下拉入口，保证操作行单行不换行
    private final JButton importProductsButton = new JButton("批量导入…");// 商品批量导入入口（管理端下拉内）
    private final ProductImportFileReader productImportReader = new ProductImportFileReader();
    private final JButton refreshCartButton = new JButton("刷新购物车");
    private final JButton selectAllCartButton = new JButton("全选");
    private final JButton updateQuantityButton = new JButton("修改数量");
    private final JButton removeFromCartButton = new JButton("批量删除");
    private final JButton checkoutSelectedButton = new JButton("购买选中");
    private final JButton checkoutButton = new JButton("结算全部");
    private final JButton refreshOrdersButton = new JButton("刷新我的订单");
    private final JButton rechargeButton = new JButton("充值");
    private final JButton refreshLedgerButton = new JButton("刷新流水");
    private final JButton adjustBalanceButton = new JButton("校正余额");
    private final JButton allOrdersButton = new JButton("刷新全部订单");

    private int activeMutationRequests;
    private boolean hotViewVisible;
    private boolean inactiveViewVisible;// 管理端「含下架」视图开关；与热销视图互斥
    private int initialProductRetryAttempts = 1;

    public StorePanel(String host, int port, Session session) {
        this(host, port, session, defaultMode(session));
    }

    /** 显式指定身份页：CONSUMER 只给消费者能力，MANAGER 只给商品维护/全部订单/余额校正。 */
    public StorePanel(String host, int port, Session session, Mode mode) {
        if (host == null || host.trim().isEmpty() || session == null || mode == null) {
            throw new IllegalArgumentException("host, session and mode must not be null");
        }
        this.host = host.trim();
        this.port = port;
        this.session = session;
        this.mode = mode;
        this.manager = mode == Mode.MANAGER;
        build();
    }

    // 3 参构造的默认模式：管理员/商店经理→MANAGER，其余→CONSUMER（供旧调用与测试）
    private static Mode defaultMode(Session session) {
        return session != null && canManage(session.getUser().getRole()) ? Mode.MANAGER : Mode.CONSUMER;
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
        selectAllCartButton.addActionListener(event -> selectAllCart());
        updateQuantityButton.addActionListener(event -> updateSelectedCartQuantity());
        removeFromCartButton.addActionListener(event -> removeSelectedCartItems());
        checkoutSelectedButton.addActionListener(event -> checkoutSelectedCart());
        checkoutButton.addActionListener(event -> checkoutCart());
        refreshOrdersButton.addActionListener(event -> loadOrders());
        rechargeButton.addActionListener(event -> promptRecharge());
        refreshLedgerButton.addActionListener(event -> loadLedger());
        allOrdersButton.addActionListener(event -> loadAllOrders());
        if (manager) {
            adjustBalanceButton.addActionListener(event -> promptAdjustBalance());
        }
        // 回车即发服务端多字段查询（关键词+类别+价格区间），与“查询商品”按钮一致；
        // 输入过程中的即时手感仍由 KeywordWatcher 对已加载结果做本地过滤兼顾
        keywordField.addActionListener(event -> loadProducts());
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
            // 选中行变化时同步购买/加购按钮置灰（下架行不可买），与卡片视图行为一致
            updateButtonState();
        });

        updateButtonState();
        loadBalance();
        // 进入商店页自动加载一次商品列表，否则列表保持空白，必须手动点查询/切换视图才出现
        loadInitialProducts();
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
        tabs.setPreferredSize(UiMetrics.dimension(0, 600));
        tabs.setMinimumSize(UiMetrics.dimension(0, 300));
        panel.add(tabs, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setOpaque(true);
        statusPanel.setBackground(VCampusTheme.PANEL);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.BORDER, 12), VCampusTheme.padding(10, 16, 10, 16)));
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
                ? "以管理者身份维护商品与库存、查看全部订单、校正用户余额。"
                : "以消费者身份浏览商品、直接购买、购物车结算，并查看本人订单与钱包流水。";
        JLabel subtitle = new JLabel("当前用户：" + session.getUser().getDisplayName() + "；" + capabilities);
        subtitle.setForeground(VCampusTheme.MUTED);

        // 余额做成右对齐圆角胶囊卡：主色浅底 + 主色边，落实组长“关键数值突出显示”
        balanceLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
        balanceLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        balanceLabel.setOpaque(true);
        balanceLabel.setBackground(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 10));
        balanceLabel.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 35), 999),
                VCampusTheme.padding(8, 14, 8, 14)));
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
        // 便签式页签（圆角顶），仅商店页使用，其他模块页签不变
        tabs.setUI(new VCampusTheme.StickyTabbedPaneUI());
        tabs.setOpaque(true);
        tabs.setBackground(VCampusTheme.BACKGROUND);
        if (mode == Mode.MANAGER) {
            // 管理者页：只有商品维护与全部订单，不出现购物车/我的订单/钱包等消费者页签
            tabs.addTab("商品维护", catalogPanel());
            tabs.addTab("全部订单", allOrderPanel());
        } else {
            // 消费者页：商品列表 / 购物车 / 我的订单 / 钱包
            tabs.addTab("商品列表", catalogPanel());
            tabs.addTab("购物车", cartPanel());
            tabs.addTab("我的订单", orderPanel());
            tabs.addTab("钱包", walletPanel());
        }
        // 切页签即加载该页数据，用户不必每页都先找“刷新”按钮
        tabs.addChangeListener(event -> onTabSelected(tabs.getSelectedIndex()));
        return tabs;
    }

    /** 页签索引按身份页不同：消费者 商品0/购物车1/我的订单2/钱包3；管理者 商品维护0/全部订单1。 */
    private void onTabSelected(int index) {
        // 已有请求在飞时不再叠加，否则新代次会顶掉正在回来的响应
        if (activeMutationRequests > 0) {
            return;
        }
        if (mode == Mode.MANAGER) {
            if (index == 1) {
                loadAllOrders();
            }
            return;
        }
        if (index == 1) {
            loadCart();
        } else if (index == 2) {
            loadOrders();
        } else if (index == 3) {
            loadLedger();
        }
    }

    private JPanel catalogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        storeSection(panel);

        // 工具栏：整行包进白色圆角表面，控件间距走 8pt 栅格（12/8），把“查询/热销/含下架/视图切换”收成清晰分区
        JPanel search = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(8)));
        storeToolbar(search);
        search.add(new JLabel("关键词"));
        VCampusTheme.roundedField(keywordField);
        keywordField.setPreferredSize(UiMetrics.dimension(160, 36));
        search.add(keywordField);
        search.add(new JLabel("类别"));
        VCampusTheme.roundedField(categoryBox);
        categoryBox.setPreferredSize(UiMetrics.dimension(150, 36));
        search.add(categoryBox);
        // 价格区间：两个数字输入框，留空=该侧不限；与关键词/类别一起发服务端做多字段拼接查询
        search.add(new JLabel("价格"));
        VCampusTheme.roundedField(minPriceField);
        minPriceField.setPreferredSize(UiMetrics.dimension(88, 36));
        search.add(minPriceField);
        search.add(new JLabel("—"));
        VCampusTheme.roundedField(maxPriceField);
        maxPriceField.setPreferredSize(UiMetrics.dimension(88, 36));
        search.add(maxPriceField);
        themeSecondary(searchButton);
        themeSecondary(hotButton);
        search.add(searchButton);
        search.add(hotButton);
        // 含下架开关对学生也开放：看得到不等于买得到（服务端购买/加购仍拒下架品）
        themeSecondary(inactiveButton);
        search.add(inactiveButton);
        if (mode == Mode.CONSUMER) {
            // 视图切换（方块/列表）只对消费者开放；管理者固定列表维护（方块卡片自带购买按钮，与管理页语义冲突）
            themeSecondary(viewModeButton);
            search.add(viewModeButton);
            viewModeButton.setToolTipText("在“方块（双列卡片）/ 列表（表格）”两种展示间切换");
        }

        configureTable(productTable);
        applyMoneyColumns(productTable, MoneyCellRenderer.MoneyFormat.YUAN, 3);
        styleProductCatalog(productTable);

        // CENTER 双视图：列表宿主与方块宿主（外层 catalogCards 为圆角裁剪容器，内部方角不外戳）
        listScroller = VCampusTheme.scrollPane(productTable);
        listScroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        cardHost.setBackground(VCampusTheme.BACKGROUND);
        cardHost.setBorder(VCampusTheme.padding(8, 8, 8, 8));// 卡片不贴容器壁
        cardScroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        cardScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        catalogCards.add(listScroller, LIST_VIEW);
        catalogCards.add(cardScroller, CARD_VIEW);
        catalogCards.setOpaque(false);
        // 窄窗（<720 逻辑像素）自动切单列，仿学籍 740px 换行先例
        cardScroller.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                updateCardColumns(cardScroller.getWidth());
            }
        });

        // SOUTH-列表：选中行操作（数量/购买/加购/详情 + 管理员维护），包进白色圆角表面
        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(6)));
        storeToolbar(actions);
        themeSecondary(detailButton);
        if (mode == Mode.CONSUMER) {
            // 消费者操作行：数量 + 购买选中 + 加入购物车 + 商品详情（不出现管理下拉与余额校正）
            themePrimary(purchaseButton);
            themeSecondary(addToCartButton);
            actions.add(new JLabel("数量"));
            styleQuantitySpinner(quantity, 88);
            actions.add(quantity);
            actions.add(purchaseButton);
            actions.add(addToCartButton);
            actions.add(detailButton);
        } else {
            // 管理者操作行：商品详情 + 管理下拉（新增/编辑/补货/下架/重新上架/批量导入）+ 余额校正；不出现数量/购买/加购
            actions.add(detailButton);
            themeSecondary(manageButton);
            actions.add(manageButton);
            final JPopupMenu manageMenu = new JPopupMenu();
            manageMenu.setBackground(VCampusTheme.PANEL);
            manageMenu.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 12));
            manageMenu.setLayout(new GridLayout(0, 1, 0, UiMetrics.px(4)));
            for (JButton operation : new JButton[] { addProductButton, editProductButton, restockButton,
                    deactivateButton, reactivateButton, importProductsButton }) {
                themeSecondary(operation);
                operation.setHorizontalAlignment(SwingConstants.LEFT);
                operation.setBorder(VCampusTheme.padding(8, 14, 8, 14));
                manageMenu.add(operation);
                operation.addActionListener(event -> manageMenu.setVisible(false));
            }
            importProductsButton.addActionListener(event -> importProducts());
            manageButton.addActionListener(event -> {
                // 向上弹出：按钮贴近窗口底部，向下弹会裁掉最后一项（重新上架）
                int menuHeight = manageMenu.getPreferredSize().height;
                manageMenu.show(manageButton, 0, -menuHeight);
            });
            themeSecondary(adjustBalanceButton);
            actions.add(adjustBalanceButton);
        }
        // SOUTH 仅承载列表操作行；方块视图时整行隐藏，把纵向空间让给商品区（卡片自带加购/购买/详情按钮）
        catalogSouth.add(actions, BorderLayout.CENTER);
        catalogSouth.setOpaque(false);

        // 商品区与操作行之间用可拖拽分隔条：列表太小时用户可自行拉大（多余空间优先给商品区）
        catalogSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, catalogCards, catalogSouth);
        catalogSplit.setResizeWeight(1.0);
        catalogSplit.setOneTouchExpandable(true);
        catalogSplit.setContinuousLayout(false);
        catalogSplit.setDividerSize(UiMetrics.px(6));
        catalogSplit.setBorder(null);
        catalogSplit.setOpaque(false);
        catalogSplit.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                if (!catalogDividerInitialized && !cardViewVisible && catalogSplit.getHeight() > 0) {
                    catalogDividerInitialized = true;
                    catalogSplit.setDividerLocation((int) (catalogSplit.getHeight() * 0.8));
                }
            }
        });

        panel.add(search, BorderLayout.NORTH);
        panel.add(catalogSplit, BorderLayout.CENTER);

        applyViewMode();// 按默认形态显示一次（买家方块/管理员列表）
        return panel;
    }

    private JPanel cartPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        storeSection(panel);
        configureTable(cartTable);
        applyMoneyColumns(cartTable, MoneyCellRenderer.MoneyFormat.YUAN, 2, 4);
        // 购物车多选：支持全选后批量删除/结算选中，覆盖 configureTable 的单选默认
        cartTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(6)));
        storeToolbar(actions);
        themeSecondary(refreshCartButton);
        themeSecondary(selectAllCartButton);
        themeSecondary(updateQuantityButton);
        themeSecondary(removeFromCartButton);
        themePrimary(checkoutSelectedButton);
        themePrimary(checkoutButton);
        actions.add(refreshCartButton);
        actions.add(selectAllCartButton);
        actions.add(updateQuantityButton);
        actions.add(removeFromCartButton);
        actions.add(checkoutSelectedButton);
        actions.add(checkoutButton);

        // 合计只累加服务端给出的 subtotalCents，不在前端用单价×数量重算，否则会与实扣金额差一两分
        // 合计也做成圆角胶囊，与头部余额一致，突出关键数值
        cartTotalLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
        cartTotalLabel.setForeground(VCampusTheme.PRIMARY_DARK);
        cartTotalLabel.setOpaque(true);
        cartTotalLabel.setBackground(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 10));
        cartTotalLabel.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 35), 999),
                VCampusTheme.padding(8, 14, 8, 14)));
        JPanel footer = new JPanel(new BorderLayout(0, 10));
        footer.setOpaque(false);
        footer.add(actions, BorderLayout.WEST);
        footer.add(cartTotalLabel, BorderLayout.EAST);

        JScrollPane scroller = VCampusTheme.scrollPane(cartTable);
        scroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        panel.add(scroller, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel orderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        storeSection(panel);
        configureTable(orderTable);
        applyMoneyColumns(orderTable, MoneyCellRenderer.MoneyFormat.YUAN, 3, 4);

        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(6)));
        storeToolbar(actions);
        themeSecondary(refreshOrdersButton);
        actions.add(refreshOrdersButton);

        JScrollPane scroller = VCampusTheme.scrollPane(orderTable);
        scroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        panel.add(scroller, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel walletPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        storeSection(panel);

        JPanel balanceBar = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(6)));
        storeToolbar(balanceBar);
        themePrimary(rechargeButton);
        themeSecondary(refreshLedgerButton);
        balanceBar.add(rechargeButton);
        balanceBar.add(refreshLedgerButton);
        // 余额校正属于管理者能力，已移到「商品维护」页操作行；钱包页为消费者专属，不再出现校正入口

        configureTable(ledgerTable);
        // 金额列是带符号的「分」，余额列是非负的「分」，两种单位不能共用同一渲染分支
        ledgerTable.getColumnModel().getColumn(2)
                .setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.MoneyFormat.SIGNED_CENTS));
        ledgerTable.getColumnModel().getColumn(3)
                .setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.MoneyFormat.CENTS));

        JScrollPane scroller = VCampusTheme.scrollPane(ledgerTable);
        scroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        panel.add(balanceBar, BorderLayout.NORTH);
        panel.add(scroller, BorderLayout.CENTER);
        return panel;
    }

    private JPanel allOrderPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        storeSection(panel);
        configureTable(allOrderTable);
        applyMoneyColumns(allOrderTable, MoneyCellRenderer.MoneyFormat.YUAN, 4, 5);

        JPanel actions = new JPanel(new WrappingFlowLayout(FlowLayout.LEFT, UiMetrics.px(12), UiMetrics.px(6)));
        storeToolbar(actions);
        themeSecondary(allOrdersButton);
        actions.add(allOrdersButton);

        JScrollPane scroller = VCampusTheme.scrollPane(allOrderTable);
        scroller.setBorder(VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16));
        panel.add(scroller, BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    /** 主题按钮样式 + 去“方角描边” + 开启悬停/焦点环（商店面板专属，圆角底由主题 ReadableButtonUI 自绘）。 */
    private static void themePrimary(AbstractButton button) {
        VCampusTheme.primaryButton(button);
        softenBorder(button);
        VCampusTheme.interactive(button);
    }

    private static void themeSecondary(AbstractButton button) {
        VCampusTheme.secondaryButton(button);
        softenBorder(button);
        VCampusTheme.interactive(button);
    }

    /** 把主题设置的外框方线换为内边距留白，按钮观感由方角→圆角扁平（底色仍由主题色板控制）。 */
    private static void softenBorder(AbstractButton button) {
        button.setBorder(VCampusTheme.padding(9, 18, 9, 18));
    }

    /** 商店页分区容器：浅底(BACKGROUND) + 圆角边(16) + 内边距，替代全局白色直角 panel()，消除“棱角”。 */
    private static void storeSection(JPanel panel) {
        panel.setOpaque(true);
        panel.setBackground(VCampusTheme.BACKGROUND);
        panel.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16), VCampusTheme.padding(16, 18, 16, 18)));
    }

    /** 商店页工具/操作行表面：白底 + 圆角边(12) + 内边距(10,14)，把同类操作收成清晰分区。 */
    private static void storeToolbar(JPanel panel) {
        panel.setOpaque(true);
        panel.setBackground(VCampusTheme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.BORDER, 12), VCampusTheme.padding(10, 14, 10, 14)));
    }

    private static void configureTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(40);
        // 开启列排序后，视图行号与模型行号不再相同，取值一律经 convertRowIndexToModel 换算
        table.setAutoCreateRowSorter(true);
        table.setShowVerticalLines(false);
        table.setGridColor(VCampusTheme.BORDER);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(VCampusTheme.font(Font.BOLD, 13));
        header.setBackground(VCampusTheme.SURFACE_ALT);
        header.setForeground(VCampusTheme.PRIMARY_DARK);
        // 表头只留底部 1px 分隔线（去掉四边框），更轻盈；仅商店表格局部覆写，不动 theme.table()
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, VCampusTheme.BORDER));
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

    /** First entry gets one delayed retry so a transient server handoff does not leave an empty catalog. */
    private void loadInitialProducts() {
        final String category = selectedCategory();
        final boolean includeInactive = inactiveViewVisible;
        runReadRequest("正在加载商品…", service -> service.listProducts(session.getToken(),
                        category.isEmpty() ? null : category, includeInactive),
                response -> showProducts(response, true), this::retryInitialProductLoad);
    }

    private void retryInitialProductLoad() {
        if (initialProductRetryAttempts-- <= 0) {
            return;
        }
        showStatus("首次加载失败，正在自动重试…", VCampusTheme.MUTED);
        Timer retry = new Timer(600, event -> loadProducts());
        retry.setRepeats(false);
        retry.start();
    }

    /**
     * 加载商品列表。announce=true 时（用户主动操作）成功后在状态栏播报条数；
     * announce=false 时（购买/结算失败后的后台静默刷新）只更新表格数据，
     * 不覆盖状态栏里的错误提示——否则「余额不足」等提示会被刷新成功文案顶掉、一闪即逝。
     */
    private void loadProducts(boolean announce) {
        final String category = selectedCategory();
        final String keyword = keywordField.getText().trim();
        // 含下架视图对所有角色开放（服务端已放宽为 STORE_READ）：买家可浏览下架陈列，购买仍被拒
        final boolean includeInactive = inactiveViewVisible;
        final Double minPrice;
        final Double maxPrice;
        try {
            minPrice = parsePriceBound(minPriceField.getText());
            maxPrice = parsePriceBound(maxPriceField.getText());
        } catch (NumberFormatException invalid) {
            showStatus("价格区间格式不正确，请输入数字或留空", VCampusTheme.DANGER);
            return;
        }
        if (minPrice != null && maxPrice != null && minPrice.doubleValue() > maxPrice.doubleValue()) {
            showStatus("最低价不能大于最高价", VCampusTheme.DANGER);
            return;
        }
        hotViewVisible = false;
        hotButton.setText("热销 Top" + HOT_PRODUCT_LIMIT);
        String suffix = includeInactive ? "（含已下架）" : "";
        runReadRequest((category.isEmpty() ? "正在查询商品" : "正在查询「" + category + "」类商品") + suffix + "…",
                service -> service.searchProducts(session.getToken(), keyword.isEmpty() ? null : keyword,
                        category.isEmpty() ? null : category, minPrice, maxPrice, includeInactive),
                response -> showProducts(response, announce));
    }

    /** 价格区间输入解析：空白=该侧不限（null）；非数字抛 NumberFormatException 由调用方转成中文提示。 */
    private static Double parsePriceBound(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return Double.valueOf(trimmed);
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
            viewModeButton.setText("列表视图");
            rebuildCardView();
        } else {
            catalogCardLayout.show(catalogCards, LIST_VIEW);
            viewModeButton.setText("方块视图");
        }
        // 方块视图不需要列表操作行：隐藏并把分隔条推到底让卡片占满；列表视图恢复可拖拽分隔
        catalogSouth.setVisible(!cardViewVisible);
        if (catalogSplit != null) {
            catalogSplit.setEnabled(!cardViewVisible);
            catalogSplit.setDividerLocation(cardViewVisible ? 1.0 : 0.8);
        }
        viewModeButton.setSelected(cardViewVisible);
    }

    /** 用当前 visibleProducts 重建方块网格（过滤/加载后数据变化时调用）；结果为空时显示居中空态文案。 */
    private void rebuildCardView() {
        cardHost.removeAll();
        applyCardGridLayout();
        if (visibleProducts.isEmpty()) {
            JLabel empty = new JLabel("未找到匹配商品，可调整关键词或类别后重试", SwingConstants.CENTER);
            empty.setForeground(VCampusTheme.MUTED);
            empty.setFont(VCampusTheme.font(Font.PLAIN, 14));
            empty.setBorder(VCampusTheme.padding(48, 0, 48, 0));
            cardHost.add(empty);
        } else {
            for (Product product : visibleProducts) {
                cardHost.add(new ProductCard(product));
            }
        }
        cardHost.revalidate();
        cardHost.repaint();
    }

    /** 按当前列数与数据量设置方块网格布局：空态用单元格居中，非空用 cardColumns 列（间距 16）。 */
    private void applyCardGridLayout() {
        if (visibleProducts.isEmpty()) {
            cardHost.setLayout(new GridLayout(1, 1, 0, 0));
        } else {
            int gap = UiMetrics.px(16);
            cardHost.setLayout(new GridLayout(0, cardColumns, gap, gap));
        }
    }

    /** 依可用宽度切换方块列数：<720 逻辑像素单列，否则双列；列数变化时重排网格。 */
    private void updateCardColumns(int availableWidth) {
        int target = availableWidth > 0 && availableWidth < UiMetrics.px(720) ? 1 : 2;
        if (target != cardColumns) {
            cardColumns = target;
            applyCardGridLayout();
            cardHost.revalidate();
            cardHost.repaint();
        }
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
        runReadRequest("正在查询热销商品…", service -> service.hotProducts(session.getToken(), HOT_PRODUCT_LIMIT),
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
        if (!storeConfirm("确认购买",
                "确认购买「" + product.getName() + "」× " + count + "，将扣款 "
                        + StoreRowMapper.formatYuan(totalCents) + " 元？")) {
            return;
        }
        final String productId = product.getProductId();
        final String productName = product.getName();
        runMutationRequest("正在提交购买请求…", service -> service.purchase(session.getToken(), productId, count),
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
        runMutationRequest("正在加入购物车…", service -> service.addToCart(session.getToken(), productId, count),
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
        showStoreDialog("商品详情", dialogBody("商品详情", detailRows(product)), false);
    }

    private void showAddProductDialog() {
        final StoreProductForm form = StoreProductForm.showAdd(this);
        if (form == null) {
            return;
        }
        runMutationRequest("正在新增商品…",
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
        runMutationRequest("正在更新商品…",
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
        String input = storeInput("补货",
                "「" + product.getName() + "」当前库存 " + product.getStock() + " 件，请输入补货数量：", null);
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
        runMutationRequest("正在补货…", service -> service.restock(session.getToken(), productId, additional), response -> {
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
        if (!storeConfirm("下架确认",
                "确定下架「" + product.getName() + "」？\n下架后买家将无法购买，已存在的购物车条目也会标记为失效。")) {
            return;
        }
        final String productId = product.getProductId();
        runMutationRequest("正在下架商品…", service -> service.deactivateProduct(session.getToken(), productId), response -> {
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
            if (!storeConfirm("重新上架确认",
                    "确认重新上架「" + selected.getName() + "」（编号 " + selected.getProductId() + "）？")) {
                return;
            }
            final String productId = selected.getProductId();
            final String productName = selected.getName();
            runMutationRequest("正在重新上架…", service -> service.reactivateProduct(session.getToken(), productId),
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
        String input = storeInput("重新上架", hint, null);
        if (input == null) {
            return;// 用户取消
        }
        final String productId = input.trim();
        if (productId.isEmpty()) {
            showStatus("商品编号不能为空", VCampusTheme.DANGER);
            return;
        }
        runMutationRequest("正在重新上架…", service -> service.reactivateProduct(session.getToken(), productId), response -> {
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
        runReadRequest("正在查询购物车…", service -> service.cartDetail(session.getToken()),
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

    /** 多选模式下选中的全部购物车行（视图行号经 rowSorter 换算回模型行）；无选中返回空列表。 */
    private List<CartLine> selectedCartLines() {
        List<CartLine> result = new ArrayList<CartLine>();
        for (int viewRow : cartTable.getSelectedRows()) {
            int modelRow = cartTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < cartLines.size()) {
                result.add(cartLines.get(modelRow));
            }
        }
        return result;
    }

    /** 全选购物车：一键选中全部行，配合批量删除/购买选中使用。 */
    private void selectAllCart() {
        if (cartLines.isEmpty()) {
            showStatus("购物车是空的，无可选条目", VCampusTheme.MUTED);
            return;
        }
        cartTable.selectAll();
    }

    private void updateSelectedCartQuantity() {
        List<CartLine> selected = selectedCartLines();
        if (selected.size() != 1) {
            showStatus("修改数量请只选择一个购物车条目", VCampusTheme.DANGER);
            return;
        }
        final CartLine line = selected.get(0);
        if (!line.isActive()) {
            showStatus("「" + line.getProductName() + "」已下架，请移除该条目", VCampusTheme.DANGER);
            return;
        }
        String input = storeInput("修改数量",
                "「" + line.getProductName() + "」当前数量 " + line.getQuantity() + "，请输入新数量：", null);
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
        runMutationRequest("正在修改数量…", service -> service.updateCart(session.getToken(), cartItemId, newQuantity),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已将「" + line.getProductName() + "」数量改为 " + newQuantity, VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    private void removeSelectedCartItems() {
        List<CartLine> selected = selectedCartLines();
        if (selected.isEmpty()) {
            showStatus("请先在购物车表中选择要删除的条目", VCampusTheme.DANGER);
            return;
        }
        final List<String> ids = new ArrayList<String>();
        for (CartLine line : selected) {
            ids.add(line.getCartItemId());
        }
        final int count = ids.size();
        // 批量删除不可逆，先把件数摆出来让用户确认
        if (!storeConfirm("批量删除", "确认从购物车删除选中的 " + count + " 条商品？")) {
            return;
        }
        runMutationRequest("正在批量删除购物车条目…",
                service -> service.removeFromCartBatch(session.getToken(), ids),
                response -> {
                    if (!isSuccessful(response)) {
                        return;
                    }
                    showStatus("已删除 " + count + " 条购物车条目", VCampusTheme.SUCCESS);
                    SwingUtilities.invokeLater(this::loadCart);
                });
    }

    /** 结算选中：仅结算勾选子集（服务端子集 checkout），含下架/合计确认与整单结算一致。 */
    private void checkoutSelectedCart() {
        List<CartLine> selected = selectedCartLines();
        if (selected.isEmpty()) {
            showStatus("请先在购物车表中选择要结算的条目", VCampusTheme.DANGER);
            return;
        }
        int inactive = 0;
        long total = 0L;
        final List<String> ids = new ArrayList<String>();
        for (CartLine line : selected) {
            if (!line.isActive()) {
                inactive++;
            }
            total += line.getSubtotalCents();
            ids.add(line.getCartItemId());
        }
        if (inactive > 0) {
            showStatus("选中的条目里有 " + inactive + " 条已下架商品，请先移除再结算", VCampusTheme.DANGER);
            return;
        }
        final int count = selected.size();
        if (!storeConfirm("结算选中",
                "本次结算选中 " + count + " 条、合计 " + StoreRowMapper.formatYuan(total)
                        + " 元，确认从校园钱包扣款？")) {
            return;
        }
        runMutationRequest("正在结算选中商品…",
                service -> service.checkoutSelected(session.getToken(), ids), response -> {
            if (!isSuccessful(response)) {
                // 结算失败会触发服务端补偿回滚；静默刷新购物车与余额（不覆盖错误提示）
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
        if (!storeConfirm("结算确认",
                "本次结算共 " + cartLines.size() + " 条、合计 " + StoreRowMapper.formatYuan(cartTotalCents)
                        + " 元，确认从校园钱包扣款？")) {
            return;
        }
        submitCheckout();
    }

    private void submitCheckout() {
        runMutationRequest("正在结算购物车…", service -> service.checkout(session.getToken()), response -> {
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
        runReadRequest("正在查询我的订单…", service -> service.ordersFor(session.getToken()), this::showOrders);
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
        runReadRequest("正在查询全部订单…", service -> service.allOrders(session.getToken()), this::showAllOrders);
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
        runReadRequest("正在查询余额…", service -> service.balance(session.getToken()), this::showBalance);
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
        runReadRequest("正在查询钱包流水…", service -> service.ledger(session.getToken()), this::showLedger);
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
        String input = storeInput("充值", "请输入充值金额（元）：", null);
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
        runMutationRequest("正在充值…", service -> service.recharge(session.getToken(), cents), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("充值成功，已入账 " + StoreRowMapper.formatYuan(cents) + " 元", VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadBalance);
            SwingUtilities.invokeLater(this::loadLedger);
        });
    }

    private void promptAdjustBalance() {
        String targetInput = storeInput("校正余额", "请输入目标用户编号：", null);
        if (targetInput == null || targetInput.trim().isEmpty()) {
            return;
        }
        String balanceInput = storeInput("校正余额", "请输入校正后的余额（元，绝对值）：", null);
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
        runMutationRequest("正在校正余额…", service -> service.adjustBalance(session.getToken(), target, cents), response -> {
            if (!isSuccessful(response)) {
                return;
            }
            showStatus("已将 " + target + " 的余额校正为 " + StoreRowMapper.formatYuan(cents) + " 元",
                    VCampusTheme.SUCCESS);
            SwingUtilities.invokeLater(this::loadBalance);
        });
    }

    private void runReadRequest(String loadingMessage, StoreRequest request, ResponseHandler responseHandler) {
        runRequest(false, loadingMessage, request, responseHandler, null);
    }

    private void runReadRequest(String loadingMessage, StoreRequest request, ResponseHandler responseHandler,
            Runnable failureHandler) {
        runRequest(false, loadingMessage, request, responseHandler, failureHandler);
    }

    private void runMutationRequest(String loadingMessage, StoreRequest request, ResponseHandler responseHandler) {
        runRequest(true, loadingMessage, request, responseHandler, null);
    }

    /**
     * Read requests keep browsing available; mutations temporarily lock mutation controls only so a slow
     * Access response cannot make the whole store page look frozen or allow duplicate payment submissions.
     */
    private void runRequest(boolean mutation, String loadingMessage, final StoreRequest request,
            final ResponseHandler responseHandler, final Runnable failureHandler) {
        if (mutation) {
            activeMutationRequests++;
            updateButtonState();
        }
        final Timer loadingStatus = DelayedUiUpdate.once(() -> {
            if (!mutation || activeMutationRequests > 0) {
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
                    if (failureHandler != null) {
                        failureHandler.run();
                    }
                } finally {
                    loadingStatus.stop();
                    if (mutation) {
                        activeMutationRequests = Math.max(0, activeMutationRequests - 1);
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
        boolean idle = activeMutationRequests == 0;
        searchButton.setEnabled(idle);
        hotButton.setEnabled(idle);
        inactiveButton.setEnabled(idle);
        viewModeButton.setEnabled(idle);
        // 列表视图：选中下架商品（或未选中）时置灰购买/加购，与卡片视图“下架不可买”一致；服务层仍兜底拒绝
        Product selectedForBuy = selectedProduct();
        boolean purchasable = idle && selectedForBuy != null && selectedForBuy.isActive();
        purchaseButton.setEnabled(purchasable);
        addToCartButton.setEnabled(purchasable);
        detailButton.setEnabled(idle);
        refreshCartButton.setEnabled(idle);
        selectAllCartButton.setEnabled(idle);
        updateQuantityButton.setEnabled(idle);
        removeFromCartButton.setEnabled(idle);
        checkoutSelectedButton.setEnabled(idle);
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
        importProductsButton.setEnabled(manager && idle);
        adjustBalanceButton.setEnabled(manager && idle);
        keywordField.setEnabled(idle);
        categoryBox.setEnabled(idle);
        minPriceField.setEnabled(idle);
        maxPriceField.setEnabled(idle);
        quantity.setEnabled(idle);
    }

    /** 商品批量导入：选择本地 csv/tsv → 主题化预览表 → 确认后逐行复用新增商品消息提交。 */
    private void importProducts() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("选择商品导入文件");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV/TSV 文件 (*.csv, *.tsv)", "csv", "tsv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final List<ProductImportRow> rows;
        try {
            rows = productImportReader.read(chooser.getSelectedFile().toPath());
        } catch (Exception failure) {
            showStoreDialog("导入失败", dialogBody("导入失败", dialogMessage(failure.getMessage())), false);
            return;
        }
        if (rows.isEmpty()) {
            showStoreDialog("导入失败", dialogBody("导入失败", dialogMessage("文件中没有可导入商品")), false);
            return;
        }
        DefaultTableModel previewModel = new DefaultTableModel(
                new Object[] { "名称", "价格", "库存", "类别", "说明" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (ProductImportRow row : rows) {
            previewModel.addRow(new Object[] { row.getName(), row.getPrice(), row.getStock(),
                    row.getCategory(), row.getDescription() });
        }
        JTable previewTable = new JTable(previewModel);
        JScrollPane previewScroller = VCampusTheme.scrollPane(previewTable);
        previewScroller.setPreferredSize(UiMetrics.dimension(560, 280));
        if (showStoreDialog("批量导入预览（共 " + rows.size() + " 件）", previewScroller, true) != JOptionPane.OK_OPTION) {
            return;
        }
        submitImport(rows);
    }

    /** 逐行提交导入：复用现有新增商品消息，汇总成功/失败后刷新列表。 */
    private void submitImport(final List<ProductImportRow> rows) {
        final int[] okCount = { 0 };
        final List<String> failedNames = new ArrayList<String>();
        runMutationRequest("正在批量导入商品…", service -> {
            Message last = null;
            for (ProductImportRow row : rows) {
                last = service.addProduct(session.getToken(), row.getName(), row.getPrice(),
                        row.getStock(), row.getDescription(), row.getCategory());
                // 后台线程不能碰 Swing（isSuccessful 会写状态栏），这里只看状态码
                if (last.getStatusCode() == StatusCode.OK) {
                    okCount[0]++;
                } else {
                    failedNames.add(row.getName());
                }
            }
            return last;
        }, response -> {
            if (failedNames.isEmpty()) {
                showStoreDialog("导入完成", dialogBody("导入完成",
                        dialogMessage("成功导入 " + okCount[0] + " 件商品")), false);
            } else {
                showStoreDialog("导入完成（部分失败）", dialogBody("导入完成（部分失败）",
                        dialogMessage("成功 " + okCount[0] + " 件，失败 " + failedNames.size()
                                + " 件：" + String.join("、", failedNames))),
                        false);
            }
            loadProducts();
        });
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
            setLayout(new BorderLayout(0, 12));
            setBackground(Color.WHITE);
            // 圆角卡片：静息用 BORDER 线、悬停用主色浅线，强化“可点选”暗示（不改动置灰/购买拦截逻辑）
            final Border idleBorder = BorderFactory.createCompoundBorder(
                    VCampusTheme.roundedBorder(VCampusTheme.BORDER, 16), VCampusTheme.padding(14, 16, 14, 16));
            final Border hoverBorder = BorderFactory.createCompoundBorder(
                    VCampusTheme.roundedBorder(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 45), 16),
                    VCampusTheme.padding(14, 16, 14, 16));
            setBorder(idleBorder);
            // 点击卡片=选中对应表格行，与“列表”操作联动
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    selectProductRow(product);
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent event) {
                    setBorder(hoverBorder);
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    setBorder(idleBorder);
                }
            });

            JPanel head = new JPanel(new BorderLayout(6, 0));
            head.setOpaque(false);
            JLabel nameLabel = new JLabel(product.getName());
            nameLabel.setFont(VCampusTheme.font(Font.BOLD, 15));
            nameLabel.setForeground(VCampusTheme.PRIMARY_DARK);
            nameLabel.setToolTipText(product.getName());
            head.add(nameLabel, BorderLayout.CENTER);
            JLabel priceLabel = new JLabel(
                    StoreRowMapper.formatYuan(StoreRowMapper.toCents(product.getPrice())) + " 元");
            priceLabel.setFont(VCampusTheme.font(Font.BOLD, 16));
            priceLabel.setForeground(VCampusTheme.PRIMARY);
            head.add(priceLabel, BorderLayout.EAST);

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(8), 0));
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
            styleQuantitySpinner(cardQuantity, 76);

            // 操作行与上方信息区留出呼吸（上边距 8），按钮间距 10
            JPanel foot = new JPanel(new FlowLayout(FlowLayout.LEFT, UiMetrics.px(10), 0));
            foot.setOpaque(false);
            foot.setBorder(VCampusTheme.padding(8, 0, 0, 0));
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
            // 尺寸必须走 UiMetrics 缩放：此前用裸像素，高 DPI 下内部（字号/内边距已缩放）溢出固定高度，
            // 导致类别/状态胶囊被压扁“埋住”；给到 196 逻辑高留出信息区余量
            setPreferredSize(UiMetrics.dimension(430, 196));
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

    /** 数量选择器统一观感：圆角边 + 主题字号 + 数字居中且编辑器透明，避免“大框小字”不协调。 */
    private static void styleQuantitySpinner(JSpinner spinner, int widthLogical) {
        VCampusTheme.roundedField(spinner);
        spinner.setFont(VCampusTheme.font(Font.PLAIN, 14));
        spinner.setPreferredSize(UiMetrics.dimension(widthLogical, 36));
        spinner.getEditor().setOpaque(false);
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor) {
            JTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
            field.setOpaque(false);
            field.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            field.setHorizontalAlignment(SwingConstants.CENTER);
            field.setFont(VCampusTheme.font(Font.BOLD, 14));
        }
    }

    // —— 商店统一对话框：去掉 JOptionPane 默认图标/灰按钮/小字号的“程序员”观感，仅商店页使用 ——

    /** 对话框内容卡标题：主色加粗，建立内容区层级。 */
    private static JLabel dialogTitle(String title) {
        JLabel label = new JLabel(title);
        label.setFont(VCampusTheme.font(Font.BOLD, 16));
        label.setForeground(VCampusTheme.PRIMARY_DARK);
        return label;
    }

    /** 多行正文：\n 渲染为换行并限定宽度自动折行，字号/颜色走主题。 */
    static JLabel dialogMessage(String message) {
        String escaped = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel label = new JLabel("<html><div style='width:" + UiMetrics.px(320) + "px;'>"
                + escaped.replace("\n", "<br>") + "</div></html>");
        label.setFont(VCampusTheme.font(Font.PLAIN, 14));
        label.setForeground(VCampusTheme.TEXT);
        return label;
    }

    /** 对话框主体内容包装：标题/留白/底色已由 showThemedDialog 的头部与容器提供，这里仅透传内容。 */
    static JComponent dialogBody(String title, JComponent inner) {
        return inner;
    }

    /** 商店统一对话框：无边框真圆角卡片 + 主色头部/浅色底部 + 自绘按钮，返回 JOptionPane 选项常量。 */
    static int showThemedDialog(Component parent, String title, JComponent content, boolean withCancel) {
        final JButton ok = new JButton("确定");
        themePrimary(ok);
        final JButton cancel = new JButton("取消");
        themeSecondary(cancel);
        final JButton close = new JButton("×");
        themeSecondary(close);
        close.setBorder(VCampusTheme.padding(2, 10, 2, 10));

        Window owner = SwingUtilities.getWindowAncestor(parent);
        boolean rounded = isTranslucencySupported();
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
        if (rounded) {
            dialog.setUndecorated(true);
            dialog.setBackground(new Color(0, 0, 0, 0));
        }
        JPanel root = rounded
                ? new VCampusTheme.ShadowedCardPanel(new BorderLayout(), 16, 10)
                : new JPanel(new BorderLayout());
        root.setBackground(VCampusTheme.PANEL);

        // 头部：主色浅底 + 图标徽章 + 加粗标题 + 关闭按钮，建立有色彩的第一视觉层
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(true);
        header.setBackground(VCampusTheme.tintOf(VCampusTheme.PRIMARY, 6));
        header.setBorder(VCampusTheme.padding(14, 18, 14, 18));
        JComponent badge = iconBadge(VCampusTheme.PRIMARY, "i");
        header.add(badge, BorderLayout.WEST);
        JLabel titleLabel = dialogTitle(title);
        header.add(titleLabel, BorderLayout.CENTER);
        header.add(close, BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(VCampusTheme.padding(16, 20, 16, 20));
        center.add(content, BorderLayout.CENTER);

        // 底部：浅色操作带 + 右对齐按钮，替掉居中孤零零一个按钮的观感
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiMetrics.px(10), 0));
        footer.setOpaque(true);
        footer.setBackground(VCampusTheme.SURFACE_ALT);
        footer.setBorder(VCampusTheme.padding(12, 18, 12, 18));
        if (withCancel) {
            footer.add(cancel);
        }
        footer.add(ok);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(root);

        final int[] result = { JOptionPane.CLOSED_OPTION };
        ok.addActionListener(event -> {
            result[0] = JOptionPane.OK_OPTION;
            dialog.dispose();
        });
        cancel.addActionListener(event -> {
            result[0] = JOptionPane.CANCEL_OPTION;
            dialog.dispose();
        });
        close.addActionListener(event -> {
            result[0] = JOptionPane.CANCEL_OPTION;
            dialog.dispose();
        });
        makeDraggable(dialog, header, titleLabel, badge);

        // 模态遮罩压暗宿主窗口，避免浅色对话框溶进浅色背景看不见
        final Runnable detachScrim = attachScrim(owner);
        try {
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        } finally {
            detachScrim.run();
        }
        return result[0];
    }

    /** 模态遮罩：把宿主窗口玻璃pane压暗，让浅色对话框从浅色背景中浮出；返回恢复动作。 */
    private static Runnable attachScrim(final Window owner) {
        JRootPane rootPane = null;
        if (owner instanceof JFrame) {
            rootPane = ((JFrame) owner).getRootPane();
        } else if (owner instanceof JDialog) {
            rootPane = ((JDialog) owner).getRootPane();
        }
        if (rootPane == null) {
            return new Runnable() {
                @Override
                public void run() {
                }
            };
        }
        final JRootPane target = rootPane;
        final Component oldGlass = target.getGlassPane();
        JPanel scrim = new JPanel() {
            @Override
            protected void paintComponent(Graphics graphics) {
                graphics.setColor(new Color(15, 23, 42, 70));
                graphics.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        scrim.setOpaque(false);
        target.setGlassPane(scrim);
        scrim.setVisible(true);
        return new Runnable() {
            @Override
            public void run() {
                scrim.setVisible(false);
                target.setGlassPane(oldGlass);
                oldGlass.setVisible(false);
            }
        };
    }

    /** 当前设备是否支持逐像素半透明（无边框真圆角对话框的前提），不支持则回退有边框方形。 */
    private static boolean isTranslucencySupported() {
        try {
            GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            return device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** 让头部（含标题/徽章）可拖拽移动无边框对话框。 */
    private static void makeDraggable(final JDialog dialog, JComponent... handles) {
        final java.awt.Point[] pressed = new java.awt.Point[1];
        java.awt.event.MouseAdapter press = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
                pressed[0] = event.getPoint();
            }
        };
        java.awt.event.MouseMotionAdapter drag = new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent event) {
                if (pressed[0] == null) {
                    return;
                }
                java.awt.Point location = dialog.getLocation();
                dialog.setLocation(location.x + event.getX() - pressed[0].x,
                        location.y + event.getY() - pressed[0].y);
            }
        };
        for (JComponent handle : handles) {
            handle.addMouseListener(press);
            handle.addMouseMotionListener(drag);
        }
    }

    /** 头部图标徽章：主色浅底圆角方块 + 居中字形，给对话框一个彩色视觉锚点。 */
    private static JComponent iconBadge(Color color, String glyph) {
        JLabel badge = new JLabel(glyph, SwingConstants.CENTER);
        badge.setFont(VCampusTheme.font(Font.BOLD, 15));
        badge.setForeground(color);
        badge.setOpaque(true);
        badge.setBackground(VCampusTheme.tintOf(color, 12));
        badge.setBorder(BorderFactory.createCompoundBorder(
                VCampusTheme.roundedBorder(VCampusTheme.tintOf(color, 35), 10),
                VCampusTheme.padding(6, 10, 6, 10)));
        return badge;
    }

    private int showStoreDialog(String title, JComponent content, boolean withCancel) {
        return showThemedDialog(this, title, content, withCancel);
    }

    private boolean storeConfirm(String title, String message) {
        return showStoreDialog(title, dialogBody(title, dialogMessage(message)), true) == JOptionPane.OK_OPTION;
    }

    private String storeInput(String title, String message, String initial) {
        JTextField field = new JTextField(initial == null ? "" : initial, 16);
        VCampusTheme.roundedField(field);
        field.setFont(VCampusTheme.font(Font.PLAIN, 14));
        JPanel inner = new JPanel(new BorderLayout(0, 10));
        inner.setOpaque(false);
        inner.add(dialogMessage(message), BorderLayout.NORTH);
        inner.add(field, BorderLayout.CENTER);
        if (showStoreDialog(title, dialogBody(title, inner), true) != JOptionPane.OK_OPTION) {
            return null;
        }
        return field.getText();
    }

    /** 商品详情内容：标签/数值两列排版，替掉纯文本多行串，关键值加粗突出。 */
    private static JPanel detailRows(Product product) {
        boolean active = product.isActive();
        JPanel grid = new JPanel(new GridLayout(0, 2, UiMetrics.px(16), UiMetrics.px(10)));
        grid.setOpaque(false);
        addDetailRow(grid, "名称", product.getName());
        addDetailRow(grid, "商品号", product.getProductId());
        addDetailRow(grid, "类别", product.getCategory());
        // 单价/状态做强调：价格主色加大、状态用彩色胶囊，给详情卡色彩与层级
        JLabel price = new JLabel(StoreRowMapper.formatYuan(StoreRowMapper.toCents(product.getPrice())) + " 元");
        price.setFont(VCampusTheme.font(Font.BOLD, 16));
        price.setForeground(VCampusTheme.PRIMARY);
        addDetailComponent(grid, "单价", price);
        JLabel stock = new JLabel(String.valueOf(product.getStock()));
        stock.setFont(VCampusTheme.font(Font.BOLD, 13));
        stock.setForeground(!active ? VCampusTheme.MUTED
                : (product.getStock() <= 0 ? VCampusTheme.DANGER
                        : (product.getStock() <= 5 ? new Color(202, 138, 4) : VCampusTheme.TEXT)));
        addDetailComponent(grid, "库存", stock);
        addDetailComponent(grid, "状态", new ChipLabel(active ? "在售" : "已下架",
                active ? VCampusTheme.SUCCESS : VCampusTheme.DANGER));
        addDetailRow(grid, "说明", product.getDescription() == null || product.getDescription().isEmpty()
                ? "（无）"
                : product.getDescription());
        return grid;
    }

    private static void addDetailComponent(JPanel grid, String label, Component value) {
        JLabel key = new JLabel(label);
        key.setFont(VCampusTheme.font(Font.PLAIN, 13));
        key.setForeground(VCampusTheme.MUTED);
        grid.add(key);
        grid.add(value);
    }

    private static void addDetailRow(JPanel grid, String label, String value) {
        JLabel key = new JLabel(label);
        key.setFont(VCampusTheme.font(Font.PLAIN, 13));
        key.setForeground(VCampusTheme.MUTED);
        grid.add(key);
        JLabel val = new JLabel(value);
        val.setFont(VCampusTheme.font(Font.BOLD, 13));
        val.setForeground(VCampusTheme.TEXT);
        grid.add(val);
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
                    ? null
                    : String.valueOf(value));
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

package cn.vcampus.server;

import cn.vcampus.store.BankAccount;
import cn.vcampus.store.CartItem;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用临时 Access 数据库验证商品和订单数据可以真实保存。 */
class AccessStoreRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private AccessProductRepository products;
    private AccessOrderRepository orders;
    private AccessCartRepository carts;
    private AccessBankAccountRepository bankAccounts;
    private AccessWalletTransactionRepository ledger;
    private Path database;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("store-test.accdb");
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblProduct ("
                    + "product_id VARCHAR(32) NOT NULL,"
                    + "name VARCHAR(100) NOT NULL,"
                    + "stock INTEGER NOT NULL,"
                    + "price DOUBLE NOT NULL,"
                    + "description VARCHAR(255),"
                    + "category VARCHAR(64) NOT NULL,"
                    + "active BIT NOT NULL,"
                    + "PRIMARY KEY (product_id))");
            statement.execute("CREATE TABLE tblOrder ("
                    + "order_id VARCHAR(36) NOT NULL,"
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "product_id VARCHAR(32) NOT NULL,"
                    + "quantity INTEGER NOT NULL,"
                    + "total_price DOUBLE NOT NULL,"
                    + "order_date DATETIME NOT NULL,"
                    + "product_name VARCHAR(100) NOT NULL,"
                    + "unit_price DOUBLE NOT NULL,"
                    + "PRIMARY KEY (order_id))");
            statement.execute("CREATE TABLE tblCartItem ("
                    + "cart_item_id VARCHAR(36) NOT NULL,"
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "product_id VARCHAR(32) NOT NULL,"
                    + "quantity INTEGER NOT NULL,"
                    + "added_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (cart_item_id),"
                    + "CONSTRAINT uk_tblCartItem_user_product UNIQUE (user_id, product_id))");
            statement.execute("CREATE TABLE tblBankAccount ("
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "balance_cents BIGINT NOT NULL,"
                    + "PRIMARY KEY (user_id))");
            statement.execute("CREATE TABLE tblWalletTransaction ("
                    + "transaction_id VARCHAR(36) NOT NULL,"
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "transaction_type VARCHAR(16) NOT NULL,"
                    + "amount_cents BIGINT NOT NULL,"
                    + "balance_after_cents BIGINT NOT NULL,"
                    + "operator_id VARCHAR(32) NOT NULL,"
                    + "note VARCHAR(200),"
                    + "created_at DATETIME NOT NULL,"
                    + "PRIMARY KEY (transaction_id))");
            insertProduct(connection, "P001", "黑色签字笔", 200, 2.0, "0.5mm 中性笔", "文具");
            insertProduct(connection, "P002", "笔记本 A5", 150, 5.0, "80页横线本", "文具");
        }
        products = new AccessProductRepository(database);
        orders = new AccessOrderRepository(database);
        carts = new AccessCartRepository(database);
        bankAccounts = new AccessBankAccountRepository(database);
        ledger = new AccessWalletTransactionRepository(database);
    }

    @Test
    void findAllReturnsAllProducts() {
        assertEquals(2, products.findAll().size());
    }

    @Test
    void findByIdReturnsExistingProduct() {
        Product product = products.findById("P001");
        assertNotNull(product);
        assertEquals("黑色签字笔", product.getName());
        assertEquals(200, product.getStock());
        assertEquals(2.0, product.getPrice(), 0.001);
        assertEquals("文具", product.getCategory());
    }

    @Test
    void findByIdReturnsNullForMissingProduct() {
        assertEquals(null, products.findById("P999"));
    }

    @Test
    void saveInsertsNewProduct() {
        products.save(new Product("P003", "矿泉水", 300, 1.5, "天然矿泉水", "零食饮料"));
        Product saved = products.findById("P003");
        assertNotNull(saved);
        assertEquals("矿泉水", saved.getName());
        assertEquals(300, saved.getStock());
    }

    @Test
    void saveUpdatesExistingProduct() {
        products.save(new Product("P001", "签字笔（升级版）", 200, 2.5, "0.7mm 中性笔", "文具"));
        Product updated = products.findById("P001");
        assertNotNull(updated);
        assertEquals("签字笔（升级版）", updated.getName());
        assertEquals(2.5, updated.getPrice(), 0.001);
    }

    @Test
    void updateStockChangesStockOnly() {
        assertTrue(products.updateStock("P001", 50));
        Product updated = products.findById("P001");
        assertEquals(50, updated.getStock());
        assertEquals("黑色签字笔", updated.getName());
    }

    @Test
    void updateStockReturnsFalseForMissingProduct() {
        assertFalse(products.updateStock("P999", 10));
    }

    @Test
    void inventoryAndCatalogMutationsArePersisted() {
        assertTrue(products.addStock("P001", 25));
        assertEquals(225, products.findById("P001").getStock());

        assertTrue(products.updateProduct(new Product("P001", "签字笔升级", 225, 2.5,
                "新描述", "文具", false)));
        Product changed = products.findById("P001");
        assertEquals("签字笔升级", changed.getName());
        assertEquals(2.5, changed.getPrice(), 0.001);
        assertFalse(changed.isActive());

        assertTrue(products.deleteById("P001"));
        assertEquals(null, products.findById("P001"));
    }

    @Test
    void createOrderPersistsAndFindByUserIdReturnsIt() {
        Order order = new Order("ORD001", "student001", "P001", 5, 10.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        assertTrue(orders.create(order));
        assertEquals(1, orders.findByUserId("student001").size());
        Order saved = orders.findByUserId("student001").get(0);
        assertEquals("ORD001", saved.getOrderId());
        assertEquals("student001", saved.getUserId());
        assertEquals("P001", saved.getProductId());
        assertEquals(5, saved.getQuantity());
        assertEquals(10.0, saved.getTotalPrice(), 0.001);
        assertEquals("黑色签字笔", saved.getProductName());
        assertEquals(2.0, saved.getUnitPrice(), 0.001);
    }

    @Test
    void createRejectsDuplicateOrderId() {
        Order order = new Order("ORD002", "student001", "P001", 3, 6.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        assertTrue(orders.create(order));
        assertFalse(orders.create(order));
    }

    @Test
    void findByUserIdReturnsEmptyForNoOrders() {
        assertTrue(orders.findByUserId("nobody").isEmpty());
    }

    @Test
    void findByUserIdReturnsOnlyOwnOrders() {
        Order order1 = new Order("ORD010", "student001", "P001", 2, 4.0,
                LocalDateTime.now(), "黑色签字笔", 2.0);
        Order order2 = new Order("ORD011", "student002", "P002", 1, 5.0,
                LocalDateTime.now(), "笔记本 A5", 5.0);
        orders.create(order1);
        orders.create(order2);
        assertEquals(1, orders.findByUserId("student001").size());
        assertEquals(1, orders.findByUserId("student002").size());
    }

    @Test
    void findAllAndSalesVolumeReadPersistedOrders() {
        orders.create(new Order("ORD020", "student001", "P001", 2, 4.0,
                LocalDateTime.now(), "黑色签字笔", 2.0));
        orders.create(new Order("ORD021", "student002", "P001", 3, 6.0,
                LocalDateTime.now(), "黑色签字笔", 2.0));
        orders.create(new Order("ORD022", "student002", "P002", 1, 5.0,
                LocalDateTime.now(), "笔记本 A5", 5.0));

        assertEquals(3, orders.findAll().size());
        List<Object[]> sales = orders.findSalesVolume();
        assertEquals(2, sales.size());
        assertEquals("P001", sales.get(0)[0]);
        assertEquals(5, sales.get(0)[1]);
    }

    @Test
    void deleteByIdRemovesProductAndReturnsFalseWhenMissing() {
        assertTrue(products.deleteById("P002"));
        assertEquals(null, products.findById("P002"));
        assertFalse(products.deleteById("P002"));
    }

    @Test
    void findAllPersistsActiveFlagForInactiveProducts() {
        products.save(new Product("P003", "已下架商品", 10, 1.0, "不可购买", "测试", false));
        Product saved = products.findById("P003");
        assertNotNull(saved);
        assertFalse(saved.isActive());
        boolean foundInactive = false;
        for (Product product : products.findAll()) {
            if (product.getProductId().equals("P003"))
                foundInactive = !product.isActive();
        }
        assertTrue(foundInactive);
    }

    @Test
    void cartAddThenFindByUserIdReturnsIt() {
        CartItem item = new CartItem("CART001", "student001", "P001", 3, LocalDateTime.now());
        assertTrue(carts.addItem(item));
        List<CartItem> items = carts.findByUserId("student001");
        assertEquals(1, items.size());
        CartItem saved = items.get(0);
        assertEquals("CART001", saved.getCartItemId());
        assertEquals("student001", saved.getUserId());
        assertEquals("P001", saved.getProductId());
        assertEquals(3, saved.getQuantity());
    }

    @Test
    void cartAddSameProductAccumulatesQuantity() {
        carts.addItem(new CartItem("CART010", "student001", "P001", 2, LocalDateTime.now()));
        carts.addItem(new CartItem("CART011", "student001", "P001", 3, LocalDateTime.now()));
        List<CartItem> items = carts.findByUserId("student001");
        assertEquals(1, items.size());
        assertEquals(5, items.get(0).getQuantity());
        assertEquals("CART010", items.get(0).getCartItemId());
    }

    @Test
    void cartFindByUserIdReturnsEmptyForNoItems() {
        assertTrue(carts.findByUserId("nobody").isEmpty());
    }

    @Test
    void cartRemoveItemDeletesEntry() {
        carts.addItem(new CartItem("CART020", "student001", "P001", 1, LocalDateTime.now()));
        assertTrue(carts.removeItem("CART020"));
        assertTrue(carts.findByUserId("student001").isEmpty());
        assertFalse(carts.removeItem("CART020"));
    }

    @Test
    void cartClearByUserIdRemovesOnlyThatUser() {
        carts.addItem(new CartItem("CART030", "student001", "P001", 1, LocalDateTime.now()));
        carts.addItem(new CartItem("CART031", "student001", "P002", 2, LocalDateTime.now()));
        carts.addItem(new CartItem("CART032", "student002", "P001", 1, LocalDateTime.now()));
        carts.clearByUserId("student001");
        assertTrue(carts.findByUserId("student001").isEmpty());
        assertEquals(1, carts.findByUserId("student002").size());
    }

    @Test
    void cartUniqueIndexRejectsDuplicateUserProductRow() throws Exception {
        insertCart("CART040", "student001", "P001", 1);
        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                () -> insertCart("CART041", "student001", "P001", 1));
    }

    @Test
    void concurrentAddSameProductKeepsSingleCartRow() throws Exception {
        carts.addItem(new CartItem("CART-BASE", "student001", "P001", 1, LocalDateTime.now()));
        int threads = 6;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < threads; i++) {
            final String cartItemId = "CART-C-" + i;
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    carts.addItem(new CartItem(cartItemId, "student001", "P001", 1, LocalDateTime.now()));
                }
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            try {
                future.get();
            } catch (java.util.concurrent.ExecutionException ignored) {
                // 个别连接因锁失败不影响唯一索引的单行保证
            }
        }
        pool.shutdown();

        List<CartItem> rows = carts.findByUserId("student001");
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).getQuantity() >= 1);
    }

    @Test
    void testAccessDeductStockSuccess() {
        assertTrue(products.deductStock("P001", 50));
        assertEquals(150, products.findById("P001").getStock());
    }

    @Test
    void testAccessDeductStockGuardRejectsInsufficient() {
        assertFalse(products.deductStock("P001", 201));// 库存 200，扣 201 被守卫拒绝
        assertEquals(200, products.findById("P001").getStock());// 库存不变
    }

    @Test
    void testAccessBankAccountUpsertOnFirstCredit() {
        assertEquals(null, bankAccounts.findByUserId("student001"));// 前置：账户不存在
        assertTrue(bankAccounts.credit("student001", 10000L));// 首次入账懒创建
        BankAccount account = bankAccounts.findByUserId("student001");
        assertNotNull(account);
        assertEquals(10000L, account.getBalanceCents());
    }

    @Test
    void testAccessCreditAccumulates() {
        bankAccounts.credit("student001", 10000L);
        bankAccounts.credit("student001", 5000L);
        assertEquals(15000L, bankAccounts.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessDebitGuardInsufficientUnchanged() {
        bankAccounts.credit("student001", 10000L);
        assertFalse(bankAccounts.debit("student001", 20000L));// 余额不足
        assertEquals(10000L, bankAccounts.findByUserId("student001").getBalanceCents());// 不变
        assertTrue(bankAccounts.debit("student001", 3000L));// 余额充足
        assertEquals(7000L, bankAccounts.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessSetBalancePersists() {
        bankAccounts.credit("student001", 10000L);
        assertTrue(bankAccounts.setBalance("student001", 250L));
        assertEquals(250L, bankAccounts.findByUserId("student001").getBalanceCents());
        // 重开仓库验证已落盘
        AccessBankAccountRepository reopened = new AccessBankAccountRepository(database);
        assertEquals(250L, reopened.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessLedgerAppendThenFindByUserIdReturnsIt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 4, 10, 30, 0);
        WalletTransaction entry = new WalletTransaction("T001", "student001", WalletTransactionType.RECHARGE,
                5000L, 5000L, "student001", "开学充值", createdAt);

        assertTrue(ledger.append(entry));

        List<WalletTransaction> found = ledger.findByUserId("student001");
        assertEquals(1, found.size());
        WalletTransaction read = found.get(0);
        assertEquals("T001", read.getTransactionId());
        assertEquals(WalletTransactionType.RECHARGE, read.getType());
        assertEquals(5000L, read.getAmountCents());
        assertEquals(5000L, read.getBalanceAfterCents());
        assertEquals("student001", read.getOperatorId());
        assertEquals("开学充值", read.getNote());
        assertEquals(createdAt, read.getCreatedAt());
    }

    @Test
    void testAccessLedgerAppendPersistsSignedAmounts() {
        // 扣款存负数、退款存正数，一段流水可直接累加对账
        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 11, 0, 0);
        assertTrue(ledger.append(new WalletTransaction("T010", "student001", WalletTransactionType.RECHARGE,
                10000L, 10000L, "student001", null, base)));
        assertTrue(ledger.append(new WalletTransaction("T011", "student001", WalletTransactionType.PURCHASE,
                -1980L, 8020L, "student001", "order ORD001", base.plusMinutes(1))));
        long sum = 0L;
        for (WalletTransaction entry : ledger.findByUserId("student001")) {
            sum += entry.getAmountCents();
        }
        assertEquals(8020L, sum);// 流水累加等于末笔余额
    }

    @Test
    void testAccessLedgerNullNoteRoundTripsAsNull() {
        // 备注可空：读回应为 null 而不是空串，区分「没写备注」与「备注是空串」
        assertTrue(ledger.append(new WalletTransaction("T020", "student001", WalletTransactionType.PURCHASE,
                -500L, 0L, "student001", null, LocalDateTime.of(2026, 9, 4, 12, 0, 0))));

        assertNull(ledger.findByUserId("student001").get(0).getNote());
    }

    @Test
    void testAccessLedgerFindByUserIdReturnsEmptyForNoTransactions() {
        assertTrue(ledger.findByUserId("nobody").isEmpty());
    }

    @Test
    void testAccessLedgerFindByUserIdReturnsOnlyOwnTransactions() {
        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 13, 0, 0);
        ledger.append(new WalletTransaction("T030", "student001", WalletTransactionType.RECHARGE,
                1000L, 1000L, "student001", null, base));
        ledger.append(new WalletTransaction("T031", "student002", WalletTransactionType.RECHARGE,
                2000L, 2000L, "student002", null, base));

        List<WalletTransaction> own = ledger.findByUserId("student001");
        assertEquals(1, own.size());
        assertEquals("T030", own.get(0).getTransactionId());
        assertEquals(1, ledger.findByUserId("student002").size());
    }

    @Test
    void testAccessLedgerOrdersByCreatedAtAscending() {
        // 乱序写入，读回必须按记账时间升序，流水页才能按时间轴展示
        LocalDateTime base = LocalDateTime.of(2026, 9, 4, 14, 0, 0);
        ledger.append(new WalletTransaction("T042", "student001", WalletTransactionType.PURCHASE,
                -300L, 700L, "student001", null, base.plusMinutes(20)));
        ledger.append(new WalletTransaction("T040", "student001", WalletTransactionType.RECHARGE,
                1000L, 1000L, "student001", null, base));
        ledger.append(new WalletTransaction("T041", "student001", WalletTransactionType.PURCHASE,
                -500L, 500L, "student001", null, base.plusMinutes(10)));

        List<WalletTransaction> ordered = ledger.findByUserId("student001");
        assertEquals(3, ordered.size());
        assertEquals("T040", ordered.get(0).getTransactionId());
        assertEquals("T041", ordered.get(1).getTransactionId());
        assertEquals("T042", ordered.get(2).getTransactionId());
    }

    @Test
    void testAccessLedgerAppendRejectsDuplicateTransactionId() {
        // 主键冲突按契约返回 false 而不上抛：记账失败绝不能拖垮一笔已成功的资金变动
        WalletTransaction entry = new WalletTransaction("T050", "student001", WalletTransactionType.ADJUST,
                100L, 100L, "manager001", "校正", LocalDateTime.of(2026, 9, 4, 15, 0, 0));
        assertTrue(ledger.append(entry));
        assertFalse(ledger.append(entry));
        assertEquals(1, ledger.findByUserId("student001").size());
    }

    @Test
    void testAccessLedgerAppendReturnsFalseForNull() {
        assertFalse(ledger.append(null));
    }

    @Test
    void testAccessLedgerRecordsOperatorForAdminAdjust() {
        // 管理员校正：operatorId 是管理员而非账户本人，回答「这笔钱是谁改的」
        assertTrue(ledger.append(new WalletTransaction("T060", "student001", WalletTransactionType.ADJUST,
                -5000L, 3000L, "manager001", "退款校正", LocalDateTime.of(2026, 9, 4, 16, 0, 0))));

        WalletTransaction read = ledger.findByUserId("student001").get(0);
        assertEquals("manager001", read.getOperatorId());
        assertEquals("student001", read.getUserId());
        assertEquals(-5000L, read.getAmountCents());
        // 重开仓库验证已落盘
        AccessWalletTransactionRepository reopened = new AccessWalletTransactionRepository(database);
        assertEquals(1, reopened.findByUserId("student001").size());
    }

    private void insertCart(String cartItemId, String userId, String productId, int quantity)
            throws java.sql.SQLException {
        String sql = "INSERT INTO tblCartItem(cart_item_id,user_id,product_id,quantity,added_at) "
                + "VALUES(?,?,?,?,?)";
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + database + ";immediatelyReleaseResources=true");
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cartItemId);
            statement.setString(2, userId);
            statement.setString(3, productId);
            statement.setInt(4, quantity);
            statement.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private static void insertProduct(Connection connection, String productId, String name,
            int stock, double price, String description, String category) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO tblProduct(product_id,name,stock,price,description,category,active) VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, productId);
            statement.setString(2, name);
            statement.setInt(3, stock);
            statement.setDouble(4, price);
            statement.setString(5, description);
            statement.setString(6, category);
            statement.setBoolean(7, true);
            statement.executeUpdate();
        }
    }
}

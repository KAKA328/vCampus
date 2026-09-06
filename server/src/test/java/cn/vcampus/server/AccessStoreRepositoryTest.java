package cn.vcampus.server;

import cn.vcampus.store.BankAccount;
import cn.vcampus.store.CartItem;
import cn.vcampus.store.Order;
import cn.vcampus.store.Product;
import cn.vcampus.store.WalletMutation;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用临时 Access 数据库验证商品和订单数据可以真实保存。 */
class AccessStoreRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    private AccessProductRepository products;
    private AccessOrderRepository orders;
    private AccessCartRepository carts;
    private AccessWalletRepository wallet;
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
                    + "version INTEGER NOT NULL,"
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
        wallet = new AccessWalletRepository(database);
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
        assertEquals(null, wallet.findByUserId("student001"));// 前置：账户不存在
        // 首次入账懒创建：credit 在同一事务内建户 + 记 RECHARGE 流水
        assertTrue(wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null).isApplied());
        BankAccount account = wallet.findByUserId("student001");
        assertNotNull(account);
        assertEquals(10000L, account.getBalanceCents());
    }

    @Test
    void testAccessCreditAccumulates() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        wallet.credit("student001", 5000L, WalletTransactionType.RECHARGE, "student001", null);
        assertEquals(15000L, wallet.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessDebitGuardInsufficientUnchanged() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        assertFalse(wallet.debit("student001", 20000L, WalletTransactionType.PURCHASE, "student001", null).isApplied());// 余额不足
        assertEquals(10000L, wallet.findByUserId("student001").getBalanceCents());// 不变
        assertTrue(wallet.debit("student001", 3000L, WalletTransactionType.PURCHASE, "student001", null).isApplied());// 余额充足
        assertEquals(7000L, wallet.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessSetBalancePersists() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        assertTrue(wallet.setBalance("student001", 250L, WalletTransactionType.ADJUST, "manager001", null).isApplied());
        assertEquals(250L, wallet.findByUserId("student001").getBalanceCents());
        // 重开仓库验证已落盘
        AccessWalletRepository reopened = new AccessWalletRepository(database);
        assertEquals(250L, reopened.findByUserId("student001").getBalanceCents());
    }

    // 新-4 契约（Access 版）：debit 金额必须为正，非正数在开库前即抛 IllegalArgumentException
    @Test
    void testAccessDebitRejectsNonPositiveCents() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        assertThrows(IllegalArgumentException.class,
                () -> wallet.debit("student001", 0L, WalletTransactionType.PURCHASE, "student001", null));
        assertThrows(IllegalArgumentException.class,
                () -> wallet.debit("student001", -100L, WalletTransactionType.PURCHASE, "student001", null));
        assertEquals(10000L, wallet.findByUserId("student001").getBalanceCents());// 余额未被非法调用改动
    }

    // 新-4 契约（Access 版）：credit 金额必须为正
    @Test
    void testAccessCreditRejectsNonPositiveCents() {
        assertThrows(IllegalArgumentException.class,
                () -> wallet.credit("student001", 0L, WalletTransactionType.RECHARGE, "student001", null));
        assertThrows(IllegalArgumentException.class,
                () -> wallet.credit("student001", -100L, WalletTransactionType.RECHARGE, "student001", null));
        assertNull(wallet.findByUserId("student001"));// 非法入账不应建户
    }

    // 新-4 契约（Access 版）：setBalance 目标余额必须非负
    @Test
    void testAccessSetBalanceRejectsNegative() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        assertThrows(IllegalArgumentException.class,
                () -> wallet.setBalance("student001", -1L, WalletTransactionType.ADJUST, "manager001", null));
        assertEquals(10000L, wallet.findByUserId("student001").getBalanceCents());// 余额不变
    }

    // 新-4 边界（Access 版）：setBalance(0) 合法，清零并落盘
    @Test
    void testAccessSetBalanceZeroAllowed() {
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        assertTrue(wallet.setBalance("student001", 0L, WalletTransactionType.ADJUST, "manager001", null).isApplied());
        assertEquals(0L, wallet.findByUserId("student001").getBalanceCents());
        AccessWalletRepository reopenedZero = new AccessWalletRepository(database);
        assertEquals(0L, reopenedZero.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessCreditRecordsLedgerEntryThenFindByUserIdReturnsIt() {
        // credit 在事务内自动记一笔 RECHARGE 流水：流水编号与记账时间由仓储生成
        wallet.credit("student001", 5000L, WalletTransactionType.RECHARGE, "student001", "开学充值");

        List<WalletTransaction> found = wallet.findTransactionsByUserId("student001");
        assertEquals(1, found.size());
        WalletTransaction read = found.get(0);
        assertNotNull(read.getTransactionId());
        assertEquals("student001", read.getUserId());
        assertEquals(WalletTransactionType.RECHARGE, read.getType());
        assertEquals(5000L, read.getAmountCents());
        assertEquals(5000L, read.getBalanceAfterCents());
        assertEquals("student001", read.getOperatorId());
        assertEquals("开学充值", read.getNote());
        assertNotNull(read.getCreatedAt());
    }

    @Test
    void testAccessLedgerPersistsSignedAmounts() {
        // 入账存正数、扣款存负数，一段流水可直接累加对账
        wallet.credit("student001", 10000L, WalletTransactionType.RECHARGE, "student001", null);
        wallet.debit("student001", 1980L, WalletTransactionType.PURCHASE, "student001", "order ORD001");
        long sum = 0L;
        for (WalletTransaction entry : wallet.findTransactionsByUserId("student001")) {
            sum += entry.getAmountCents();
        }
        assertEquals(8020L, sum);// 流水累加等于末笔余额
        assertEquals(8020L, wallet.findByUserId("student001").getBalanceCents());
    }

    @Test
    void testAccessLedgerNullNoteRoundTripsAsNull() {
        // 备注可空：读回应为 null 而不是空串，区分「没写备注」与「备注是空串」
        wallet.credit("student001", 500L, WalletTransactionType.RECHARGE, "student001", null);

        assertNull(wallet.findTransactionsByUserId("student001").get(0).getNote());
    }

    @Test
    void testAccessLedgerFindByUserIdReturnsEmptyForNoTransactions() {
        assertTrue(wallet.findTransactionsByUserId("nobody").isEmpty());
    }

    @Test
    void testAccessLedgerFindByUserIdReturnsOnlyOwnTransactions() {
        wallet.credit("student001", 1000L, WalletTransactionType.RECHARGE, "student001", null);
        wallet.credit("student002", 2000L, WalletTransactionType.RECHARGE, "student002", null);

        List<WalletTransaction> own = wallet.findTransactionsByUserId("student001");
        assertEquals(1, own.size());
        assertEquals("student001", own.get(0).getUserId());
        assertEquals(1, wallet.findTransactionsByUserId("student002").size());
    }

    @Test
    void testAccessLedgerOrdersByCreatedAtAscending() {
        // 连续多笔写入，读回必须按记账时间升序，流水页才能按时间轴展示
        wallet.credit("student001", 1000L, WalletTransactionType.RECHARGE, "student001", null);
        wallet.debit("student001", 300L, WalletTransactionType.PURCHASE, "student001", null);
        wallet.debit("student001", 200L, WalletTransactionType.PURCHASE, "student001", null);

        List<WalletTransaction> ordered = wallet.findTransactionsByUserId("student001");
        assertEquals(3, ordered.size());
        for (int i = 1; i < ordered.size(); i++) {
            assertFalse(ordered.get(i).getCreatedAt().isBefore(ordered.get(i - 1).getCreatedAt()),
                    "流水应按记账时间升序返回");
        }
    }

    @Test
    void testAccessSaveSetsBalanceWithoutLedger() {
        // save 仅置余额、不记流水，供种子数据与测试预置初始余额使用
        assertTrue(wallet.save(new BankAccount("student001", 8888L)));
        assertEquals(8888L, wallet.findByUserId("student001").getBalanceCents());
        assertTrue(wallet.findTransactionsByUserId("student001").isEmpty());// 不记流水
        // 再次 save 走 UPDATE 覆盖余额，仍不记流水
        assertTrue(wallet.save(new BankAccount("student001", 6666L)));
        assertEquals(6666L, wallet.findByUserId("student001").getBalanceCents());
        assertTrue(wallet.findTransactionsByUserId("student001").isEmpty());
    }

    @Test
    void testAccessSaveRejectsNull() {
        assertFalse(wallet.save(null));
    }

    @Test
    void testAccessLedgerRecordsOperatorForAdminAdjust() {
        // 管理员校正：operatorId 是管理员而非账户本人，回答「这笔钱是谁改的」
        wallet.credit("student001", 8000L, WalletTransactionType.RECHARGE, "student001", null);
        wallet.setBalance("student001", 3000L, WalletTransactionType.ADJUST, "manager001", "退款校正");

        List<WalletTransaction> entries = wallet.findTransactionsByUserId("student001");
        assertEquals(2, entries.size());
        // 同毫秒内两笔流水顺序不保证，故按类型查找而不是按下标
        WalletTransaction adjust = null;
        for (WalletTransaction entry : entries) {
            if (entry.getType() == WalletTransactionType.ADJUST)
                adjust = entry;
        }
        assertNotNull(adjust);
        assertEquals("manager001", adjust.getOperatorId());
        assertEquals("student001", adjust.getUserId());
        assertEquals(-5000L, adjust.getAmountCents());// 3000 - 8000 的真实差额
        assertEquals("退款校正", adjust.getNote());
        // 重开仓库验证已落盘
        AccessWalletRepository reopened = new AccessWalletRepository(database);
        assertEquals(2, reopened.findTransactionsByUserId("student001").size());
    }

    // 原子性：流水表缺失时 debit 抛 IllegalStateException，且余额已回滚未变（重开仓库验证）
    @Test
    void testDebitRollsBackBalanceWhenLedgerTableMissing() throws Exception {
        Path broken = createBalanceOnlyDatabase();
        AccessWalletRepository walletWithoutLedger = new AccessWalletRepository(broken);
        walletWithoutLedger.save(new BankAccount("student001", 10000L));// 预置余额（save 不触碰流水表）

        assertThrows(IllegalStateException.class, () -> walletWithoutLedger.debit("student001", 3000L,
                WalletTransactionType.PURCHASE, "student001", "order X"));

        // 余额与流水本应同事务提交，流水写不进去 → 整笔回滚，余额停在原值
        AccessWalletRepository reopened = new AccessWalletRepository(broken);
        assertEquals(10000L, reopened.findByUserId("student001").getBalanceCents());
    }

    // 原子性：流水表缺失时 credit 抛 IllegalStateException，且余额已回滚未变（重开仓库验证）
    @Test
    void testCreditRollsBackBalanceWhenLedgerTableMissing() throws Exception {
        Path broken = createBalanceOnlyDatabase();
        AccessWalletRepository walletWithoutLedger = new AccessWalletRepository(broken);
        walletWithoutLedger.save(new BankAccount("student001", 10000L));

        assertThrows(IllegalStateException.class, () -> walletWithoutLedger.credit("student001", 5000L,
                WalletTransactionType.RECHARGE, "student001", null));

        AccessWalletRepository reopened = new AccessWalletRepository(broken);
        assertEquals(10000L, reopened.findByUserId("student001").getBalanceCents());// 入账已回滚
    }

    // setBalance 在事务内读实际旧值算差额：save(100) 后设 250，流水金额必须是真实差额 150
    @Test
    void testSetBalanceRecordsRealDelta() {
        wallet.save(new BankAccount("student001", 100L));// 预置余额 100 分，save 不记流水

        WalletMutation adjust = wallet.setBalance("student001", 250L, WalletTransactionType.ADJUST, "manager001",
                "校正");

        assertTrue(adjust.isApplied());
        assertEquals(100L, adjust.getBalanceBeforeCents());
        assertEquals(250L, adjust.getBalanceAfterCents());
        List<WalletTransaction> entries = wallet.findTransactionsByUserId("student001");
        assertEquals(1, entries.size());
        assertEquals(150L, entries.get(0).getAmountCents());// 差额 = 250 - 100
        assertEquals(250L, entries.get(0).getBalanceAfterCents());
    }

    // 并发校正对账：多线程同时 setBalance 到不同目标值，串行化后「逐笔流水累加 == 最终余额」
    @Test
    void concurrentSetBalanceKeepsLedgerReconcilable() throws Exception {
        wallet.save(new BankAccount("student001", 0L));
        final long[] targets = { 100L, 200L, 300L, 400L, 500L };
        int threads = targets.length;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < threads; i++) {
            final long target = targets[i];
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    wallet.setBalance("student001", target, WalletTransactionType.ADJUST, "manager001", null);
                }
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        List<WalletTransaction> entries = wallet.findTransactionsByUserId("student001");
        assertEquals(targets.length, entries.size());// 每次校正记一笔
        long ledgerSum = 0L;
        for (WalletTransaction entry : entries) {
            ledgerSum += entry.getAmountCents();
        }
        long finalBalance = wallet.findByUserId("student001").getBalanceCents();
        assertEquals(finalBalance, ledgerSum);// 流水累加恒等于最终余额（对账不变量）
        assertTrue(containsTarget(targets, finalBalance));// 最终余额是某个校正目标值
    }

    private static boolean containsTarget(long[] values, long target) {
        for (long value : values) {
            if (value == target)
                return true;
        }
        return false;
    }

    // 建一个只有 tblBankAccount、没有 tblWalletTransaction 的库，模拟「流水表缺失」的存储故障
    private Path createBalanceOnlyDatabase() throws Exception {
        Path balanceOnly = temporaryDirectory.resolve("wallet-no-ledger.accdb");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + balanceOnly
                        + ";newDatabaseVersion=V2010;immediatelyReleaseResources=true");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE tblBankAccount ("
                    + "user_id VARCHAR(32) NOT NULL,"
                    + "balance_cents BIGINT NOT NULL,"
                    + "PRIMARY KEY (user_id))");
        }
        return balanceOnly;
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
                "INSERT INTO tblProduct(product_id,name,stock,price,description,category,active,version) VALUES(?,?,?,?,?,?,?,?)")) {
            statement.setString(1, productId);
            statement.setString(2, name);
            statement.setInt(3, stock);
            statement.setDouble(4, price);
            statement.setString(5, description);
            statement.setString(6, category);
            statement.setBoolean(7, true);
            statement.setInt(8, 0);
            statement.executeUpdate();
        }
    }
}

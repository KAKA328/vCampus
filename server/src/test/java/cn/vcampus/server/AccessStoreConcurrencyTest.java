package cn.vcampus.server;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import cn.vcampus.store.BankAccount;
import cn.vcampus.store.CartItem;
import cn.vcampus.store.DefaultStoreService;
import cn.vcampus.store.Order;
import cn.vcampus.store.WalletMutation;
import cn.vcampus.store.WalletRepository;
import cn.vcampus.store.WalletTransaction;
import cn.vcampus.store.WalletTransactionType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 Access 库上的商店并发与竞态验收测试。
 *
 * <p>
 * 每个测试用一次性临时库，数据来自 {@code database/schema.sql} + {@code seed.sql} +
 * {@code database/store-test-data.sql}，因此断言的是**限量竞态专项数据在持久化库上的真实行为**，
 * 同时反向验证该脚本可执行、可重复构建。覆盖：两人抢最后一件（P901）、买两件与买一件的顺序对照（P901）、
 * 多人齐射不得超过库存（P903）、高频齐射的累计一致性（P908）、批量结算的库存冲突与失败方可重试（P907）、
 * 子集结算重复条目只结算一次（P907+P908）、扣款被拒后的跨资源回滚（故障注入确定触发）、
 * 余额刚好买得起一次的边界（P910），以及专项脚本的可重复性与「必须全新库」约束。
 *
 * <p>
 * 每个场景都断言最终落盘状态：库存、订单、钱包余额、钱包流水与购物车，全部通过**重新打开的仓储**读取，
 * 确保结论来自数据库文件而不是进程内缓存。
 */
class AccessStoreConcurrencyTest {
    /** 专项脚本预置的限量商品与初始库存，与 database/store-test-data.sql 一一对应。 */
    private static final String LAST_UNIT = "P901";// 库存 1，单价 9.90 元 = 990 分
    private static final String FIVE_PAIRS = "P903";// 库存 5，单价 99.00 元
    private static final String SOLD_OUT = "P905";// 库存 0
    private static final String INACTIVE = "P906";// 已下架
    private static final String CART_RACE = "P907";// 库存 3，单价 2.00 元 = 200 分
    private static final String SALVO = "P908";// 库存 20，单价 0.50 元 = 50 分
    private static final String ONE_YUAN = "P910";// 库存 5，单价 1.00 元 = 100 分
    private static final long LAST_UNIT_CENTS = 990L;
    private static final long TWO_PAIRS_CENTS = 19800L;
    private static final long THREE_BOTTLES_CENTS = 600L;
    private static final long ONE_BOTTLE_CENTS = 200L;
    private static final long ONE_BISCUIT_CENTS = 50L;
    private static final long ONE_BREAD_CENTS = 100L;
    private static final long RICH_BALANCE_CENTS = 100000L;

    @TempDir
    Path temporaryDirectory;

    private Path database;
    private AccessProductRepository products;
    private AccessOrderRepository orders;
    private AccessCartRepository carts;
    private AccessWalletRepository wallet;
    private DefaultStoreService store;

    @BeforeEach
    void setUp() throws Exception {
        database = temporaryDirectory.resolve("store-race.accdb");
        buildDatabase(database);
        products = new AccessProductRepository(database);
        orders = new AccessOrderRepository(database);
        carts = new AccessCartRepository(database);
        wallet = new AccessWalletRepository(database);
        store = new DefaultStoreService(products, orders, carts, wallet);
    }

    // —— 场景 1：两人（多人）抢最后一件，只允许一单成交 ——
    @Test
    void simultaneousBuyersRacingForLastUnitProduceExactlyOneOrder() throws Exception {
        int workers = 6;
        for (int index = 0; index < workers; index++)
            fund(buyer(index), RICH_BALANCE_CENTS);

        List<ServiceResult<Void>> results = simultaneously(
                workers, index -> store.purchase(buyer(index), LAST_UNIT, 1));

        assertStatuses(results, 1, StatusCode.CONFLICT);
        // 落盘状态：库存归零且不为负，订单恰好一笔
        assertEquals(0, persistedStock(LAST_UNIT));
        List<Order> sold = persistedOrders(LAST_UNIT);
        assertEquals(1, sold.size());
        assertEquals(1, sold.get(0).getQuantity());
        assertEquals(9.9d, sold.get(0).getTotalPrice(), 0.001d);
        // 赢家扣款一笔且流水可对账；输家余额、流水、订单一概不动
        for (int index = 0; index < workers; index++) {
            String user = buyer(index);
            boolean won = results.get(index).getStatus() == StatusCode.OK;
            assertEquals(won ? RICH_BALANCE_CENTS - LAST_UNIT_CENTS : RICH_BALANCE_CENTS,
                    persistedBalance(user), user + " 余额");
            List<WalletTransaction> ledger = persistedLedger(user);
            assertEquals(won ? 1 : 0, ledger.size(), user + " 流水笔数");
            if (won) {
                assertEquals(WalletTransactionType.PURCHASE, ledger.get(0).getType());
                assertEquals(-LAST_UNIT_CENTS, ledger.get(0).getAmountCents());
                assertEquals(RICH_BALANCE_CENTS - LAST_UNIT_CENTS, ledger.get(0).getBalanceAfterCents());
                assertEquals(user, sold.get(0).getUserId());
            }
            assertEquals(won ? 1 : 0, persistedOrders(user).size(), user + " 订单数");
        }
    }

    // —— 场景 2：库存只剩 1 件时，「买 2 件」与「买 1 件」同时提交（评审点名的典型竞态） ——
    @Test
    void quantityTwoNeverBeatsQuantityOneForTheLastUnit() throws Exception {
        fund("buyer-two", RICH_BALANCE_CENTS);
        fund("buyer-one", RICH_BALANCE_CENTS);
        final int[] quantities = { 2, 1 };

        List<ServiceResult<Void>> results = simultaneously(
                2, index -> store.purchase(index == 0 ? "buyer-two" : "buyer-one", LAST_UNIT, quantities[index]));

        // 买 2 件者恒被拒（需求量超过库存，绝不部分成交）；买 1 件者恒成功（前者从不扣减库存）
        assertEquals(StatusCode.CONFLICT, results.get(0).getStatus(), results.get(0).getMessage());
        assertEquals(StatusCode.OK, results.get(1).getStatus(), results.get(1).getMessage());
        assertEquals(0, persistedStock(LAST_UNIT));
        List<Order> sold = persistedOrders(LAST_UNIT);
        assertEquals(1, sold.size());
        assertEquals("buyer-one", sold.get(0).getUserId());
        assertEquals(1, sold.get(0).getQuantity());
        assertEquals(RICH_BALANCE_CENTS, persistedBalance("buyer-two"));// 失败方分文未动
        assertTrue(persistedLedger("buyer-two").isEmpty());
        assertEquals(RICH_BALANCE_CENTS - LAST_UNIT_CENTS, persistedBalance("buyer-one"));
    }

    // —— 场景 3：多人各买 2 双抢 5 双库存，最多两人成功且剩余量精确 ——
    @Test
    void simultaneousBuyersCannotTakeMoreThanAvailableStock() throws Exception {
        int workers = 6;
        for (int index = 0; index < workers; index++)
            fund(buyer(index), RICH_BALANCE_CENTS);

        List<ServiceResult<Void>> results = simultaneously(
                workers, index -> store.purchase(buyer(index), FIVE_PAIRS, 2));

        // 库存 5、每人要 2：只能成交 2 人（4 双），剩 1 双
        assertStatuses(results, 2, StatusCode.CONFLICT);
        assertEquals(1, persistedStock(FIVE_PAIRS));
        List<Order> sold = persistedOrders(FIVE_PAIRS);
        assertEquals(2, sold.size());
        int soldUnits = 0;
        for (Order order : sold) {
            assertEquals(2, order.getQuantity());
            soldUnits += order.getQuantity();
        }
        // 核心不变量：初始库存 - 剩余库存 = 成功订单的购买量之和
        assertEquals(5 - 1, soldUnits);
        for (int index = 0; index < workers; index++) {
            String user = buyer(index);
            boolean won = results.get(index).getStatus() == StatusCode.OK;
            assertEquals(won ? RICH_BALANCE_CENTS - TWO_PAIRS_CENTS : RICH_BALANCE_CENTS, persistedBalance(user));
            assertEquals(won ? 1 : 0, persistedLedger(user).size());
        }
    }

    // —— 场景 4：高频齐射的累计一致性（成功订单数 = 库存扣减量 = 扣款流水笔数） ——
    @Test
    void highFrequencySalvoKeepsOrdersEqualToStockDeductions() throws Exception {
        int workers = 8;
        for (int index = 0; index < workers; index++)
            fund(buyer(index), RICH_BALANCE_CENTS);

        List<ServiceResult<Void>> results = simultaneously(
                workers, index -> store.purchase(buyer(index), SALVO, 1));

        assertStatuses(results, workers, null);// 库存 20 足够 8 人各买一包，必须全部成功
        assertEquals(20 - workers, persistedStock(SALVO));
        List<Order> sold = persistedOrders(SALVO);
        assertEquals(workers, sold.size());
        long ledgerEntries = 0L;
        for (int index = 0; index < workers; index++) {
            List<WalletTransaction> own = persistedLedger(buyer(index));
            assertEquals(1, own.size());
            assertEquals(-ONE_BISCUIT_CENTS, own.get(0).getAmountCents());
            ledgerEntries += own.size();
        }
        assertEquals(sold.size(), ledgerEntries);// 一单一笔扣款，无多扣漏扣
        assertEquals(20 - workers, 20 - soldUnits(sold));
    }

    // —— 场景 5：两人同时结算购物车抢最后 3 瓶，失败方购物车必须完好可重试 ——
    @Test
    void concurrentCartCheckoutsCompetingForLastStockLeaveLoserCartRetryable() throws Exception {
        fund("buyer-a", RICH_BALANCE_CENTS);
        fund("buyer-b", RICH_BALANCE_CENTS);
        // 加入购物车不校验库存，两人都能加满 3 瓶；冲突只在结算时被裁决
        assertEquals(StatusCode.OK, store.addToCart("buyer-a", CART_RACE, 3).getStatus());
        assertEquals(StatusCode.OK, store.addToCart("buyer-b", CART_RACE, 3).getStatus());

        List<ServiceResult<Void>> results = simultaneously(2,
                index -> store.checkout(index == 0 ? "buyer-a" : "buyer-b"));

        assertStatuses(results, 1, StatusCode.CONFLICT);
        assertEquals(0, persistedStock(CART_RACE));
        List<Order> sold = persistedOrders(CART_RACE);
        assertEquals(1, sold.size());
        assertEquals(3, sold.get(0).getQuantity());
        String winner = sold.get(0).getUserId();
        String loser = winner.equals("buyer-a") ? "buyer-b" : "buyer-a";
        // 赢家：购物车清空、扣款一笔 CHECKOUT
        assertTrue(persistedCart(winner).isEmpty(), "赢家购物车应已清空");
        assertEquals(RICH_BALANCE_CENTS - THREE_BOTTLES_CENTS, persistedBalance(winner));
        assertEquals(1, persistedLedger(winner).size());
        assertEquals(WalletTransactionType.CHECKOUT, persistedLedger(winner).get(0).getType());
        assertEquals(-THREE_BOTTLES_CENTS, persistedLedger(winner).get(0).getAmountCents());
        // 输家：分文未动、无订单，购物车条目**原样保留**，因此可以直接重试
        assertEquals(RICH_BALANCE_CENTS, persistedBalance(loser));
        assertTrue(persistedLedger(loser).isEmpty());
        assertEquals(0, persistedOrders(loser).size());
        List<CartItem> retained = persistedCart(loser);
        assertEquals(1, retained.size(), "失败方购物车必须保留条目以便重试");
        assertEquals(CART_RACE, retained.get(0).getProductId());
        assertEquals(3, retained.get(0).getQuantity());
        // 失败后重试：库存已为 0，重试仍被拒且不留副作用（不会重复下单）
        assertEquals(StatusCode.CONFLICT, store.checkout(loser).getStatus());
        assertEquals(0, persistedStock(CART_RACE));
        assertEquals(1, persistedOrders(CART_RACE).size());
        assertEquals(1, persistedCart(loser).size());
        assertEquals(RICH_BALANCE_CENTS, persistedBalance(loser));
    }

    // —— 场景 6：子集批量结算中重复的 cartItemId 只结算一次（防重复扣款/扣库存） ——
    @Test
    void subsetCheckoutWithDuplicateCartItemIdSettlesEachItemOnce() throws Exception {
        fund("buyer-subset", RICH_BALANCE_CENTS);
        assertEquals(StatusCode.OK, store.addToCart("buyer-subset", CART_RACE, 3).getStatus());
        assertEquals(StatusCode.OK, store.addToCart("buyer-subset", SALVO, 2).getStatus());
        List<CartItem> lines = store.getCart("buyer-subset").getData();
        assertEquals(2, lines.size());
        String bottleLine = lineFor(lines, CART_RACE).getCartItemId();
        String biscuitLine = lineFor(lines, SALVO).getCartItemId();

        // 故意把矿泉水那条重复传两次：去重后只应结算一次
        ServiceResult<Void> result = store.checkoutItems("buyer-subset",
                Arrays.asList(bottleLine, bottleLine, biscuitLine));

        assertEquals(StatusCode.OK, result.getStatus(), result.getMessage());
        assertEquals(0, persistedStock(CART_RACE));// 3 瓶只扣一次
        assertEquals(18, persistedStock(SALVO));// 2 包只扣一次
        assertEquals(2, persistedOrders(CART_RACE).size() + persistedOrders(SALVO).size());
        // 金额 = 3×200 + 2×50 = 700 分；若重复条目被结算两次会多扣 600 分
        assertEquals(RICH_BALANCE_CENTS - (THREE_BOTTLES_CENTS + 2 * ONE_BISCUIT_CENTS),
                persistedBalance("buyer-subset"));
        List<WalletTransaction> ledger = persistedLedger("buyer-subset");
        assertEquals(2, ledger.size(), "两条购物车条目对应两笔扣款，重复 id 不得产生第三笔");
        long debited = 0L;
        for (WalletTransaction entry : ledger) {
            assertEquals(WalletTransactionType.CHECKOUT, entry.getType());
            debited += entry.getAmountCents();
        }
        assertEquals(-(THREE_BOTTLES_CENTS + 2 * ONE_BISCUIT_CENTS), debited);
        assertTrue(persistedCart("buyer-subset").isEmpty());
    }

    // —— 场景 7：扣款被拒（预检已通过）→ 跨资源回滚：库存回补、订单撤销、退款流水相抵、购物车保留 ——
    // 这条路径在单进程下无法用并发点击复现（checkoutInternal 全程持锁、预检金额与逐项扣款金额相同），
    // 故按评审要求用故障注入确定触发：拒绝第二次 debit，使「第一条目已成交、第二条目扣款失败」必然发生。
    @Test
    void walletDebitRejectionAfterStockDeductionRollsBackStockOrderAndLedger() throws Exception {
        fund("buyer-rollback", RICH_BALANCE_CENTS);
        RejectingWallet rejecting = new RejectingWallet(wallet);
        rejecting.rejectDebitFromCall(2);// 第 2 次 debit 返回 applied=false
        DefaultStoreService injected = new DefaultStoreService(products, orders, carts, rejecting);
        assertEquals(StatusCode.OK, injected.addToCart("buyer-rollback", SALVO, 1).getStatus());
        assertEquals(StatusCode.OK, injected.addToCart("buyer-rollback", CART_RACE, 1).getStatus());

        ServiceResult<Void> result = injected.checkout("buyer-rollback");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertTrue(result.getMessage().contains("rolled back"), result.getMessage());
        // 库存全部回补到初始值：已扣的两件都不能留下扣减痕迹
        assertEquals(20, persistedStock(SALVO));
        assertEquals(3, persistedStock(CART_RACE));
        // 已建订单被撤销，不得留下半成功订单
        assertTrue(persistedOrders(SALVO).isEmpty());
        assertTrue(persistedOrders(CART_RACE).isEmpty());
        // 余额回到原值，流水一扣一退净额为零（对账相抵）
        assertEquals(RICH_BALANCE_CENTS, persistedBalance("buyer-rollback"));
        List<WalletTransaction> ledger = persistedLedger("buyer-rollback");
        assertEquals(2, ledger.size());
        long net = 0L;
        boolean hasCheckout = false;
        boolean hasRefund = false;
        for (WalletTransaction entry : ledger) {
            net += entry.getAmountCents();
            hasCheckout |= entry.getType() == WalletTransactionType.CHECKOUT;
            hasRefund |= entry.getType() == WalletTransactionType.REFUND;
        }
        assertEquals(0L, net, "扣款与退款必须等额相抵");
        assertTrue(hasCheckout && hasRefund, "回滚必须留下退款流水");
        // 购物车条目保留，故障排除后可直接重试
        assertEquals(2, persistedCart("buyer-rollback").size());

        // 失败后重试：钱包恢复正常，同一购物车整单结算成功，状态与流水全部对齐
        rejecting.rejectDebitFromCall(Integer.MAX_VALUE);
        assertEquals(StatusCode.OK, injected.checkout("buyer-rollback").getStatus());
        assertEquals(19, persistedStock(SALVO));
        assertEquals(2, persistedStock(CART_RACE));
        assertEquals(2, persistedOrders(SALVO).size() + persistedOrders(CART_RACE).size());
        assertEquals(RICH_BALANCE_CENTS - (ONE_BISCUIT_CENTS + ONE_BOTTLE_CENTS),
                persistedBalance("buyer-rollback"));
        assertTrue(persistedCart("buyer-rollback").isEmpty());
        long finalNet = 0L;
        for (WalletTransaction entry : persistedLedger("buyer-rollback"))
            finalNet += entry.getAmountCents();
        assertEquals(-(ONE_BISCUIT_CENTS + ONE_BOTTLE_CENTS), finalNet);// 退款被重试的真实扣款抵消
    }

    // —— 场景 8：余额刚好买得起一次（P910 单价 1.00 元 + demo_student_cross 余额 1.00 元） ——
    @Test
    void balanceExactlyAffordsOneUnitThenRejectsSecondPurchase() {
        String poor = "demo_student_cross";// 专项脚本预置 100 分
        assertEquals(ONE_BREAD_CENTS, persistedBalance(poor));

        assertEquals(StatusCode.OK, store.purchase(poor, ONE_YUAN, 1).getStatus());
        assertEquals(4, persistedStock(ONE_YUAN));
        assertEquals(0L, persistedBalance(poor));
        assertEquals(1, persistedOrders(poor).size());
        List<WalletTransaction> afterFirst = persistedLedger(poor);
        assertEquals(1, afterFirst.size());
        assertEquals(WalletTransactionType.PURCHASE, afterFirst.get(0).getType());
        assertEquals(-ONE_BREAD_CENTS, afterFirst.get(0).getAmountCents());
        assertEquals(0L, afterFirst.get(0).getBalanceAfterCents());

        // 第二次：预检阶段即 PAYMENT_REQUIRED，不扣库存、不建单、不记流水
        ServiceResult<Void> second = store.purchase(poor, ONE_YUAN, 1);
        assertEquals(StatusCode.PAYMENT_REQUIRED, second.getStatus());
        assertEquals(4, persistedStock(ONE_YUAN));
        assertEquals(0L, persistedBalance(poor));
        assertEquals(1, persistedOrders(poor).size());
        assertEquals(1, persistedLedger(poor).size());

        // 另一位低余额买家 1.50 元：买一次后剩 0.50 元，同样买不起第二次
        String tight = "demo_student_elective";
        assertEquals(StatusCode.OK, store.purchase(tight, ONE_YUAN, 1).getStatus());
        assertEquals(ONE_BREAD_CENTS / 2, persistedBalance(tight));
        assertEquals(StatusCode.PAYMENT_REQUIRED, store.purchase(tight, ONE_YUAN, 1).getStatus());
        assertEquals(3, persistedStock(ONE_YUAN));

        // 售罄与下架商品的边界：库存为 0 → CONFLICT；已下架 → NOT_FOUND
        fund("buyer-edge", RICH_BALANCE_CENTS);
        assertEquals(StatusCode.CONFLICT, store.purchase("buyer-edge", SOLD_OUT, 1).getStatus());
        assertEquals(StatusCode.NOT_FOUND, store.purchase("buyer-edge", INACTIVE, 1).getStatus());
        assertEquals(0, persistedStock(SOLD_OUT));
        assertTrue(persistedOrders("buyer-edge").isEmpty());
        assertTrue(persistedLedger("buyer-edge").isEmpty());
    }

    // —— 场景 9：专项脚本可重复执行性：全新库每次得到同一初始状态；对已建库重跑必须失败 ——
    @Test
    void specializedScriptBuildsRepeatableFreshDatabaseAndRejectsSecondRun() throws Exception {
        // 本次 setUp 建出的库即为「第一次构建」
        assertEquals(115, count("tblProduct", database));
        assertEquals(8, count("tblBankAccount", database));
        assertEquals(1, persistedStock(LAST_UNIT));
        assertEquals(5, persistedStock(ONE_YUAN));
        assertEquals(0, persistedStock(SOLD_OUT));
        assertEquals(ONE_BREAD_CENTS, persistedBalance("demo_student_cross"));

        // 消耗限量库存后，只有重建才能复位
        fund("buyer-drain", RICH_BALANCE_CENTS);
        assertEquals(StatusCode.OK, store.purchase("buyer-drain", LAST_UNIT, 1).getStatus());
        assertEquals(0, persistedStock(LAST_UNIT));

        Path rebuilt = temporaryDirectory.resolve("store-race-rebuilt.accdb");
        buildDatabase(rebuilt);
        AccessProductRepository reopened = new AccessProductRepository(rebuilt);
        assertEquals(1, reopened.findById(LAST_UNIT).getStock(), "重建即复位到脚本预置库存");
        assertEquals(115, count("tblProduct", rebuilt));

        // 对已经建好的库重复执行专项脚本：主键冲突必须失败，因此验收要求「必须使用全新测试库」
        SQLException conflict = assertThrows(SQLException.class, () -> AccessDatabaseSchemaTest.executeScript(
                rebuilt, AccessDatabaseSchemaTest.readScript("database/store-test-data.sql")));
        assertNotNull(conflict.getMessage());
    }

    // —— 基础设施 ——

    /**
     * 按 schema.sql → seed.sql → store-test-data.sql 的顺序建一次性测试库，与 rebuild.ps1 完全一致。
     */
    private static void buildDatabase(Path target) throws Exception {
        Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        AccessDatabaseSchemaTest.executeScript(target, AccessDatabaseSchemaTest.readScript("database/schema.sql"));
        AccessDatabaseSchemaTest.executeScript(target, AccessDatabaseSchemaTest.readScript("database/seed.sql"));
        AccessDatabaseSchemaTest.executeScript(target,
                AccessDatabaseSchemaTest.readScript("database/store-test-data.sql"));
    }

    /** 预置买家余额：save 只置余额不记流水，供测试构造初始资金状态。 */
    private void fund(String userId, long cents) {
        assertTrue(wallet.save(new BankAccount(userId, cents)), "预置钱包失败：" + userId);
    }

    private static String buyer(int index) {
        return "race-buyer-" + index;
    }

    private static CartItem lineFor(List<CartItem> lines, String productId) {
        for (CartItem line : lines)
            if (line.getProductId().equals(productId))
                return line;
        throw new AssertionError("购物车缺少商品条目：" + productId);
    }

    private static int soldUnits(List<Order> sold) {
        int units = 0;
        for (Order order : sold)
            units += order.getQuantity();
        return units;
    }

    // 以下 persisted* 一律重新打开仓储读取数据库文件，断言落盘结果而非进程内状态

    private int persistedStock(String productId) {
        cn.vcampus.store.Product product = new AccessProductRepository(database).findById(productId);
        assertNotNull(product, "商品必须存在：" + productId);
        assertTrue(product.getStock() >= 0, "库存不得为负：" + productId);
        return product.getStock();
    }

    private List<Order> persistedOrders(String productIdOrUserId) {
        List<Order> result = new ArrayList<Order>();
        for (Order order : new AccessOrderRepository(database).findAll())
            if (order.getProductId().equals(productIdOrUserId) || order.getUserId().equals(productIdOrUserId))
                result.add(order);
        return result;
    }

    private long persistedBalance(String userId) {
        BankAccount account = new AccessWalletRepository(database).findByUserId(userId);
        assertNotNull(account, "钱包账户必须存在：" + userId);
        return account.getBalanceCents();
    }

    private List<WalletTransaction> persistedLedger(String userId) {
        return new AccessWalletRepository(database).findTransactionsByUserId(userId);
    }

    private List<CartItem> persistedCart(String userId) {
        return new AccessCartRepository(database).findByUserId(userId);
    }

    private static int count(String table, Path target) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:ucanaccess://" + target + ";immediatelyReleaseResources=true");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    /** 断言成功笔数，其余必须是指定失败码；expectedFailure 为 null 表示要求全部成功。 */
    private static void assertStatuses(List<ServiceResult<Void>> results, int expectedSuccesses,
            StatusCode expectedFailure) {
        int successes = 0;
        for (ServiceResult<Void> result : results) {
            if (result.getStatus() == StatusCode.OK)
                successes++;
            else if (expectedFailure != null)
                assertEquals(expectedFailure, result.getStatus(), result.getMessage());
            else
                throw new AssertionError("预期全部成功，实际失败：" + result.getMessage());
        }
        assertEquals(expectedSuccesses, successes, "成功笔数不符");
    }

    /** 栅栏式并发：所有请求先到齐，再同时放行，最大化竞态窗口。 */
    private static <T> List<T> simultaneously(int count, IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<Future<T>>();
        try {
            for (int index = 0; index < count; index++) {
                final int requestIndex = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("并发放行信号超时");
                    return action.apply(requestIndex);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "所有请求必须先到达起跑栅栏");
            start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            List<T> results = new ArrayList<T>();
            for (Future<T> future : futures) {
                long remaining = deadline - System.nanoTime();
                assertTrue(remaining > 0, "并发请求超过总时限");
                results.add(future.get(remaining, TimeUnit.NANOSECONDS));
            }
            return results;
        } finally {
            start.countDown();
            for (Future<T> future : futures)
                future.cancel(true);
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "线程池未正常终止");
        }
    }

    /** 钱包故障注入：从第 N 次 debit 起返回 applied=false，确定触发「预检通过但扣款失败」的回滚路径。 */
    private static final class RejectingWallet implements WalletRepository {
        private final WalletRepository delegate;
        private int debitCalls;
        private int rejectFromCall = Integer.MAX_VALUE;

        RejectingWallet(WalletRepository delegate) {
            this.delegate = delegate;
        }

        void rejectDebitFromCall(int callNumber) {
            this.rejectFromCall = callNumber;
        }

        @Override
        public BankAccount findByUserId(String userId) {
            return delegate.findByUserId(userId);
        }

        @Override
        public List<WalletTransaction> findTransactionsByUserId(String userId) {
            return delegate.findTransactionsByUserId(userId);
        }

        @Override
        public boolean save(BankAccount account) {
            return delegate.save(account);
        }

        @Override
        public WalletMutation debit(String userId, long cents, WalletTransactionType type, String operatorId,
                String note) {
            debitCalls++;
            if (debitCalls >= rejectFromCall) {
                BankAccount current = delegate.findByUserId(userId);
                return WalletMutation.rejected(current == null ? 0L : current.getBalanceCents());
            }
            return delegate.debit(userId, cents, type, operatorId, note);
        }

        @Override
        public WalletMutation credit(String userId, long cents, WalletTransactionType type, String operatorId,
                String note) {
            return delegate.credit(userId, cents, type, operatorId, note);
        }

        @Override
        public WalletMutation setBalance(String userId, long newBalanceCents, WalletTransactionType type,
                String operatorId, String note) {
            return delegate.setBalance(userId, newBalanceCents, type, operatorId, note);
        }
    }
}

package cn.vcampus.store;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发不超卖 / 不透支证明。
 * 用 CountDownLatch 让多个线程「同时出发」制造最大竞争，断言最终一致性不变量：
 * 库存 >= 0、累计售出 <= 初始库存、成功订单数与库存扣减一致、各账户余额扣减与成功订单总额（分）相符。
 * DefaultStoreService 的 purchase/checkout 都是 synchronized，共用一把锁把「检查+扣减」串行化，
 * 因此无论线程如何交错，都不会超卖或透支。
 */
class StoreConcurrencyTest {

    // 场景1：库存 2，三人分别直购 1/2/3 件（总需求 6 > 2），断言不超卖
    @Test
    void scenario1ConcurrentPurchaseNeverOversells() throws Exception {
        final InMemoryProductRepository products = new InMemoryProductRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryBankAccountRepository bank = new InMemoryBankAccountRepository();
        final InMemoryCartRepository cart = new InMemoryCartRepository();
        products.save(new Product("HOT", "限量商品", 2, 10.0, "", "test"));
        final String[] users = { "u1", "u2", "u3" };
        final int[] qtys = { 1, 2, 3 };
        for (String user : users) {
            bank.credit(user, 100_000L);// 余额充足，排除余额干扰，专测库存竞态
        }
        final DefaultStoreService service = new DefaultStoreService(products, orders, cart, bank);

        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger okCount = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(users.length);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (int i = 0; i < users.length; i++) {
            final int idx = i;
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    await(start);
                    if (service.purchase(users[idx], "HOT", qtys[idx]).getStatus() == StatusCode.OK)
                        okCount.incrementAndGet();
                }
            }));
        }
        start.countDown();// 同时放行
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        int finalStock = products.findById("HOT").getStock();
        int sold = 2 - finalStock;
        assertTrue(finalStock >= 0, "库存不能为负");
        assertTrue(sold <= 2, "累计售出不能超过初始库存");
        List<Order> all = orders.findAll();
        assertEquals(okCount.get(), all.size(), "成功购买次数应等于订单数");
        int orderedQty = 0;
        for (Order order : all) {
            orderedQty += order.getQuantity();
        }
        assertEquals(sold, orderedQty, "订单总数量应等于库存扣减量");
    }

    // 场景2：库存 3，u3 直购 + u1/u2 结账（各 2 件，总需求 6 > 3），断言不超卖且余额与订单相符
    @Test
    void scenario2MixedPurchaseAndCheckoutStaysConsistent() throws Exception {
        final InMemoryProductRepository products = new InMemoryProductRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryBankAccountRepository bank = new InMemoryBankAccountRepository();
        final InMemoryCartRepository cart = new InMemoryCartRepository();
        products.save(new Product("HOT", "限量商品", 3, 10.0, "", "test"));
        for (String user : new String[] { "u1", "u2", "u3" }) {
            bank.credit(user, 100_000L);
        }
        // u1、u2 走购物车结账，各 2 件
        cart.addItem(new CartItem("c1", "u1", "HOT", 2, LocalDateTime.now()));
        cart.addItem(new CartItem("c2", "u2", "HOT", 2, LocalDateTime.now()));
        final DefaultStoreService service = new DefaultStoreService(products, orders, cart, bank);

        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                service.checkout("u1");
            }
        }));
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                service.checkout("u2");
            }
        }));
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                service.purchase("u3", "HOT", 2);// u3 直购 2 件
            }
        }));
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        int finalStock = products.findById("HOT").getStock();
        int sold = 3 - finalStock;
        assertTrue(finalStock >= 0, "库存不能为负");
        assertTrue(sold <= 3, "累计售出不能超过初始库存");
        int orderedQty = 0;
        for (Order order : orders.findAll()) {
            orderedQty += order.getQuantity();
        }
        assertEquals(sold, orderedQty, "订单总数量应等于库存扣减量");
        // 每个账户余额扣减量 == 其成功订单总额（换算成分）
        for (String user : new String[] { "u1", "u2", "u3" }) {
            long spent = 0L;
            for (Order order : orders.findByUserId(user)) {
                spent += Math.round(order.getTotalPrice() * 100);
            }
            assertEquals(100_000L - spent, bank.findByUserId(user).getBalanceCents(),
                    user + " 余额扣减应与成功订单总额相符");
        }
        // 结账失败方购物车保留（成功才清空）
        for (String user : new String[] { "u1", "u2" }) {
            if (orders.findByUserId(user).isEmpty())
                assertTrue(!cart.findByUserId(user).isEmpty(), user + " 结账失败应保留购物车");
        }
    }

    // 场景3：库存 3，u3 直购但余额不足，断言 u3 不透支、无订单、余额不变，其余不超卖
    @Test
    void scenario3InsufficientBalanceDoesNotOversellOrOverdraw() throws Exception {
        final InMemoryProductRepository products = new InMemoryProductRepository();
        final InMemoryOrderRepository orders = new InMemoryOrderRepository();
        final InMemoryBankAccountRepository bank = new InMemoryBankAccountRepository();
        final InMemoryCartRepository cart = new InMemoryCartRepository();
        products.save(new Product("HOT", "限量商品", 3, 10.0, "", "test"));
        bank.credit("u1", 100_000L);
        bank.credit("u2", 100_000L);
        // u3 余额 0，买 10 元商品必然余额不足
        final DefaultStoreService service = new DefaultStoreService(products, orders, cart, bank);

        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger u3Status = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<?>> futures = new ArrayList<Future<?>>();
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                service.purchase("u1", "HOT", 2);
            }
        }));
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                service.purchase("u2", "HOT", 2);
            }
        }));
        futures.add(pool.submit(new Runnable() {
            @Override
            public void run() {
                await(start);
                ServiceResult<Void> result = service.purchase("u3", "HOT", 1);
                u3Status.set(result.getStatus() == StatusCode.PAYMENT_REQUIRED ? 1 : 0);
            }
        }));
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertEquals(1, u3Status.get(), "u3 余额不足应返回 PAYMENT_REQUIRED");
        assertTrue(orders.findByUserId("u3").isEmpty(), "u3 不应产生订单");
        assertEquals(0L, bank.findByUserId("u3") == null ? 0L : bank.findByUserId("u3").getBalanceCents(),
                "u3 余额不应被透支");
        int finalStock = products.findById("HOT").getStock();
        assertTrue(finalStock >= 0, "库存不能为负");
        int orderedQty = 0;
        for (Order order : orders.findAll()) {
            orderedQty += order.getQuantity();
        }
        assertEquals(3 - finalStock, orderedQty, "订单总数量应等于库存扣减量");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}

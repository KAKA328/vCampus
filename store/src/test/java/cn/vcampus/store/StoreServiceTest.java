package cn.vcampus.store;

import java.util.List;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 商店服务测试
class StoreServiceTest {
    // 商店服务实例
    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final InMemoryCartRepository cartRepo = new InMemoryCartRepository();
    private final InMemoryWalletRepository walletRepo = new InMemoryWalletRepository();
    private final InMemoryStoreService service = new InMemoryStoreService(products, orders, cartRepo, walletRepo);

    StoreServiceTest() {
        products.save(new Product("00001", "Apple", 100, 2.5, "A delicious apple", "Fruit"));
        products.save(new Product("00002", "Banana", 150, 1.5, "A long and yellow banana", "Fruit"));
        products.save(new Product("00003", "Carrot", 200, 0.5, "A orange and crunchy carrot", "Vegetable"));
        products.save(new Product("00004", "Toy Car", 100, 15, "A pastical toy car", "Toy"));
        // 预置充足余额（分），保证既有 purchase/checkout 用例在接入余额后不破
        String[] funded = { "0110", "0111", "0112", "0113", "0115", "0116", "0120", "0121",
                "0201", "0202", "0301", "0302", "0303", "0401", "0402", "0403" };
        for (String userId : funded) {
            walletRepo.save(new BankAccount(userId, 100_000_000L));
        }
    }

    // 测试列表商品返回所有商品
    @Test
    void testListProductsReturnsAllProducts() {
        int targetNum = products.findAll().size();
        ServiceResult<List<Product>> testResult = service.listProducts();
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertEquals(targetNum, testResult.getData().size());
    }

    // 测试购买商品
    @Test
    void testPurchaseSuccess() {
        int currStock = products.findById("00004").getStock();
        ServiceResult<Void> testResult = service.purchase("0120", "00004", 77);
        assertEquals(StatusCode.OK, testResult.getStatus());// 验证状态码
        List<Order> userOrders = orders.findByUserId("0120"); // 获取用户订单列表
        // 验证用户订单列表中是否包含该订单
        boolean isFound = false;
        for (Order order : userOrders) {
            if (order.getProductId().equals("00004") && order.getQuantity() == 77) {
                isFound = true;
                break;
            }
        }
        assertTrue(isFound);
        // 验证存货是否减少
        assertEquals(currStock - 77, products.findById("00004").getStock());
    }

    // 测试购买不存在的商品
    @Test
    void testPurchaseNonExistentProduct() {
        ServiceResult<Void> testResult = service.purchase("0120", "99999999", 1);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试购买商品数量超过库存：库存不足是资源状态冲突，与原子扣减失败统一返回 CONFLICT
    @Test
    void testPurchaseExceedsStock() {
        int toBuy = products.findById("00004").getStock() + 1;
        ServiceResult<Void> testResult = service.purchase("0120", "00004", toBuy);
        assertEquals(StatusCode.CONFLICT, testResult.getStatus());
    }

    // 测试没有订单的学生查询订单不会报错
    @Test
    void testFindOrderByUserIdEmpty() {
        ServiceResult<List<Order>> testResult = service.findOrdersByUserId("9999");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertTrue(testResult.getData().isEmpty());
    }

    // 测试只要购买了商品，就一定能够查到订单
    @Test
    void testFindOrdersByUserIdWithData() {
        int currStock = products.findById("00004").getStock();
        int toBuy = currStock / 2;
        service.purchase("0110", "00004", toBuy);
        ServiceResult<List<Order>> testResult = service.findOrdersByUserId("0110");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(toBuy, testResult.getData().get(0).getQuantity());
        assertEquals("00004", testResult.getData().get(0).getProductId());
        assertEquals(products.findById("00004").getName(), testResult.getData().get(0).getProductName());
    }

    // 测试已经有的商品快照中商品单价、名称不会随着商品信息改变而改变
    @Test
    void testStabilityOfProductSnapshot() {
        String formerName = products.findById("00004").getName();
        double formerPrice = products.findById("00004").getPrice();
        service.purchase("0111", "00004", products.findById("00004").getStock() - 1);
        products.save(new Product("00004", "Toy Plane", 10000, 5, "A pastical toy plane", "Toy"));
        ServiceResult<List<Order>> testResult = service.findOrdersByUserId("0111");
        assertEquals(formerName, testResult.getData().get(0).getProductName());
        assertEquals(formerPrice, testResult.getData().get(0).getUnitPrice());
    }

    // 测试刚好买光一个商品
    @Test
    void testExactlyBuyOut() {
        int currStock = products.findById("00004").getStock();
        ServiceResult<Void> testResult = service.purchase("0112", "00004", currStock);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(0, products.findById("00004").getStock());
    }

    // 测试只买1件商品
    @Test
    void testBuyOne() {
        int currStock = products.findById("00004").getStock();
        ServiceResult<Void> testResult = service.purchase("0113", "00004", 1);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(currStock - 1, products.findById("00004").getStock());
    }

    // 测试购买商品数量为负数
    @Test
    void testPurchaseNegativeQuantity() {
        ServiceResult<Void> testResult = service.purchase("0114", "00004", -1);
        assertEquals(StatusCode.BAD_REQUEST, testResult.getStatus());
    }

    @Test
    void productPriceMustBeFiniteAndPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new Product("bad-zero", "Bad", 1, 0.0, "", "test"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("bad-nan", "Bad", 1, Double.NaN, "", "test"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("bad-infinity", "Bad", 1, Double.POSITIVE_INFINITY, "", "test"));
    }

    @Test
    void serviceRejectsNonFiniteOrNonPositiveProductPrices() {
        assertEquals(StatusCode.BAD_REQUEST, service.addProduct("Zero", 0.0, 1, "", "test").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.addProduct("NaN", Double.NaN, 1, "", "test").getStatus());
        assertEquals(StatusCode.BAD_REQUEST,
                service.addProduct("Infinity", Double.POSITIVE_INFINITY, 1, "", "test").getStatus());
    }

    @Test
    void generatedProductIdFitsDatabaseColumn() {
        Product product = service.addProduct("Generated", 1.0, 1, "", "test").getData();
        assertNotNull(product);
        assertEquals(32, product.getProductId().length());
    }

    @Test
    void purchaseRestoresStockWhenOrderRepositoryThrows() {
        InMemoryProductRepository purchaseProducts = new InMemoryProductRepository();
        purchaseProducts.save(new Product("P", "P", 2, 2.0, "", "test"));
        FailingOrderRepository throwingOrders = new FailingOrderRepository(true);
        InMemoryWalletRepository purchaseWallet = new InMemoryWalletRepository();
        purchaseWallet.save(new BankAccount("u", 100_000L));
        DefaultStoreService purchase = new DefaultStoreService(purchaseProducts, throwingOrders,
                new InMemoryCartRepository(), purchaseWallet);

        ServiceResult<Void> result = purchase.purchase("u", "P", 1);

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(2, purchaseProducts.findById("P").getStock());
        assertEquals(100_000L, purchase.getBalance("u"));// 建单失败已退款
        assertTrue(throwingOrders.findByUserId("u").isEmpty());
    }

    @Test
    void inventoryUpdatesPreserveInactiveProductState() {
        Product inactive = new Product("00005", "下架商品", 8, 3.0, "不可购买", "测试", false);
        products.save(inactive);

        assertTrue(products.updateStock("00005", 5));
        assertFalse(products.findById("00005").isActive());
    }

    @Test
    void productRepositorySupportsInventoryAndCatalogOperations() {
        assertTrue(products.addStock("00001", 7));
        assertEquals(107, products.findById("00001").getStock());

        Product changed = new Product("00001", "Apple Plus", 107, 3.0, "Updated", "Fruit", true);
        assertTrue(products.updateProduct(changed));
        assertEquals("Apple Plus", products.findById("00001").getName());
        assertEquals(3.0, products.findById("00001").getPrice(), 0.001);

        assertTrue(products.deleteById("00001"));
        assertEquals(null, products.findById("00001"));
        assertFalse(products.deleteById("00001"));
    }

    @Test
    void orderRepositoryListsAllOrdersAndSalesVolume() {
        service.purchase("0115", "00001", 2);
        service.purchase("0116", "00001", 3);
        service.purchase("0115", "00002", 1);

        assertEquals(3, orders.findAll().size());
        List<Object[]> sales = orders.findSalesVolume();
        assertEquals(2, sales.size());
        assertEquals("00001", sales.get(0)[0]);
        assertEquals(5, sales.get(0)[1]);
    }

    @Test
    void checkoutRollsBackEarlierItemsWhenAnOrderFails() {
        InMemoryProductRepository checkoutProducts = new InMemoryProductRepository();
        checkoutProducts.save(new Product("A", "A", 3, 2.0, "", "test"));
        checkoutProducts.save(new Product("B", "B", 3, 3.0, "", "test"));
        FailingOrderRepository failingOrders = new FailingOrderRepository();
        InMemoryCartRepository checkoutCart = new InMemoryCartRepository();
        checkoutCart.addItem(new CartItem("cart-a", "u", "A", 1, java.time.LocalDateTime.now()));
        checkoutCart.addItem(new CartItem("cart-b", "u", "B", 1, java.time.LocalDateTime.now()));
        InMemoryWalletRepository checkoutWallet = new InMemoryWalletRepository();
        checkoutWallet.save(new BankAccount("u", 100_000L));

        DefaultStoreService checkout = new DefaultStoreService(checkoutProducts, failingOrders, checkoutCart,
                checkoutWallet);
        ServiceResult<Void> result = checkout.checkout("u");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(3, checkoutProducts.findById("A").getStock());
        assertEquals(3, checkoutProducts.findById("B").getStock());
        assertEquals(100_000L, checkout.getBalance("u"));// 已扣款项全额退回
        assertTrue(failingOrders.findByUserId("u").isEmpty());
        assertEquals(2, checkoutCart.findByUserId("u").size());
    }

    // 测试补货成功
    @Test
    void testRestockSuccess() {
        int formerNum = products.findById("00001").getStock();
        ServiceResult<Void> testResult = service.restock("00001", 100);

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(formerNum + 100, products.findById("00001").getStock());
    }

    // 测试补货商品不存在
    @Test
    void testRestockProductNotFound() {
        ServiceResult<Void> testResult = service.restock("99999999999", 100);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试补货数量为负数
    @Test
    void testRestockNegativeAmount() {
        ServiceResult<Void> testResult = service.restock("00001", -100);
        assertEquals(StatusCode.BAD_REQUEST, testResult.getStatus());
    }

    // 测试新增商品
    @Test
    void testAddProduct() {
        ServiceResult<Product> testResult = service.addProduct("Toy robot", 20.50, 10, "A pastical toy robot", "Toy");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertEquals("Toy robot", testResult.getData().getName());
        assertEquals(20.50, testResult.getData().getPrice(), 0.01);
        assertEquals(10, testResult.getData().getStock());
        assertEquals("A pastical toy robot", testResult.getData().getDescription());
        assertEquals("Toy", testResult.getData().getCategory());
        assertNotNull(testResult.getData().getProductId());
    }

    // 测试新增商品价格非法
    @Test
    void testAddProductInvalidPrice() {
        ServiceResult<Product> testResult = service.addProduct("Toy robot", -20.50, 10, "A pastical toy robot", "Toy");
        assertEquals(StatusCode.BAD_REQUEST, testResult.getStatus());
    }

    // 测试修改商品价格
    @Test
    void testUpdateProductPrice() {
        int formerNum = products.findById("00001").getStock();
        ServiceResult<Product> testResult = service.updateProduct("00001", "Apple", 3.0, "A delicious apple", "Fruit");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(3.0, testResult.getData().getPrice(), 0.01);
        assertEquals(formerNum, testResult.getData().getStock());
    }

    // 测试修改不存在的商品
    @Test
    void testUpdateProductNotFound() {
        ServiceResult<Product> testResult = service.updateProduct("99999999999", "Apple", 3.0, "A delicious apple",
                "Fruit");
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试下架商品
    @Test
    void testDeactivateProduct() {
        ServiceResult<Void> testResult = service.deactivateProduct("00001");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertFalse(products.findById("00001").isActive());
    }

    // 测试下架商品不在列表
    @Test
    void testDeactivatedProductNotListed() {
        service.deactivateProduct("00001");
        ServiceResult<List<Product>> testResult = service.listProducts();
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertFalse(testResult.getData().contains(products.findById("00001")));
    }

    // 测试加入购物车
    @Test
    void testAddToCart() {
        ServiceResult<Void> testResult = service.addToCart("0120", "00001", 1);
        assertEquals(StatusCode.OK, testResult.getStatus());
    }

    // 测试重复加购数量累加
    @Test
    void testAddToCartDuplicateQuantity() {
        service.addToCart("0120", "00001", 1);
        ServiceResult<Void> testResult = service.addToCart("0120", "00001", 1);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(1, cartRepo.findByUserId("0120").size());
        assertEquals(2, cartRepo.findByUserId("0120").get(0).getQuantity());
    }

    // 测试加购不存在的商品
    @Test
    void testAddToCartProductNotFound() {
        ServiceResult<Void> testResult = service.addToCart("0120", "99999999999", 1);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试移除购物车条目
    @Test
    void testRemoveFromCart() {
        service.addToCart("0120", "00001", 1);
        String cartItemId = service.getCart("0120").getData().get(0).getCartItemId();
        ServiceResult<Void> testResult = service.removeFromCart("0120", cartItemId);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(0, cartRepo.findByUserId("0120").size());
    }

    // 测试不能移除他人条目
    @Test
    void testRemoveFromCartNotOwned() {
        service.addToCart("0121", "00001", 1);
        String cartItemId = service.getCart("0121").getData().get(0).getCartItemId();
        ServiceResult<Void> testResult = service.removeFromCart("0120", cartItemId);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
        assertEquals(1, cartRepo.findByUserId("0121").size());
    }

    // 测试查询空购物车
    @Test
    void testGetCartEmpty() {
        ServiceResult<List<CartItem>> testResult = service.getCart("0120");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertTrue(testResult.getData().isEmpty());
    }

    // 测试结账成功
    @Test
    void testCheckoutSuccess() {
        int formerStock1 = products.findById("00001").getStock();
        int formerStock2 = products.findById("00002").getStock();
        double price1 = products.findById("00001").getPrice();
        double price2 = products.findById("00002").getPrice();
        service.addToCart("0120", "00001", 2);
        service.addToCart("0120", "00001", 2);
        service.addToCart("0120", "00002", 10);
        ServiceResult<Void> testResult = service.checkout("0120");
        assertEquals(StatusCode.OK, testResult.getStatus());
        // 验证库存扣减
        assertEquals(formerStock1 - 4, products.findById("00001").getStock());
        assertEquals(formerStock2 - 10, products.findById("00002").getStock());
        // 验证两张订单各自的数量和总价
        assertEquals(2, orders.findByUserId("0120").size());
        boolean appleOrderFound = false;
        boolean bananaOrderFound = false;
        for (Order order : orders.findByUserId("0120")) {
            if (order.getProductId().equals("00001")) {
                assertEquals(4, order.getQuantity());
                assertEquals(4 * price1, order.getTotalPrice(), 0.01);
                appleOrderFound = true;
            }
            if (order.getProductId().equals("00002")) {
                assertEquals(10, order.getQuantity());
                assertEquals(10 * price2, order.getTotalPrice(), 0.01);
                bananaOrderFound = true;
            }
        }
        assertTrue(appleOrderFound);
        assertTrue(bananaOrderFound);
    }

    // 测试结账库存不足：库存不足统一返回 CONFLICT，购物车与库存均不得被修改
    @Test
    void testCheckoutInsufficientStock() {
        int stock = products.findById("00001").getStock();
        service.addToCart("0120", "00001", stock + 1);
        ServiceResult<Void> testResult = service.checkout("0120");
        assertEquals(StatusCode.CONFLICT, testResult.getStatus());
        assertEquals(1, cartRepo.findByUserId("0120").size());
        assertEquals(stock + 1, cartRepo.findByUserId("0120").get(0).getQuantity());
        assertEquals(stock, products.findById("00001").getStock());
    }

    // 测试结账后清空购物车
    @Test
    void testCheckoutClearsCart() {
        service.addToCart("0120", "00001", 2);
        service.addToCart("0120", "00002", 1);
        ServiceResult<Void> testResult = service.checkout("0120");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertTrue(service.getCart("0120").getData().isEmpty());
    }

    // 测试查询所有订单
    @Test
    void testFindAllOrders() {
        service.purchase("0201", "00001", 2);
        service.purchase("0202", "00002", 3);

        ServiceResult<List<Order>> testResult = service.findAllOrders();
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertEquals(2, testResult.getData().size());
    }

    // 测试无订单时查询所有订单
    @Test
    void testFindAllOrdersEmpty() {
        ServiceResult<List<Order>> testResult = service.findAllOrders();
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertTrue(testResult.getData().isEmpty());
    }

    // 测试热销榜排序且不含下架商品
    @Test
    void testListHotProducts() {
        service.purchase("0301", "00001", 5);
        service.purchase("0302", "00002", 3);
        service.purchase("0303", "00003", 1);

        ServiceResult<List<Product>> testResult = service.listHotProducts(10);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(3, testResult.getData().size());
        assertEquals("00001", testResult.getData().get(0).getProductId());
        assertEquals("00002", testResult.getData().get(1).getProductId());

        // 下架后榜单不再包含它
        service.deactivateProduct("00001");
        ServiceResult<List<Product>> afterDeactivate = service.listHotProducts(10);
        assertEquals(2, afterDeactivate.getData().size());
        assertEquals("00002", afterDeactivate.getData().get(0).getProductId());
    }

    // 测试无订单时热销榜为空
    @Test
    void testListHotProductsEmpty() {
        ServiceResult<List<Product>> testResult = service.listHotProducts(5);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertTrue(testResult.getData().isEmpty());
    }

    // 测试热销榜 limit 限制
    @Test
    void testListHotProductsLimit() {
        service.purchase("0401", "00001", 5);
        service.purchase("0402", "00002", 3);
        service.purchase("0403", "00003", 1);

        ServiceResult<List<Product>> testResult = service.listHotProducts(2);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(2, testResult.getData().size());
        assertEquals("00001", testResult.getData().get(0).getProductId());
        assertEquals("00002", testResult.getData().get(1).getProductId());
    }

    // 测试按分类列出商品
    @Test
    void testListProductsByCategory() {
        ServiceResult<List<Product>> testResult = service.listProducts("Fruit");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(2, testResult.getData().size());
        for (Product product : testResult.getData()) {
            assertEquals("Fruit", product.getCategory());
        }
    }

    // 测试分类为 null 列出全部
    @Test
    void testListProductsByNullCategory() {
        int activeCount = service.listProducts().getData().size();
        ServiceResult<List<Product>> testResult = service.listProducts(null);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(activeCount, testResult.getData().size());
    }

    // 测试清空购物车失败时结账回滚
    @Test
    void checkoutRollsBackAndAllowsRetryWhenClearingCartFails() {
        InMemoryProductRepository retryProducts = new InMemoryProductRepository();
        retryProducts.save(new Product("A", "A", 5, 2.0, "", "test"));
        InMemoryOrderRepository retryOrders = new InMemoryOrderRepository();
        InMemoryWalletRepository retryWallet = new InMemoryWalletRepository();
        retryWallet.save(new BankAccount("u", 100_000L));
        FailingCartRepository flakyCart = new FailingCartRepository(true);
        flakyCart.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        DefaultStoreService checkout = new DefaultStoreService(retryProducts, retryOrders, flakyCart, retryWallet);

        ServiceResult<Void> failed = checkout.checkout("u");

        assertEquals(StatusCode.CONFLICT, failed.getStatus());
        assertEquals(5, retryProducts.findById("A").getStock());
        assertTrue(retryOrders.findByUserId("u").isEmpty());
        assertEquals(1, flakyCart.findByUserId("u").size());

        flakyCart.failOnClear = false;
        ServiceResult<Void> retried = checkout.checkout("u");

        assertEquals(StatusCode.OK, retried.getStatus());
        assertEquals(3, retryProducts.findById("A").getStock());
        assertEquals(1, retryOrders.findByUserId("u").size());
        assertTrue(flakyCart.findByUserId("u").isEmpty());
    }

    // 测试并发加购同一商品
    @Test
    void concurrentAddToCartSameProductKeepsSingleLine() throws Exception {
        final int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    service.addToCart("0120", "00001", 1);
                }
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        List<CartItem> lines = cartRepo.findByUserId("0120");
        assertEquals(1, lines.size());
        assertEquals(threads, lines.get(0).getQuantity());
    }

    // 测试余额不足时购买返回 PAYMENT_REQUIRED 且库存不变（预检拦截，未扣库存）
    @Test
    void testPurchaseInsufficientBalanceReturnsPaymentRequiredAndStockUnchanged() {
        int stock = products.findById("00001").getStock();
        ServiceResult<Void> result = service.purchase("broke", "00001", 1);
        assertEquals(StatusCode.PAYMENT_REQUIRED, result.getStatus());
        assertEquals(stock, products.findById("00001").getStock());
        assertTrue(orders.findByUserId("broke").isEmpty());
    }

    // 测试充值后可成功购买，余额按分正确扣减
    @Test
    void testPurchaseAfterRechargeSucceeds() {
        assertEquals(StatusCode.PAYMENT_REQUIRED, service.purchase("broke2", "00001", 1).getStatus());
        assertEquals(StatusCode.OK, service.recharge("broke2", 100_000L).getStatus());
        ServiceResult<Void> result = service.purchase("broke2", "00001", 1);
        assertEquals(StatusCode.OK, result.getStatus());
        assertEquals(100_000L - 250L, service.getBalance("broke2"));// 00001 单价 2.5 元 = 250 分
    }

    // 测试结账余额不足时回滚：返回 PAYMENT_REQUIRED、库存不变、购物车保留
    @Test
    void testCheckoutInsufficientBalanceRollsBack() {
        service.addToCart("broke3", "00001", 2);
        ServiceResult<Void> result = service.checkout("broke3");
        assertEquals(StatusCode.PAYMENT_REQUIRED, result.getStatus());
        assertEquals(100, products.findById("00001").getStock());
        assertEquals(1, cartRepo.findByUserId("broke3").size());
        assertTrue(orders.findByUserId("broke3").isEmpty());
    }

    // 测试管理员校正余额生效（绝对设置）
    @Test
    void testAdjustBalanceByAdminTakesEffect() {
        assertEquals(StatusCode.OK, service.adjustBalance("admin", "0120", 50_000L).getStatus());
        assertEquals(50_000L, service.getBalance("0120"));
    }

    // 测试充值拒绝非正金额
    @Test
    void testRechargeRejectsNonPositive() {
        assertEquals(StatusCode.BAD_REQUEST, service.recharge("0120", 0L).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.recharge("0120", -100L).getStatus());
    }

    // 测试无账户查询余额返回 0
    @Test
    void testGetBalanceReturnsZeroForUnknownUser() {
        assertEquals(0L, service.getBalance("no-such-user"));
    }

    // 补偿失败1：清空购物车抛异常 → 回滚 → CONFLICT，库存/余额/订单均无残留
    @Test
    void testCheckoutClearCartFailureRollsBack() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        InMemoryOrderRepository o = new InMemoryOrderRepository();
        InMemoryWalletRepository b = new InMemoryWalletRepository();
        b.save(new BankAccount("u", 100_000L));
        FailingCartRepository c = new FailingCartRepository(true);
        c.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        DefaultStoreService svc = new DefaultStoreService(p, o, c, b);

        ServiceResult<Void> result = svc.checkout("u");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(5, p.findById("A").getStock());
        assertTrue(o.findByUserId("u").isEmpty());
        assertEquals(100_000L, svc.getBalance("u"));
        assertEquals(1, c.findByUserId("u").size());
    }

    // 补偿失败2：原子扣款失败 → 回补库存 → CONFLICT（预检放行但 debit 被拒）
    @Test
    void testDebitFailureRestoresStock() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        InMemoryOrderRepository o = new InMemoryOrderRepository();
        InMemoryCartRepository c = new InMemoryCartRepository();
        c.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        FailingWalletRepository b = new FailingWalletRepository();
        b.reportedBalance = 100_000L;// 预检读到足够余额
        b.failOnDebit = true;// 但原子扣款被拒（applied=false）
        DefaultStoreService svc = new DefaultStoreService(p, o, c, b);

        ServiceResult<Void> result = svc.checkout("u");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(5, p.findById("A").getStock());// 库存已回补
        assertTrue(o.findByUserId("u").isEmpty());
        assertEquals(1, c.findByUserId("u").size());
    }

    // 补偿失败3：建单抛异常 → 退款 + 回补库存 → CONFLICT
    @Test
    void testCreateOrderFailureRefundsAndRestores() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        FailingOrderRepository o = new FailingOrderRepository(true);// create 抛异常
        InMemoryCartRepository c = new InMemoryCartRepository();
        c.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        InMemoryWalletRepository b = new InMemoryWalletRepository();
        b.save(new BankAccount("u", 100_000L));
        DefaultStoreService svc = new DefaultStoreService(p, o, c, b);

        ServiceResult<Void> result = svc.checkout("u");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(5, p.findById("A").getStock());// 库存回补
        assertEquals(100_000L, svc.getBalance("u"));// 余额退款
        assertTrue(o.findByUserId("u").isEmpty());
    }

    // 补偿失败4：退款 credit 本身失败 → 补偿不完整，升级 SERVER_ERROR 并留痕待人工对账（余额被扣未退）
    @Test
    void testRefundCreditFailureReturnsServerError() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        FailingOrderRepository o = new FailingOrderRepository(true);// create 抛异常 → 触发退款
        InMemoryCartRepository c = new InMemoryCartRepository();
        c.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        FailingWalletRepository b = new FailingWalletRepository();
        b.save(new BankAccount("u", 100_000L));// 真实余额
        b.failOnCredit = true;// 但退款 credit 失败
        DefaultStoreService svc = new DefaultStoreService(p, o, c, b);

        ServiceResult<Void> result = svc.checkout("u");

        assertEquals(StatusCode.SERVER_ERROR, result.getStatus());// 补偿不完整不再伪装成可重试的 CONFLICT
        assertEquals(5, p.findById("A").getStock());// 库存回补成功
        assertEquals(99_600L, b.findByUserId("u").getBalanceCents());// 退款失败，余额停留在已扣状态
        // 落一条待人工对账的记录：refund 步骤失败，含受影响用户与待退金额
        List<CompensationFailure> failures = svc.compensationFailures();
        assertEquals(1, failures.size());
        assertEquals("checkout", failures.get(0).getOperation());
        assertEquals("refund", failures.get(0).getFailedStep());
        assertEquals("u", failures.get(0).getUserId());
        assertEquals(400L, failures.get(0).getAmountCents());
    }

    // 补偿失败5：回补库存本身失败 → 补偿不完整，升级 SERVER_ERROR 并留痕待人工对账
    @Test
    void testRestoreStockFailureReturnsServerError() {
        FailingProductRepository p = new FailingProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        p.failOnAddStock = true;// 回补库存失败
        InMemoryOrderRepository o = new InMemoryOrderRepository();
        FailingWalletRepository b = new FailingWalletRepository();
        b.reportedBalance = 100_000L;// 预检通过
        b.failOnDebit = true;// 扣款被拒 → 触发回补库存
        DefaultStoreService svc = new DefaultStoreService(p, o, new InMemoryCartRepository(), b);

        ServiceResult<Void> result = svc.purchase("u", "A", 2);

        assertEquals(StatusCode.SERVER_ERROR, result.getStatus());// 回补失败不再伪装成可重试的 CONFLICT
        assertTrue(o.findByUserId("u").isEmpty());
        // 落一条待人工对账的记录：restore_stock 步骤失败，含商品与待回补数量
        List<CompensationFailure> failures = svc.compensationFailures();
        assertEquals(1, failures.size());
        assertEquals("purchase", failures.get(0).getOperation());
        assertEquals("restore_stock", failures.get(0).getFailedStep());
        assertEquals("A", failures.get(0).getProductId());
        assertEquals(2, failures.get(0).getQuantity());
    }

    // 补偿失败6：购买建单抛异常触发退款，但退款 credit 失败 → 补偿不完整，升级 SERVER_ERROR 并留痕
    @Test
    void testPurchaseRefundFailureReturnsServerError() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        FailingOrderRepository o = new FailingOrderRepository(true);// create 抛异常 → 触发退款 + 回补
        FailingWalletRepository b = new FailingWalletRepository();
        b.save(new BankAccount("u", 100_000L));// 真实余额
        b.failOnCredit = true;// 退款 credit 失败
        DefaultStoreService svc = new DefaultStoreService(p, o, new InMemoryCartRepository(), b);

        ServiceResult<Void> result = svc.purchase("u", "A", 2);

        assertEquals(StatusCode.SERVER_ERROR, result.getStatus());
        assertTrue(o.findByUserId("u").isEmpty());// 订单未建成
        assertEquals(5, p.findById("A").getStock());// 库存已回补
        assertEquals(99_600L, b.findByUserId("u").getBalanceCents());// 扣款成功但退款失败，余额停留在已扣状态
        List<CompensationFailure> failures = svc.compensationFailures();
        assertEquals(1, failures.size());
        assertEquals("purchase", failures.get(0).getOperation());
        assertEquals("refund", failures.get(0).getFailedStep());
        assertEquals(400L, failures.get(0).getAmountCents());
    }

    // 唯一业务编号：建单成功但清空购物车失败 → 回滚 → 重试，无重复 orderId，库存/余额只按成功次数扣减
    @Test
    void testRetryAfterRollbackProducesNoDuplicateOrderId() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        InMemoryOrderRepository o = new InMemoryOrderRepository();
        InMemoryWalletRepository b = new InMemoryWalletRepository();
        b.save(new BankAccount("u", 100_000L));
        FailingCartRepository c = new FailingCartRepository(true);
        c.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        DefaultStoreService svc = new DefaultStoreService(p, o, c, b);

        ServiceResult<Void> first = svc.checkout("u");
        assertEquals(StatusCode.CONFLICT, first.getStatus());
        assertTrue(o.findByUserId("u").isEmpty());// 订单已撤销
        assertEquals(5, p.findById("A").getStock());// 库存回补
        assertEquals(100_000L, b.findByUserId("u").getBalanceCents());// 余额退款

        c.failOnClear = false;
        ServiceResult<Void> second = svc.checkout("u");
        assertEquals(StatusCode.OK, second.getStatus());
        List<Order> userOrders = o.findByUserId("u");
        assertEquals(1, userOrders.size());// 只有一张订单，无重复
        assertNotNull(userOrders.get(0).getOrderId());
        assertEquals(3, p.findById("A").getStock());// 库存只按成功一次扣减
        assertEquals(100_000L - 400L, b.findByUserId("u").getBalanceCents());// 余额只按成功一次扣减
    }

    // 修改购物车条目数量：改自己的条目成功，仓库中数量同步更新
    @Test
    void testUpdateCartQuantitySuccess() {
        service.addToCart("0120", "00001", 2);
        String cartItemId = service.getCart("0120").getData().get(0).getCartItemId();

        ServiceResult<Void> testResult = service.updateCartQuantity("0120", cartItemId, 5);

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(5, cartRepo.findByUserId("0120").get(0).getQuantity());
    }

    // 越权防护：拿他人 cartItemId 改数量一律按不存在处理，且他人购物车不得被改动
    @Test
    void testUpdateCartQuantityRejectsOtherUsersItem() {
        service.addToCart("0120", "00001", 2);
        String victimItemId = service.getCart("0120").getData().get(0).getCartItemId();

        ServiceResult<Void> testResult = service.updateCartQuantity("0121", victimItemId, 99);

        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
        assertEquals(2, cartRepo.findByUserId("0120").get(0).getQuantity());
        assertTrue(cartRepo.findByUserId("0121").isEmpty());
    }

    // 新数量必须为正，否则 BAD_REQUEST 且不触碰仓库
    @Test
    void testUpdateCartQuantityRejectsNonPositive() {
        service.addToCart("0120", "00001", 2);
        String cartItemId = service.getCart("0120").getData().get(0).getCartItemId();

        assertEquals(StatusCode.BAD_REQUEST, service.updateCartQuantity("0120", cartItemId, 0).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.updateCartQuantity("0120", cartItemId, -3).getStatus());
        assertEquals(2, cartRepo.findByUserId("0120").get(0).getQuantity());
    }

    // 条目编号不存在时返回 NOT_FOUND，与 removeFromCart 的语义保持一致
    @Test
    void testUpdateCartQuantityRejectsUnknownItem() {
        ServiceResult<Void> testResult = service.updateCartQuantity("0120", "cart-does-not-exist", 3);

        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 购物车明细携带商品名、单价（分）与小计（分），前端无需再查一次商品
    @Test
    void testCartDetailsCarryProductNameAndSubtotal() {
        service.addToCart("0120", "00001", 3);

        ServiceResult<List<CartLine>> testResult = service.getCartDetails("0120");

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(1, testResult.getData().size());
        CartLine line = testResult.getData().get(0);
        assertEquals("00001", line.getProductId());
        assertEquals("Apple", line.getProductName());
        assertEquals(250L, line.getUnitPriceCents());
        assertEquals(3, line.getQuantity());
        assertEquals(750L, line.getSubtotalCents());
        assertTrue(line.isActive());
    }

    // 读取时联表的核心价值：商品调价后购物车明细立刻显示新价，无需数据迁移
    @Test
    void testCartDetailsReflectPriceChangeImmediately() {
        service.addToCart("0120", "00001", 2);
        service.updateProduct("00001", "Apple", 3.0, "A delicious apple", "Fruit");

        CartLine line = service.getCartDetails("0120").getData().get(0);

        assertEquals(300L, line.getUnitPriceCents());
        assertEquals(600L, line.getSubtotalCents());
    }

    // 明细带在售标记，前端可据此灰显已下架商品，无需额外一次商品查询
    @Test
    void testCartDetailsMarksDeactivatedProduct() {
        service.addToCart("0120", "00001", 1);
        service.deactivateProduct("00001");

        CartLine line = service.getCartDetails("0120").getData().get(0);

        assertFalse(line.isActive());
    }

    // 商品被物理删除后明细行直接跳过：没有名称与价格可用，留一行空壳反而误导
    @Test
    void testCartDetailsSkipDeletedProduct() {
        service.addToCart("0120", "00001", 1);
        products.deleteById("00001");

        assertEquals(1, cartRepo.findByUserId("0120").size());
        assertTrue(service.getCartDetails("0120").getData().isEmpty());
    }

    // 对账口径：明细小计必须等于结账实扣金额；unitPriceCents × quantity 会因四舍五入少一分，
    // 因此前端合计只能累加 subtotalCents
    @Test
    void testCartDetailsSubtotalMatchesCheckoutDebit() {
        products.save(new Product("ROUND", "Rounding item", 10, 1.005, "", "test"));
        walletRepo.save(new BankAccount("round_user", 100_000L));
        service.addToCart("round_user", "ROUND", 3);

        CartLine line = service.getCartDetails("round_user").getData().get(0);
        assertEquals(100L, line.getUnitPriceCents());
        assertEquals(301L, line.getSubtotalCents());
        assertEquals(300L, line.getUnitPriceCents() * line.getQuantity());// 单价乘数量少一分，不可用于合计

        long balanceBefore = walletRepo.findByUserId("round_user").getBalanceCents();
        assertEquals(StatusCode.OK, service.checkout("round_user").getStatus());
        assertEquals(balanceBefore - line.getSubtotalCents(), walletRepo.findByUserId("round_user").getBalanceCents());
    }

    // 充值成功后记一笔 RECHARGE 流水：金额为正，操作者是本人，余额快照与账户一致
    @Test
    void testRechargeRecordsLedgerEntry() {
        ServiceResult<Void> testResult = service.recharge("ledger_user", 5000L);

        assertEquals(StatusCode.OK, testResult.getStatus());
        List<WalletTransaction> entries = walletRepo.findTransactionsByUserId("ledger_user");
        assertEquals(1, entries.size());
        WalletTransaction entry = entries.get(0);
        assertEquals(WalletTransactionType.RECHARGE, entry.getType());
        assertEquals(5000L, entry.getAmountCents());
        assertEquals(5000L, entry.getBalanceAfterCents());
        assertEquals("ledger_user", entry.getOperatorId());
    }

    // 购买成功后记一笔 PURCHASE 流水：金额为负，备注带上订单编号，可双向追溯
    @Test
    void testPurchaseRecordsLedgerEntryWithOrderReference() {
        walletRepo.save(new BankAccount("ledger_user", 100_000L));

        assertEquals(StatusCode.OK, service.purchase("ledger_user", "00001", 2).getStatus());

        List<WalletTransaction> entries = walletRepo.findTransactionsByUserId("ledger_user");
        assertEquals(1, entries.size());
        WalletTransaction entry = entries.get(0);
        assertEquals(WalletTransactionType.PURCHASE, entry.getType());
        assertEquals(-500L, entry.getAmountCents());// Apple 2.5 元 × 2 件
        assertEquals(100_000L - 500L, entry.getBalanceAfterCents());
        String orderId = orders.findByUserId("ledger_user").get(0).getOrderId();
        assertEquals("order " + orderId, entry.getNote());
    }

    // 管理员校正余额：流水记下操作者编号，让「谁改的」不再随参数被丢弃
    @Test
    void testAdjustBalanceRecordsOperatorId() {
        walletRepo.save(new BankAccount("ledger_user", 10_000L));

        ServiceResult<Void> testResult = service.adjustBalance("admin001", "ledger_user", 25_000L);

        assertEquals(StatusCode.OK, testResult.getStatus());
        List<WalletTransaction> entries = walletRepo.findTransactionsByUserId("ledger_user");
        assertEquals(1, entries.size());
        assertEquals(WalletTransactionType.ADJUST, entries.get(0).getType());
        assertEquals(15_000L, entries.get(0).getAmountCents());// 25000 - 10000 的差额
        assertEquals(25_000L, entries.get(0).getBalanceAfterCents());
        assertEquals("admin001", entries.get(0).getOperatorId());
    }

    // 操作者编号为空即拒绝，避免出现一条查不到责任人的校正流水
    @Test
    void testAdjustBalanceRejectsBlankAdminId() {
        assertEquals(StatusCode.BAD_REQUEST, service.adjustBalance("  ", "0120", 100L).getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.adjustBalance(null, "0120", 100L).getStatus());
    }

    // 补偿留痕：建单失败后流水留下一正一负两笔，累加为 0，余额复原
    @Test
    void testCompensationLeavesOffsettingLedgerEntries() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        FailingOrderRepository o = new FailingOrderRepository(true);
        InMemoryWalletRepository wallet = new InMemoryWalletRepository();
        wallet.save(new BankAccount("u", 100_000L));
        DefaultStoreService svc = new DefaultStoreService(p, o, new InMemoryCartRepository(), wallet);

        assertEquals(StatusCode.CONFLICT, svc.purchase("u", "A", 2).getStatus());

        List<WalletTransaction> entries = wallet.findTransactionsByUserId("u");
        assertEquals(2, entries.size());
        // 同毫秒内的两笔流水排序不保证与业务顺序一致，故按类型查找而不是按下标
        WalletTransaction purchaseEntry = null;
        WalletTransaction refundEntry = null;
        long netCents = 0L;
        for (WalletTransaction entry : entries) {
            netCents += entry.getAmountCents();
            if (entry.getType() == WalletTransactionType.PURCHASE)
                purchaseEntry = entry;
            if (entry.getType() == WalletTransactionType.REFUND)
                refundEntry = entry;
        }
        assertNotNull(purchaseEntry);
        assertNotNull(refundEntry);
        assertEquals(-400L, purchaseEntry.getAmountCents());
        assertEquals(400L, refundEntry.getAmountCents());
        assertEquals(0L, netCents);// 账面正负相抵
        assertEquals(100_000L, wallet.findByUserId("u").getBalanceCents());// 余额已复原
    }

    // 结账逐件记账：两件商品产生两笔 CHECKOUT 流水，金额之和等于实际扣款
    @Test
    void testCheckoutRecordsOneLedgerEntryPerItem() {
        walletRepo.save(new BankAccount("ledger_user", 100_000L));
        service.addToCart("ledger_user", "00001", 2);
        service.addToCart("ledger_user", "00002", 3);

        assertEquals(StatusCode.OK, service.checkout("ledger_user").getStatus());

        List<WalletTransaction> entries = walletRepo.findTransactionsByUserId("ledger_user");
        assertEquals(2, entries.size());
        long totalCents = 0L;
        for (WalletTransaction entry : entries) {
            assertEquals(WalletTransactionType.CHECKOUT, entry.getType());
            totalCents += entry.getAmountCents();
        }
        assertEquals(-950L, totalCents);// Apple 2.5×2=500 分，Banana 1.5×3=450 分
        assertEquals(100_000L - 950L, walletRepo.findByUserId("ledger_user").getBalanceCents());
    }

    // 流水写失败即回滚：钱包存储故障（余额与流水同事务写不进去）时购买返回 SERVER_ERROR，
    // 余额未变、无订单、无流水、库存已回补，绝不静默成功
    @Test
    void testLedgerFailureRollsBackPurchase() {
        InMemoryProductRepository p = new InMemoryProductRepository();
        p.save(new Product("A", "A", 5, 2.0, "", "test"));
        InMemoryOrderRepository o = new InMemoryOrderRepository();
        FailingWalletRepository wallet = new FailingWalletRepository();
        wallet.save(new BankAccount("u", 100_000L));
        wallet.throwOnDebit = true;// 扣款（余额+流水同事务）写失败 → 抛 IllegalStateException
        DefaultStoreService svc = new DefaultStoreService(p, o, new InMemoryCartRepository(), wallet);

        ServiceResult<Void> result = svc.purchase("u", "A", 2);
        assertEquals(StatusCode.SERVER_ERROR, result.getStatus());
        assertEquals(5, p.findById("A").getStock());// 库存已回补
        assertTrue(o.findByUserId("u").isEmpty());// 无订单
        assertEquals(100_000L, wallet.findByUserId("u").getBalanceCents());// 余额未变
        assertTrue(wallet.findTransactionsByUserId("u").isEmpty());// 无流水
    }

    // 流水按用户隔离：查自己的账看不到别人的流水
    @Test
    void testListTransactionsIsolatedPerUser() {
        service.recharge("ledger_a", 1000L);
        service.recharge("ledger_b", 2000L);

        ServiceResult<List<WalletTransaction>> testResult = service.listTransactions("ledger_a");

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(1, testResult.getData().size());
        assertEquals("ledger_a", testResult.getData().get(0).getUserId());
    }

    // 用户编号为空即拒绝，避免返回全表流水
    @Test
    void testListTransactionsRejectsBlankUserId() {
        assertEquals(StatusCode.BAD_REQUEST, service.listTransactions("  ").getStatus());
        assertEquals(StatusCode.BAD_REQUEST, service.listTransactions(null).getStatus());
    }

    // 无流水的用户返回空列表而非 null，前端可直接遍历
    @Test
    void testListTransactionsReturnsEmptyForUnknownUser() {
        ServiceResult<List<WalletTransaction>> testResult = service.listTransactions("nobody");

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertTrue(testResult.getData().isEmpty());
    }

    // 防御性拷贝：查询结果不可被调用方改写，避免绕过服务层直接篡改仓库数据
    @Test
    void testQueryResultsAreUnmodifiable() {
        service.addToCart("0120", "00001", 1);
        service.purchase("0120", "00002", 1);
        service.recharge("0120", 100L);

        assertThrows(UnsupportedOperationException.class, () -> service.getCart("0120").getData().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.getCartDetails("0120").getData().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> service.findOrdersByUserId("0120").getData().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.findAllOrders().getData().clear());
        assertThrows(UnsupportedOperationException.class, () -> service.listTransactions("0120").getData().clear());
    }

    private static final class FailingOrderRepository implements OrderRepository {
        private final InMemoryOrderRepository delegate = new InMemoryOrderRepository();
        private int createCount;
        private final boolean throwOnCreate;

        private FailingOrderRepository() {
            this(false);
        }

        private FailingOrderRepository(boolean throwOnCreate) {
            this.throwOnCreate = throwOnCreate;
        }

        @Override
        public boolean create(Order order) {
            if (throwOnCreate)
                throw new IllegalStateException("database unavailable");
            createCount++;
            return createCount == 2 ? false : delegate.create(order);
        }

        @Override
        public boolean deleteById(String orderId) {
            return delegate.deleteById(orderId);
        }

        @Override
        public List<Order> findByUserId(String userId) {
            return delegate.findByUserId(userId);
        }

        @Override
        public List<Order> findAll() {
            return delegate.findAll();
        }

        @Override
        public List<Object[]> findSalesVolume() {
            return delegate.findSalesVolume();
        }
    }

    private static final class FailingCartRepository implements CartRepository {
        private final InMemoryCartRepository delegate = new InMemoryCartRepository();
        private boolean failOnClear;

        private FailingCartRepository(boolean failOnClear) {
            this.failOnClear = failOnClear;
        }

        @Override
        public List<CartItem> findByUserId(String userId) {
            return delegate.findByUserId(userId);
        }

        @Override
        public boolean addItem(CartItem item) {
            return delegate.addItem(item);
        }

        @Override
        public boolean removeItem(String cartItemId) {
            return delegate.removeItem(cartItemId);
        }

        @Override
        public boolean updateQuantity(String cartItemId, int newQuantity) {
            return delegate.updateQuantity(cartItemId, newQuantity);
        }

        @Override
        public void clearByUserId(String userId) {
            if (failOnClear)
                throw new IllegalStateException("cart storage unavailable");
            delegate.clearByUserId(userId);
        }
    }

    // 可控钱包替身：能注入 debit/credit 存储故障（抛 IllegalStateException）或业务拒绝（applied=false），
    // 或用 reportedBalance 让预检读到虚假余额
    private static final class FailingWalletRepository implements WalletRepository {
        private final InMemoryWalletRepository delegate = new InMemoryWalletRepository();
        private boolean failOnDebit;// debit 返回 applied=false（模拟余额不足被守卫拒绝）
        private boolean failOnCredit;// credit 返回 applied=false
        private boolean throwOnDebit;// debit 抛 IllegalStateException（模拟余额与流水同事务写失败）
        private long reportedBalance = -1L;// -1 表示用 delegate 真实余额

        @Override
        public BankAccount findByUserId(String userId) {
            if (reportedBalance >= 0)
                return new BankAccount(userId, reportedBalance);
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
            if (throwOnDebit)
                throw new IllegalStateException("ledger write failed");
            if (failOnDebit)
                return WalletMutation.rejected(currentBalance(userId));
            return delegate.debit(userId, cents, type, operatorId, note);
        }

        @Override
        public WalletMutation credit(String userId, long cents, WalletTransactionType type, String operatorId,
                String note) {
            if (failOnCredit)
                return WalletMutation.rejected(currentBalance(userId));
            return delegate.credit(userId, cents, type, operatorId, note);
        }

        @Override
        public WalletMutation setBalance(String userId, long newBalanceCents, WalletTransactionType type,
                String operatorId, String note) {
            return delegate.setBalance(userId, newBalanceCents, type, operatorId, note);
        }

        private long currentBalance(String userId) {
            BankAccount account = delegate.findByUserId(userId);
            return account == null ? 0L : account.getBalanceCents();
        }
    }

    // 可控商品替身：能单独注入 addStock（回补库存）失败，deductStock 仍正常
    private static final class FailingProductRepository implements ProductRepository {
        private final InMemoryProductRepository delegate = new InMemoryProductRepository();
        private boolean failOnAddStock;

        @Override
        public List<Product> findAll() {
            return delegate.findAll();
        }

        @Override
        public Product findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public void save(Product product) {
            delegate.save(product);
        }

        @Override
        public boolean updateStock(String productId, int newStock) {
            return delegate.updateStock(productId, newStock);
        }

        @Override
        public boolean addStock(String productId, int amount) {
            if (failOnAddStock)
                return false;
            return delegate.addStock(productId, amount);
        }

        @Override
        public boolean updateProduct(Product product) {
            return delegate.updateProduct(product);
        }

        @Override
        public boolean deleteById(String productId) {
            return delegate.deleteById(productId);
        }

        @Override
        public boolean deductStock(String productId, int qty) {
            return delegate.deductStock(productId, qty);
        }
    }
}

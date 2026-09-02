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
    private final InMemoryStoreService service = new InMemoryStoreService(products, orders, cartRepo);

    StoreServiceTest() {
        products.save(new Product("00001", "Apple", 100, 2.5, "A delicious apple", "Fruit"));
        products.save(new Product("00002", "Banana", 150, 1.5, "A long and yellow banana", "Fruit"));
        products.save(new Product("00003", "Carrot", 200, 0.5, "A orange and crunchy carrot", "Vegetable"));
        products.save(new Product("00004", "Toy Car", 100, 15, "A pastical toy car", "Toy"));
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

    // 测试购买商品数量超过库存
    @Test
    void testPurchaseExceedsStock() {
        int toBuy = products.findById("00004").getStock() + 1;
        ServiceResult<Void> testResult = service.purchase("0120", "00004", toBuy);
        assertEquals(StatusCode.BAD_REQUEST, testResult.getStatus());
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
        DefaultStoreService purchase = new DefaultStoreService(purchaseProducts, throwingOrders);

        ServiceResult<Void> result = purchase.purchase("u", "P", 1);

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(2, purchaseProducts.findById("P").getStock());
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

        DefaultStoreService checkout = new DefaultStoreService(checkoutProducts, failingOrders, checkoutCart);
        ServiceResult<Void> result = checkout.checkout("u");

        assertEquals(StatusCode.CONFLICT, result.getStatus());
        assertEquals(3, checkoutProducts.findById("A").getStock());
        assertEquals(3, checkoutProducts.findById("B").getStock());
        assertTrue(failingOrders.findByUserId("u").isEmpty());
        assertEquals(2, checkoutCart.findByUserId("u").size());
    }

    // 测试补货成功
    @Test
    void testRestockSuccess() {
        int formerNum = products.findById("00001").getStock();
        ServiceResult<Void> testResult = service.restock("admin", "00001", 100);

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(formerNum + 100, products.findById("00001").getStock());
    }

    // 测试补货商品不存在
    @Test
    void testRestockProductNotFound() {
        ServiceResult<Void> testResult = service.restock("admin", "99999999999", 100);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试补货数量为负数
    @Test
    void testRestockNegativeAmount() {
        ServiceResult<Void> testResult = service.restock("admin", "00001", -100);
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
        ServiceResult<Void> testResult = service.deactivateProduct("admin", "00001");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertFalse(products.findById("00001").isActive());
    }

    // 测试下架商品不在列表
    @Test
    void testDeactivatedProductNotListed() {
        service.deactivateProduct("admin", "00001");
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

    // 测试结账库存不足
    @Test
    void testCheckoutInsufficientStock() {
        int stock = products.findById("00001").getStock();
        service.addToCart("0120", "00001", stock + 1);
        ServiceResult<Void> testResult = service.checkout("0120");
        assertEquals(StatusCode.BAD_REQUEST, testResult.getStatus());
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
        service.deactivateProduct("admin", "00001");
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
        FailingCartRepository flakyCart = new FailingCartRepository(true);
        flakyCart.addItem(new CartItem("cart-a", "u", "A", 2, java.time.LocalDateTime.now()));
        DefaultStoreService checkout = new DefaultStoreService(retryProducts, retryOrders, flakyCart);

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
}

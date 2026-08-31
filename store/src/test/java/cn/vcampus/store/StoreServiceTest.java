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
    private final InMemoryStoreService service = new InMemoryStoreService(products, orders);

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
        assertEquals(StatusCode.BAD_REQUEST, service.addProduct("Infinity", Double.POSITIVE_INFINITY, 1, "", "test").getStatus());
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

    private static final class FailingOrderRepository implements OrderRepository {
        private final InMemoryOrderRepository delegate = new InMemoryOrderRepository();
        private int createCount;

        @Override
        public boolean create(Order order) {
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
}

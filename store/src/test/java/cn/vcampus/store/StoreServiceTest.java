package cn.vcampus.store;

import java.util.List;
import cn.vcampus.common.ServiceResult;
import cn.vcampus.common.StatusCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    // 管理员功能测试：
    // 测试库存补充
    @Test
    void testRestockSuccess() {
        int formerNum = products.findById("00001").getStock();
        ServiceResult<Void> testResult = service.restock("admin", "00001", 100);

        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(formerNum + 100, products.findById("00001").getStock());
    }

    // 测试补货目标不存在
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

    // 测试添加新商品
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

    // 测试添加新商品价格为负数
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

    // 测试修改假的商品编号
    @Test
    void testUpdateProductNotFound() {
        ServiceResult<Product> testResult = service.updateProduct("99999999999", "Apple", 3.0, "A delicious apple",
                "Fruit");
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试下架00001
    @Test
    void testDeactivateProduct() {
        ServiceResult<Void> testResult = service.deactivateProduct("admin", "00001");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertFalse(products.findById("00001").isActive());
    }

    // 测试下架00001并且listProducts（）
    @Test
    void testDeactivatedProductNotListed() {
        service.deactivateProduct("admin", "00001");
        ServiceResult<List<Product>> testResult = service.listProducts();
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertFalse(testResult.getData().contains(products.findById("00001")));
    }

    // 购物车功能测试:
    // 测试加入购物车
    @Test
    void testAddToCart() {
        ServiceResult<Void> testResult = service.addToCart("0120", "00001", 1);
        assertEquals(StatusCode.OK, testResult.getStatus());
    }

    // 测试同一个商品加两次
    @Test
    void testAddToCartDuplicateQuantity() {
        service.addToCart("0120", "00001", 1);
        ServiceResult<Void> testResult = service.addToCart("0120", "00001", 1);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(1, cartRepo.findByUserId("0120").size());
        assertEquals(2, cartRepo.findByUserId("0120").get(0).getQuantity());
    }

    // 测试加入假编号
    @Test
    void testAddToCartProductNotFound() {
        ServiceResult<Void> testResult = service.addToCart("0120", "99999999999", 1);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
    }

    // 测试加入后删除
    @Test
    void testRemoveFromCart() {
        service.addToCart("0120", "00001", 1);
        String cartItemId = service.getCart("0120").getData().get(0).getCartItemId();
        ServiceResult<Void> testResult = service.removeFromCart("0120", cartItemId);
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertEquals(0, cartRepo.findByUserId("0120").size());
    }

    // 测试删除别的用户的购物车
    @Test
    void testRemoveFromCartNotOwned() {
        service.addToCart("0121", "00001", 1);
        String cartItemId = service.getCart("0121").getData().get(0).getCartItemId();
        ServiceResult<Void> testResult = service.removeFromCart("0120", cartItemId);
        assertEquals(StatusCode.NOT_FOUND, testResult.getStatus());
        assertEquals(1, cartRepo.findByUserId("0121").size());
    }

    // 测试查询空购物车的用户
    @Test
    void testGetCartEmpty() {
        ServiceResult<List<CartItem>> testResult = service.getCart("0120");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertNotNull(testResult.getData());
        assertTrue(testResult.getData().isEmpty());
    }

    // 测试购买购物车中的所有商品
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

    // 测试购买数量超过了库存
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

    // 测试结账成功后购物车被清空
    @Test
    void testCheckoutClearsCart() {
        service.addToCart("0120", "00001", 2);
        service.addToCart("0120", "00002", 1);
        ServiceResult<Void> testResult = service.checkout("0120");
        assertEquals(StatusCode.OK, testResult.getStatus());
        assertTrue(service.getCart("0120").getData().isEmpty());
    }

}

package cn.vcampus.client.view;

import cn.vcampus.common.ServiceResult;
import cn.vcampus.store.DefaultStoreService;
import cn.vcampus.store.InMemoryCartRepository;
import cn.vcampus.store.InMemoryOrderRepository;
import cn.vcampus.store.InMemoryProductRepository;
import cn.vcampus.store.InMemoryWalletRepository;
import cn.vcampus.store.Product;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 关键词匹配口径一致性：客户端本地过滤与服务端查询必须命中同一批商品。
 *
 * <p>历史缺陷：客户端多匹配了类别与说明，服务端只匹配名称与说明，于是输入商品编号时
 * 「本地筛得到、一触发服务端查询（含 5 秒自动刷新）列表就变空」。本测试用同一批关键词
 * 分别跑两侧过滤并断言结果集合相等，防止口径再次漂移。
 */
class StoreKeywordMatchConsistencyTest {

    private static final String[] PROBES = {
            "apple",        // 名称命中
            "APPLE",        // 大小写不敏感
            "00003",        // 商品编号完整命中
            "0000",         // 商品编号片段命中多条
            "crunchy",      // 仅说明命中：两侧都必须不命中
            "vegetable",    // 仅类别命中：两侧都必须不命中
            "zzz",          // 谁都不命中
    };

    @Test
    void clientAndServerKeywordMatchingAgreeOnEveryProbe() throws Exception {
        DefaultStoreService service = service();
        Method clientMatcher = StorePanel.class.getDeclaredMethod("matchesKeyword", Product.class, String.class);
        clientMatcher.setAccessible(true);

        for (String probe : PROBES) {
            String lowered = probe.trim().toLowerCase();

            ServiceResult<List<Product>> serverResult = service.searchProducts(probe, null, null, null, true);
            List<String> serverIds = ids(serverResult.getData());

            List<String> clientIds = new ArrayList<String>();
            for (Product product : service.listProducts(null, true).getData()) {
                if (((Boolean) clientMatcher.invoke(null, product, lowered)).booleanValue()) {
                    clientIds.add(product.getProductId());
                }
            }

            assertEquals(serverIds, clientIds,
                    "关键词「" + probe + "」在客户端与服务端的命中集合必须一致");
        }
    }

    @Test
    void productIdSearchActuallyFindsTheProduct() throws Exception {
        DefaultStoreService service = service();
        ServiceResult<List<Product>> result = service.searchProducts("00003", null, null, null, false);

        assertEquals(1, result.getData().size());
        assertEquals("00003", result.getData().get(0).getProductId());
    }

    @Test
    void productIdPrefixSearchFindsEverySeededProduct() throws Exception {
        DefaultStoreService service = service();
        ServiceResult<List<Product>> result = service.searchProducts("0000", null, null, null, false);

        assertEquals(4, result.getData().size());
        assertTrue(ids(result.getData()).contains("00001"));
        assertTrue(ids(result.getData()).contains("00004"));
    }

    private static List<String> ids(List<Product> products) {
        List<String> ids = new ArrayList<String>();
        for (Product product : products) {
            ids.add(product.getProductId());
        }
        return ids;
    }

    private static DefaultStoreService service() {
        InMemoryProductRepository products = new InMemoryProductRepository();
        products.save(new Product("00001", "Apple", 100, 2.5d, "A delicious apple", "Fruit"));
        products.save(new Product("00002", "Banana", 150, 1.5d, "A long and yellow banana", "Fruit"));
        products.save(new Product("00003", "Carrot", 200, 0.5d, "A orange and crunchy carrot", "Vegetable"));
        products.save(new Product("00004", "Toy Car", 100, 15.0d, "A pastical toy car", "Toy"));
        return new DefaultStoreService(products, new InMemoryOrderRepository(),
                new InMemoryCartRepository(), new InMemoryWalletRepository());
    }
}

package cn.vcampus.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 内存商品仓储的原子扣库存测试
class InMemoryProductRepositoryTest {
    // 被测仓储实例
    private final InMemoryProductRepository repo = new InMemoryProductRepository();

    // 测试扣库存成功：库存充足时扣减并返回 true
    @Test
    void testDeductStockSuccess() {
        repo.save(new Product("p001", "Apple", 10, 2.5, "A delicious apple", "Fruit"));// 库存 10

        boolean result = repo.deductStock("p001", 3);// 扣 3

        assertTrue(result);
        assertEquals(7, repo.findById("p001").getStock());
    }

    // 测试扣库存守卫：库存不足时返回 false 且库存不变
    @Test
    void testDeductStockGuardRejectsInsufficient() {
        repo.save(new Product("p001", "Apple", 10, 2.5, "A delicious apple", "Fruit"));// 库存 10

        boolean result = repo.deductStock("p001", 20);// 想扣 20，不够

        assertFalse(result);
        assertEquals(10, repo.findById("p001").getStock());// 库存仍为 10
    }

    // 测试扣库存边界：库存恰好等于扣减量时成功（扣成 0）
    @Test
    void testDeductStockExactBoundary() {
        repo.save(new Product("p001", "Apple", 5, 2.5, "A delicious apple", "Fruit"));// 库存 5

        assertTrue(repo.deductStock("p001", 5));// 恰好扣完
        assertEquals(0, repo.findById("p001").getStock());
    }

    // 测试扣库存：商品不存在时返回 false
    @Test
    void testDeductStockNonExistentReturnsFalse() {
        assertFalse(repo.deductStock("ghost", 1));
    }

    // A2：仓储层乐观并发守卫——期望版本与存储版本不符返回 false 且不写；命中则写入并版本 +1
    @Test
    void testUpdateProductVersionGuard() {
        repo.save(new Product("p001", "Apple", 10, 2.5, "d", "Fruit"));// version 0

        // 陈旧版本（1 != 0）→ 拒绝，名称/版本均不变
        assertFalse(repo.updateProduct(new Product("p001", "Apple X", 10, 9.0, "d", "Fruit", true, 1)));
        assertEquals("Apple", repo.findById("p001").getName());
        assertEquals(0, repo.findById("p001").getVersion());

        // 命中版本（0 == 0）→ 写入并把版本号 +1
        assertTrue(repo.updateProduct(new Product("p001", "Apple v2", 10, 3.0, "d", "Fruit", true, 0)));
        assertEquals("Apple v2", repo.findById("p001").getName());
        assertEquals(1, repo.findById("p001").getVersion());
    }
}

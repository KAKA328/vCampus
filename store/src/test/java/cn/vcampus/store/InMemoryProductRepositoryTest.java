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
}

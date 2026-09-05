package cn.vcampus.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Product 实体构造期护栏测试（DSH A3）：长度与价格上界。
// 纯 POJO，无 Swing、无服务依赖；长度上界镜像 DB tblProduct 的 VARCHAR 列宽，
// 故此处硬编码 100/64/255 有意「钉住」列宽契约——若列宽变更，本测试与 Product 常量须同改。
class ProductTest {

    // Java 8 无 String.repeat，手写生成指定长度串
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // name 恰好 100 放行，101 抛 IAE（镜像 DB name VARCHAR(100)）
    @Test
    void testNameLengthBoundary() {
        assertDoesNotThrow(() -> new Product("P1", repeat('a', 100), 1, 1.0, "d", "cat"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", repeat('a', 101), 1, 1.0, "d", "cat"));
    }

    // category 恰好 64 放行，65 抛 IAE（镜像 DB category VARCHAR(64)）
    @Test
    void testCategoryLengthBoundary() {
        assertDoesNotThrow(() -> new Product("P1", "name", 1, 1.0, "d", repeat('c', 64)));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", "name", 1, 1.0, "d", repeat('c', 65)));
    }

    // description 恰好 255 放行，256 抛 IAE；null/空白仍放行（A1 已令说明可选，镜像 DB VARCHAR(255)）
    @Test
    void testDescriptionLengthBoundary() {
        assertDoesNotThrow(() -> new Product("P1", "name", 1, 1.0, repeat('d', 255), "cat"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", "name", 1, 1.0, repeat('d', 256), "cat"));
        assertDoesNotThrow(() -> new Product("P1", "name", 1, 1.0, null, "cat"));
        assertDoesNotThrow(() -> new Product("P1", "name", 1, 1.0, "  ", "cat"));
    }

    // price 恰好 10^9 放行，超过抛 IAE（Product.MAX_PRICE 为 private，故硬编码；+1 远大于该量级
    // ULP，无浮点歧义）
    @Test
    void testPriceUpperBound() {
        assertDoesNotThrow(() -> new Product("P1", "name", 1, 1_000_000_000.0D, "d", "cat"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", "name", 1, 1_000_000_001.0D, "d", "cat"));
    }

    // 回归：必填字段空白、price 非正/非有限、stock 负仍抛 IAE（A3 未放松既有校验）
    @Test
    void testExistingValidationUnchanged() {
        assertThrows(IllegalArgumentException.class, () -> new Product("P1", "  ", 1, 1.0, "d", "cat"));
        assertThrows(IllegalArgumentException.class, () -> new Product("P1", "name", 1, 0.0, "d", "cat"));
        assertThrows(IllegalArgumentException.class,
                () -> new Product("P1", "name", 1, Double.NaN, "d", "cat"));
        assertThrows(IllegalArgumentException.class, () -> new Product("P1", "name", -1, 1.0, "d", "cat"));
    }

    // 合法构造：字段回读一致，description 保留原值（实体不 trim，与 A1 前行为一致）
    @Test
    void testValidConstructionReadsBack() {
        Product p = new Product("P1", "  Green Apple  ", 10, 2.5, "  tasty  ", "Fruit");
        assertEquals("Green Apple", p.getName());// 必填串 trim
        assertEquals("Fruit", p.getCategory());
        assertEquals("  tasty  ", p.getDescription());// 说明不 trim
        assertEquals(10, p.getStock());
        assertEquals(2.5, p.getPrice(), 0.0001);
    }
}

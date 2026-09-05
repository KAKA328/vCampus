package cn.vcampus.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A1 回归：商品「说明」(description) 是可选字段，命令层不得因空/null 说明而拒绝构造——
 * 与服务端 Product 实体（直接赋值不校验）、DB 可空列 VARCHAR(255) 一致；
 * 其余必填字段（name/category/token/productId）与 price/stock 数值边界仍按原契约抛
 * IllegalArgumentException。
 */
class StoreProductCommandTest {

    // ---- A1：说明可空，规范为 trim 后的串 ----

    @Test
    void addCommandAllowsEmptyOrNullDescription() {
        // 空串说明可构造，getDescription 返回空串
        assertEquals("", new StoreProductAddCommand("tk", "苹果", 10.0, 5, "", "水果").getDescription());
        // null 说明同样可构造，规范为空串（避免下游 NPE）
        assertEquals("", new StoreProductAddCommand("tk", "苹果", 10.0, 5, null, "水果").getDescription());
        // 纯空白说明 trim 后为空串
        assertEquals("", new StoreProductAddCommand("tk", "苹果", 10.0, 5, "   ", "水果").getDescription());
        // 有内容时正常 trim 保留
        assertEquals("红富士", new StoreProductAddCommand("tk", "苹果", 10.0, 5, "  红富士  ", "水果").getDescription());
    }

    @Test
    void updateCommandAllowsEmptyOrNullDescription() {
        assertEquals("", new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "", "水果").getDescription());
        assertEquals("", new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, null, "水果").getDescription());
        assertEquals("", new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "  ", "水果").getDescription());
    }

    // ---- 回归：必填字段与数值边界不变 ----

    @Test
    void addCommandStillRejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductAddCommand("", "苹果", 10.0, 5, "d", "水果")); // token 空
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductAddCommand("tk", "", 10.0, 5, "d", "水果")); // name 空
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductAddCommand("tk", "苹果", 10.0, 5, "d", "")); // category 空
    }

    @Test
    void updateCommandStillRejectsBlankRequiredFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductUpdateCommand("tk", "", "苹果", 10.0, "d", "水果")); // productId 空
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductUpdateCommand("tk", "p1", "", 10.0, "d", "水果")); // name 空
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "d", "")); // category 空
    }

    @Test
    void commandsStillRejectInvalidNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductAddCommand("tk", "苹果", -1.0, 5, "d", "水果")); // price < 0
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductAddCommand("tk", "苹果", 10.0, -1, "d", "水果")); // stock < 0
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductUpdateCommand("tk", "p1", "苹果", -1.0, "d", "水果")); // price < 0
    }

    // ---- A2：乐观并发版本快照 ----

    @Test
    void updateCommandCarriesVersionAndRejectsNegative() {
        // 7 参构造携带版本快照，getVersion 原样返回
        assertEquals(7, new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "d", "水果", 7).getVersion());
        // 6 参兼容构造期望版本默认 0
        assertEquals(0, new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "d", "水果").getVersion());
        // 负版本号拒绝
        assertThrows(IllegalArgumentException.class,
                () -> new StoreProductUpdateCommand("tk", "p1", "苹果", 10.0, "d", "水果", -1));
    }
}

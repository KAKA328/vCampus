package cn.vcampus.store;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Money 换算入口单测（DSH P1-2）：元→分一次性 Math.round，是全链路唯一的换算公式。
 * 这些用例锁定公式行为，任何改动舍入方式的做法都会在此暴露，避免订单与流水悄悄对不上账。
 */
class MoneyTest {

    @Test
    void toCentsRoundsYuanToWholeCents() {
        assertEquals(1250L, Money.toCents(12.50d));
        assertEquals(750L, Money.toCents(7.5d));
        assertEquals(0L, Money.toCents(0.0d));
        // 浮点尾巴 12.999 归一到分（1299.9 → 1300），与展示层 StoreRowMapper.toCents 一致
        assertEquals(1300L, Money.toCents(12.999d));
    }

    @Test
    void toCentsScalesQuantityAmountsLikeCheckout() {
        // 单价 × 数量 再换算，与结账实扣、购物车小计同式：2.5 元 × 3 = 7.5 元 = 750 分
        assertEquals(750L, Money.toCents(2.5d * 3));
        // 3.33 元 × 3 = 9.99 元 = 999 分（与购物车小计对账基准一致，验证浮点尾巴不进位到 1000）
        assertEquals(999L, Money.toCents(3.33d * 3));
    }
}

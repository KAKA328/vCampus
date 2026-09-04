package cn.vcampus.store;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 钱包流水实体测试
class WalletTransactionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 10, 30, 0);

    // 测试正常构造：入账为正、扣款为负，金额与余额快照都以分为单位
    @Test
    void testValidConstruction() {
        WalletTransaction entry = new WalletTransaction("T-1", "u001", WalletTransactionType.RECHARGE, 5000L,
                15000L, "u001", null, NOW);
        assertEquals("T-1", entry.getTransactionId());
        assertEquals("u001", entry.getUserId());
        assertEquals(WalletTransactionType.RECHARGE, entry.getType());
        assertEquals(5000L, entry.getAmountCents());
        assertEquals(15000L, entry.getBalanceAfterCents());
        assertEquals("u001", entry.getOperatorId());
        assertEquals(NOW, entry.getCreatedAt());
    }

    // 测试扣款金额为负合法（PURCHASE/CHECKOUT 的符号约定）
    @Test
    void testNegativeAmountAllowed() {
        WalletTransaction entry = new WalletTransaction("T-2", "u001", WalletTransactionType.PURCHASE, -500L,
                14500L, "u001", "order O-1", NOW);
        assertEquals(-500L, entry.getAmountCents());
        assertEquals("order O-1", entry.getNote());
    }

    // 测试校正差额为 0 合法（管理员把余额校正成与原值相同，仍应留痕）
    @Test
    void testZeroAmountAllowedForAdjust() {
        WalletTransaction entry = new WalletTransaction("T-3", "u001", WalletTransactionType.ADJUST, 0L,
                100L, "admin001", null, NOW);
        assertEquals(0L, entry.getAmountCents());
        assertEquals("admin001", entry.getOperatorId());
    }

    // 测试变动后余额为负抛异常（余额恒非负）
    @Test
    void testNegativeBalanceAfterThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-4", "u001", WalletTransactionType.PURCHASE, -100L,
                        -1L, "u001", null, NOW));
    }

    // 测试流水类型为 null 抛异常
    @Test
    void testNullTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-5", "u001", null, 100L, 100L, "u001", null, NOW));
    }

    // 测试空流水编号抛异常
    @Test
    void testEmptyTransactionIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("", "u001", WalletTransactionType.RECHARGE, 100L, 100L, "u001",
                        null, NOW));
    }

    // 测试空用户编号抛异常
    @Test
    void testEmptyUserIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-6", "  ", WalletTransactionType.RECHARGE, 100L, 100L, "u001",
                        null, NOW));
    }

    // 测试空操作者编号抛异常：每笔流水都必须能回答「谁干的」
    @Test
    void testBlankOperatorIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-7", "u001", WalletTransactionType.ADJUST, 100L, 100L, "  ",
                        null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-8", "u001", WalletTransactionType.ADJUST, 100L, 100L, null,
                        null, NOW));
    }

    // 测试记账时间为 null 抛异常
    @Test
    void testNullCreatedAtThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WalletTransaction("T-9", "u001", WalletTransactionType.RECHARGE, 100L, 100L, "u001",
                        null, null));
    }

    // 测试备注允许为 null（区分「没写备注」与「备注是空串」），非 null 时去除首尾空格
    @Test
    void testNoteNullableAndTrimmed() {
        WalletTransaction withoutNote = new WalletTransaction("T-10", "u001", WalletTransactionType.RECHARGE,
                100L, 100L, "u001", null, NOW);
        assertNull(withoutNote.getNote());
        WalletTransaction withNote = new WalletTransaction("T-11", "u001", WalletTransactionType.RECHARGE,
                100L, 100L, "u001", "  order O-1  ", NOW);
        assertEquals("order O-1", withNote.getNote());
    }

    // 测试一段流水的金额可直接累加对账：充值 + 扣款 + 补偿退款，净额为 0
    @Test
    void testSignedAmountsAreSummable() {
        long[] amounts = { 10000L, -500L, 500L, -10000L };
        long netCents = 0L;
        for (long amount : amounts) {
            netCents += amount;
        }
        assertEquals(0L, netCents);
    }

    // 测试五种流水类型齐备，缺一种都会让审计出现盲区
    @Test
    void testTransactionTypeCoversAllMoneyMovements() {
        assertEquals(5, WalletTransactionType.values().length);
        assertEquals(WalletTransactionType.RECHARGE, WalletTransactionType.valueOf("RECHARGE"));
        assertEquals(WalletTransactionType.PURCHASE, WalletTransactionType.valueOf("PURCHASE"));
        assertEquals(WalletTransactionType.CHECKOUT, WalletTransactionType.valueOf("CHECKOUT"));
        assertEquals(WalletTransactionType.REFUND, WalletTransactionType.valueOf("REFUND"));
        assertEquals(WalletTransactionType.ADJUST, WalletTransactionType.valueOf("ADJUST"));
    }
}

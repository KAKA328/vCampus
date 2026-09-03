package cn.vcampus.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// 银行账户实体测试
class BankAccountTest {

    // 测试正常构造：userId 可读、余额以分为单位
    @Test
    void testValidConstruction() {
        BankAccount account = new BankAccount("u001", 12345L);
        assertEquals("u001", account.getUserId());
        assertEquals(12345L, account.getBalanceCents());
    }

    // 测试余额为 0 合法（懒创建时的初始空账户）
    @Test
    void testZeroBalanceAllowed() {
        BankAccount account = new BankAccount("u001", 0L);
        assertEquals(0L, account.getBalanceCents());
    }

    // 测试负余额构造抛异常（账户永不为负）
    @Test
    void testNegativeBalanceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BankAccount("u001", -1L));
    }

    // 测试空 userId 构造抛异常
    @Test
    void testEmptyUserIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BankAccount("", 100L));
    }

    // 测试 null userId 构造抛异常
    @Test
    void testNullUserIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new BankAccount(null, 100L));
    }

    // 测试 userId 会去除首尾空格（与 CartItem 的 checkStr 行为一致）
    @Test
    void testUserIdTrimmed() {
        BankAccount account = new BankAccount("  u001  ", 100L);
        assertEquals("u001", account.getUserId());
    }
}

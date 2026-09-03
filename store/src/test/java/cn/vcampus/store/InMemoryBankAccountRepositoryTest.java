package cn.vcampus.store;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 内存银行账户仓储测试
class InMemoryBankAccountRepositoryTest {
    // 被测仓储实例（JUnit 每个测试方法都会新建测试类实例，互不干扰）
    private final InMemoryBankAccountRepository repo = new InMemoryBankAccountRepository();

    // 测试入账时账户不存在则懒创建（先建 0 余额再累加）
    @Test
    void testCreditCreatesAccountWhenAbsent() {
        assertNull(repo.findByUserId("u001"));// 前置：账户尚不存在

        boolean result = repo.credit("u001", 500L);

        assertTrue(result);
        BankAccount account = repo.findByUserId("u001");
        assertNotNull(account);// 账户已被懒创建
        assertEquals(500L, account.getBalanceCents());
    }

    // 测试多次入账累加
    @Test
    void testCreditAccumulates() {
        repo.credit("u001", 500L);
        repo.credit("u001", 300L);

        assertEquals(800L, repo.findByUserId("u001").getBalanceCents());
    }

    // 测试扣款余额不足时返回 false 且余额不变（守卫）
    @Test
    void testDebitInsufficientReturnsFalseAndUnchanged() {
        repo.credit("u001", 100L);// 余额 100

        boolean result = repo.debit("u001", 200L);// 想扣 200，不够

        assertFalse(result);
        assertEquals(100L, repo.findByUserId("u001").getBalanceCents());// 余额仍为 100
    }

    // 测试扣款余额充足时成功扣减
    @Test
    void testDebitSufficientDeducts() {
        repo.credit("u001", 500L);// 余额 500

        boolean result = repo.debit("u001", 200L);// 扣 200

        assertTrue(result);
        assertEquals(300L, repo.findByUserId("u001").getBalanceCents());
    }

    // 测试扣款账户不存在时返回 false 且不会凭空建账户
    @Test
    void testDebitNonExistentReturnsFalse() {
        assertFalse(repo.debit("ghost", 100L));
        assertNull(repo.findByUserId("ghost"));
    }

    // 测试设置余额为绝对值（而非累加）
    @Test
    void testSetBalanceAbsolute() {
        repo.credit("u001", 500L);// 余额 500

        boolean result = repo.setBalance("u001", 1000L);

        assertTrue(result);
        assertEquals(1000L, repo.findByUserId("u001").getBalanceCents());// 直接变 1000，不是 1500
    }

    // 测试 save 的 upsert：不存在则创建、存在则覆盖
    @Test
    void testSaveUpsert() {
        assertTrue(repo.save(new BankAccount("u001", 200L)));// 创建
        assertEquals(200L, repo.findByUserId("u001").getBalanceCents());

        assertTrue(repo.save(new BankAccount("u001", 800L)));// 覆盖
        assertEquals(800L, repo.findByUserId("u001").getBalanceCents());
    }

    // 测试 save 传入 null 返回 false
    @Test
    void testSaveNullReturnsFalse() {
        assertFalse(repo.save(null));
    }
}

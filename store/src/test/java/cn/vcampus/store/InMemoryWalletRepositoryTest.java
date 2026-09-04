package cn.vcampus.store;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 内存钱包仓储测试：余额与流水在同一把锁内一起改，
// 重点验证「记不记流水、记多少」与「逐笔流水累加恒等于余额」的对账不变量
class InMemoryWalletRepositoryTest {
    // 被测仓储实例（JUnit 每个测试方法都会新建测试类实例，互不干扰）
    private final InMemoryWalletRepository repo = new InMemoryWalletRepository();

    // save 只置余额、不记流水（种子/测试预置）；upsert 覆盖同样不记流水
    @Test
    void testSaveSetsBalanceWithoutLedger() {
        assertTrue(repo.save(new BankAccount("u001", 200L)));// 创建
        assertEquals(200L, repo.findByUserId("u001").getBalanceCents());
        assertTrue(repo.findTransactionsByUserId("u001").isEmpty(), "save 不应产生流水");

        assertTrue(repo.save(new BankAccount("u001", 800L)));// 覆盖
        assertEquals(800L, repo.findByUserId("u001").getBalanceCents());
        assertTrue(repo.findTransactionsByUserId("u001").isEmpty(), "覆盖仍不应产生流水");
    }

    // save 传入 null 返回 false
    @Test
    void testSaveNullReturnsFalse() {
        assertFalse(repo.save(null));
    }

    // credit 账户不存在时懒创建，并记一笔 +cents
    @Test
    void testCreditCreatesAccountAndRecordsPositive() {
        assertNull(repo.findByUserId("u001"));// 前置：账户尚不存在

        WalletMutation mutation = repo.credit("u001", 500L, WalletTransactionType.RECHARGE, "u001", null);

        assertTrue(mutation.isApplied());
        assertEquals(0L, mutation.getBalanceBeforeCents());
        assertEquals(500L, mutation.getBalanceAfterCents());
        assertEquals(500L, repo.findByUserId("u001").getBalanceCents());
        List<WalletTransaction> ledger = repo.findTransactionsByUserId("u001");
        assertEquals(1, ledger.size());
        assertEquals(500L, ledger.get(0).getAmountCents());
        assertEquals(WalletTransactionType.RECHARGE, ledger.get(0).getType());
        assertEquals(500L, ledger.get(0).getBalanceAfterCents());
    }

    // credit 多次累加，逐笔各记一条正数流水
    @Test
    void testCreditAccumulatesAndRecordsEach() {
        repo.credit("u001", 500L, WalletTransactionType.RECHARGE, "u001", null);
        repo.credit("u001", 300L, WalletTransactionType.RECHARGE, "u001", null);

        assertEquals(800L, repo.findByUserId("u001").getBalanceCents());
        assertEquals(2, repo.findTransactionsByUserId("u001").size());
    }

    // debit 余额不足：applied=false，不改余额、不记流水（守卫）
    @Test
    void testDebitInsufficientRejectedWithoutLedger() {
        repo.save(new BankAccount("u001", 100L));// 余额 100

        WalletMutation mutation = repo.debit("u001", 200L, WalletTransactionType.PURCHASE, "u001", "order x");

        assertFalse(mutation.isApplied());
        assertEquals(100L, mutation.getBalanceBeforeCents());
        assertEquals(100L, mutation.getBalanceAfterCents());
        assertEquals(100L, repo.findByUserId("u001").getBalanceCents());// 余额仍为 100
        assertTrue(repo.findTransactionsByUserId("u001").isEmpty(), "被拒绝的扣款不应记流水");
    }

    // debit 账户不存在：applied=false，不凭空建账户、不记流水
    @Test
    void testDebitNonExistentRejected() {
        WalletMutation mutation = repo.debit("ghost", 100L, WalletTransactionType.PURCHASE, "ghost", null);

        assertFalse(mutation.isApplied());
        assertNull(repo.findByUserId("ghost"));
        assertTrue(repo.findTransactionsByUserId("ghost").isEmpty());
    }

    // debit 余额充足：applied=true，扣减并记一笔 -cents
    @Test
    void testDebitSufficientDeductsAndRecordsNegative() {
        repo.save(new BankAccount("u001", 500L));// 余额 500

        WalletMutation mutation = repo.debit("u001", 200L, WalletTransactionType.PURCHASE, "u001", "order x");

        assertTrue(mutation.isApplied());
        assertEquals(500L, mutation.getBalanceBeforeCents());
        assertEquals(300L, mutation.getBalanceAfterCents());
        assertEquals(300L, repo.findByUserId("u001").getBalanceCents());
        List<WalletTransaction> ledger = repo.findTransactionsByUserId("u001");
        assertEquals(1, ledger.size());
        assertEquals(-200L, ledger.get(0).getAmountCents());
        assertEquals(300L, ledger.get(0).getBalanceAfterCents());
        assertEquals("order x", ledger.get(0).getNote());
    }

    // setBalance 账户不存在（视作 0）：记差额 = 新值 - 0，并留下操作者编号
    @Test
    void testSetBalanceFromAbsentRecordsFullDelta() {
        WalletMutation mutation = repo.setBalance("u001", 1000L, WalletTransactionType.ADJUST, "admin", null);

        assertTrue(mutation.isApplied());
        assertEquals(0L, mutation.getBalanceBeforeCents());
        assertEquals(1000L, mutation.getBalanceAfterCents());
        assertEquals(1000L, repo.findByUserId("u001").getBalanceCents());
        WalletTransaction entry = repo.findTransactionsByUserId("u001").get(0);
        assertEquals(1000L, entry.getAmountCents());
        assertEquals("admin", entry.getOperatorId());
    }

    // setBalance 升高：记正差额
    @Test
    void testSetBalanceIncreaseRecordsPositiveDelta() {
        repo.save(new BankAccount("u001", 500L));

        WalletMutation mutation = repo.setBalance("u001", 800L, WalletTransactionType.ADJUST, "admin", null);

        assertEquals(500L, mutation.getBalanceBeforeCents());
        assertEquals(800L, mutation.getBalanceAfterCents());
        assertEquals(300L, repo.findTransactionsByUserId("u001").get(0).getAmountCents());
    }

    // setBalance 降低：记负差额
    @Test
    void testSetBalanceDecreaseRecordsNegativeDelta() {
        repo.save(new BankAccount("u001", 500L));

        WalletMutation mutation = repo.setBalance("u001", 200L, WalletTransactionType.ADJUST, "admin", null);

        assertEquals(500L, mutation.getBalanceBeforeCents());
        assertEquals(200L, mutation.getBalanceAfterCents());
        assertEquals(-300L, repo.findTransactionsByUserId("u001").get(0).getAmountCents());
    }

    // findTransactionsByUserId 按（记账时间、流水编号）升序，且逐笔累加恒等于余额
    @Test
    void testFindTransactionsAscendingAndReconcilable() {
        repo.credit("u001", 100L, WalletTransactionType.RECHARGE, "u001", null);
        repo.debit("u001", 30L, WalletTransactionType.PURCHASE, "u001", "order a");
        repo.credit("u001", 5L, WalletTransactionType.REFUND, "u001", "compensation");

        List<WalletTransaction> ledger = repo.findTransactionsByUserId("u001");
        assertEquals(3, ledger.size());
        // 升序：按记账时间、同一时间按流水编号，验证排序契约（不假定具体某笔落在哪个位置）
        for (int i = 1; i < ledger.size(); i++) {
            WalletTransaction prev = ledger.get(i - 1);
            WalletTransaction curr = ledger.get(i);
            int byTime = prev.getCreatedAt().compareTo(curr.getCreatedAt());
            assertTrue(byTime < 0 || (byTime == 0 && prev.getTransactionId().compareTo(curr.getTransactionId()) <= 0),
                    "流水应按记账时间、再按流水编号升序");
        }
        // 对账不变量：逐笔 amountCents 累加 == 最终余额
        long sum = 0L;
        for (WalletTransaction entry : ledger) {
            sum += entry.getAmountCents();
        }
        assertEquals(repo.findByUserId("u001").getBalanceCents(), sum, "流水累加应恒等于余额");
    }

    // findTransactionsByUserId 返回副本：清空返回列表不影响仓储内部流水
    @Test
    void testFindTransactionsReturnsDefensiveCopy() {
        repo.credit("u001", 100L, WalletTransactionType.RECHARGE, "u001", null);
        repo.debit("u001", 30L, WalletTransactionType.PURCHASE, "u001", "order a");

        List<WalletTransaction> ledger = repo.findTransactionsByUserId("u001");
        ledger.clear();// 改返回的副本
        assertEquals(2, repo.findTransactionsByUserId("u001").size(), "仓储流水不应被外部副本改动");
    }

    // 无流水用户返回空列表而非 null
    @Test
    void testFindTransactionsEmptyForUnknownUser() {
        List<WalletTransaction> ledger = repo.findTransactionsByUserId("ghost");
        assertNotNull(ledger);
        assertTrue(ledger.isEmpty());
    }
}

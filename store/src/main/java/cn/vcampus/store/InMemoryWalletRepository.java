package cn.vcampus.store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的内存钱包仓储，用于本地演示与服务测试。
 * 余额与流水在同一把锁内一起改：debit/credit/setBalance 都是 synchronized，
 * 锁内「改余额 + append 流水」不可分割，因此与 Access 版一样不存在「余额变了、流水缺失」的中间态，
 * 且并发校正被串行化，逐笔流水累加恒等于最终余额。
 */
public final class InMemoryWalletRepository implements WalletRepository {

    private final Map<String, BankAccount> accounts = new ConcurrentHashMap<String, BankAccount>();// 按用户存余额

    private final Map<String, List<WalletTransaction>> ledger = new ConcurrentHashMap<String, List<WalletTransaction>>();// 按用户存流水

    // 记账时间升序、同一时间按流水编号升序，保证返回顺序稳定
    private static final Comparator<WalletTransaction> BY_TIME_THEN_ID = new Comparator<WalletTransaction>() {
        @Override
        public int compare(WalletTransaction left, WalletTransaction right) {
            int byTime = left.getCreatedAt().compareTo(right.getCreatedAt());
            return byTime != 0 ? byTime : left.getTransactionId().compareTo(right.getTransactionId());
        }
    };

    @Override
    public BankAccount findByUserId(String userId) {
        return accounts.get(userId);// 只读，ConcurrentHashMap 自身保证可见性
    }

    @Override
    public synchronized List<WalletTransaction> findTransactionsByUserId(String userId) {
        List<WalletTransaction> stored = ledger.get(userId);
        if (stored == null)
            return new ArrayList<WalletTransaction>();
        List<WalletTransaction> result = new ArrayList<WalletTransaction>(stored);
        Collections.sort(result, BY_TIME_THEN_ID);
        return result;
    }

    @Override
    public synchronized boolean save(BankAccount account) {
        if (account == null)
            return false;
        accounts.put(account.getUserId(), account);// 仅置余额、不记流水（种子/测试预置）
        return true;
    }

    @Override
    public synchronized WalletMutation debit(String userId, long cents, WalletTransactionType type, String operatorId,
            String note) {
        BankAccount account = accounts.get(userId);
        long before = account == null ? 0L : account.getBalanceCents();
        // 守卫：账户不存在或余额不足即拒绝，不改余额、不记流水
        if (account == null || before < cents)
            return WalletMutation.rejected(before);
        long after = before - cents;
        accounts.put(userId, new BankAccount(userId, after));
        append(userId, type, -cents, after, operatorId, note);
        return WalletMutation.applied(before, after);
    }

    @Override
    public synchronized WalletMutation credit(String userId, long cents, WalletTransactionType type, String operatorId,
            String note) {
        BankAccount account = accounts.get(userId);
        long before = account == null ? 0L : account.getBalanceCents();// 懒创建：不存在按 0 起算
        long after = before + cents;
        accounts.put(userId, new BankAccount(userId, after));
        append(userId, type, cents, after, operatorId, note);
        return WalletMutation.applied(before, after);
    }

    @Override
    public synchronized WalletMutation setBalance(String userId, long newBalanceCents, WalletTransactionType type,
            String operatorId, String note) {
        BankAccount account = accounts.get(userId);
        // 锁内读实际旧值，差额 = 新余额 - 实际旧余额，杜绝并发校正算错差额
        long before = account == null ? 0L : account.getBalanceCents();
        long after = newBalanceCents;
        accounts.put(userId, new BankAccount(userId, after));
        append(userId, type, after - before, after, operatorId, note);
        return WalletMutation.applied(before, after);
    }

    // 锁内追加一条流水：流水编号用 UUID，记账时间取当前时刻
    private void append(String userId, WalletTransactionType type, long amountCents, long balanceAfterCents,
            String operatorId, String note) {
        WalletTransaction entry = new WalletTransaction(UUID.randomUUID().toString(), userId, type, amountCents,
                balanceAfterCents, operatorId, note, LocalDateTime.now());
        List<WalletTransaction> userLedger = ledger.get(userId);
        if (userLedger == null) {
            userLedger = new ArrayList<WalletTransaction>();
            ledger.put(userId, userLedger);
        }
        userLedger.add(entry);
    }
}

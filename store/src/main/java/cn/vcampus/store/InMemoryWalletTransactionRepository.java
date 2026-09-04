package cn.vcampus.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 线程安全的内存钱包流水仓库，用于本地演示和服务测试。 */
public final class InMemoryWalletTransactionRepository implements WalletTransactionRepository {
    private final Map<String, WalletTransaction> transactions = new ConcurrentHashMap<String, WalletTransaction>();// 根据流水编号存储流水

    private final Map<String, List<WalletTransaction>> userIdMap = new ConcurrentHashMap<String, List<WalletTransaction>>();// 根据用户编号存储流水

    @Override
    public synchronized final boolean append(WalletTransaction transaction) {
        if (transaction == null)
            return false;
        // 流水编号重复即拒绝，避免同一笔钱被记两次
        if (transactions.containsKey(transaction.getTransactionId()))
            return false;
        String userId = transaction.getUserId();
        transactions.put(transaction.getTransactionId(), transaction);
        List<WalletTransaction> userTransactions = userIdMap.get(userId);
        if (userTransactions == null) {
            userTransactions = new ArrayList<WalletTransaction>();
            userIdMap.put(userId, userTransactions);
        }
        userTransactions.add(transaction);
        return true;
    }

    @Override
    public synchronized final List<WalletTransaction> findByUserId(String userId) {
        List<WalletTransaction> stored = userIdMap.get(userId);
        if (stored == null)
            return new ArrayList<WalletTransaction>();
        List<WalletTransaction> result = new ArrayList<WalletTransaction>(stored);
        // 记账时间升序，同一时间按流水编号升序，保证返回顺序稳定
        Collections.sort(result, new Comparator<WalletTransaction>() {
            @Override
            public int compare(WalletTransaction left, WalletTransaction right) {
                int byTime = left.getCreatedAt().compareTo(right.getCreatedAt());
                return byTime != 0 ? byTime : left.getTransactionId().compareTo(right.getTransactionId());
            }
        });
        return result;
    }
}

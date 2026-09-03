package cn.vcampus.store;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 线程安全的内存银行账户仓库，用于本地演示和服务测试 */
public final class InMemoryBankAccountRepository implements BankAccountRepository {
    
    private final Map<String, BankAccount> accounts = new ConcurrentHashMap<String, BankAccount>();

    @Override
    public BankAccount findByUserId(String userId) {
        return accounts.get(userId);// 读方法，不需要添加synchronized
    }

    @Override
    public synchronized boolean save(BankAccount account) {
        if (account == null) return false;
        accounts.put(account.getUserId(), account);
        return true;
    }

    @Override
    public synchronized boolean credit(String userId, long cents) {
        BankAccount account = accounts.computeIfAbsent(userId, id -> new BankAccount(id, 0));
        accounts.put(userId, new BankAccount(userId, account.getBalanceCents() + cents));
        return true;
    }

    @Override
    public synchronized boolean debit(String userId, long cents) {
        BankAccount account = accounts.get(userId);
        if (account == null || account.getBalanceCents() < cents) return false;{
            accounts.put(userId, new BankAccount(userId, account.getBalanceCents() - cents));
        }
        return true;
    }

    @Override
    public synchronized boolean setBalance(String userId, long cents) {
        accounts.put(userId, new BankAccount(userId, cents));
        return true;
    }
}

package cn.vcampus.store;

/**
 * 银行账户仓库契约，全部金额以「分」为单位的 long 传递。
 * 实现须保证 credit/debit/setBalance 的「检查+写入」原子完成（内存版 synchronized，
 * Access 版用 WHERE balance_cents>=? 守卫），以在单 JVM 下达成补偿一致性，而非数据库事务。
 */
public interface BankAccountRepository {

    /** 按用户ID查询账户，账户不存在返回 null。 */
    BankAccount findByUserId(String userId);

    /** 保存账户（upsert）：存在则覆盖，不存在则创建。 */
    boolean save(BankAccount account);

    /** 入账（懒创建）：存在则累加，不存在则先建 0 余额账户再累加。 */
    boolean credit(String userId, long cents);

    /** 扣款（守卫）：balance_cents>=cents 才扣，余额不足或账户不存在返回 false 且不改余额。 */
    boolean debit(String userId, long cents);

    /** 绝对设置余额为 cents（用于管理员校正）。 */
    boolean setBalance(String userId, long cents);
}
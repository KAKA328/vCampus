package cn.vcampus.store;

import java.util.List;

/**
 * 校园钱包仓储契约：把「余额」与「流水」当作同一个一致性单元，全部金额以「分」为单位的 long 传递。
 *
 * <p>
 * debit/credit/setBalance 三个写原语都在**同一事务/锁**内完成「改余额 + 追加流水」：
 * Access 实现用单连接 {@code setAutoCommit(false)} 事务（与 AccessLibraryRepository 同款写法，
 * UCanAccess 4.0.4 支持单连接事务），内存实现用单一 {@code synchronized} 锁。
 * 因此不存在「余额已变、流水却缺失」的中间态；存储层故障（流水表缺失/只读/写异常）由实现回滚后
 * 抛 {@link IllegalStateException}，调用方据此补偿并返回错误，绝不静默丢账。
 *
 * <p>
 * setBalance 在同一事务/锁内读取**实际旧余额**并据此计算差额记流水，故并发校正被串行化，
 * 逐笔流水金额累加恒等于最终余额，可直接对账。
 */
public interface WalletRepository {

    /** 按用户ID查询账户余额，账户不存在返回 null。 */
    BankAccount findByUserId(String userId);

    /** 按用户查询流水，按记账时间升序返回；无流水返回空列表而非 null。 */
    List<WalletTransaction> findTransactionsByUserId(String userId);

    /** 直接置余额（upsert），**不记流水**，仅供种子数据与测试预置初始余额使用。 */
    boolean save(BankAccount account);

    /**
     * 原子扣款 + 记流水：{@code balance_cents >= cents} 才扣，成功返回 applied=true（记
     * {@code -cents}），
     * 余额不足或账户不存在返回 applied=false 且不改余额、不记流水；存储故障抛 IllegalStateException。
     * 契约：cents 必须为正，违反抛 IllegalArgumentException（Access/内存两实现行为一致）。
     */
    WalletMutation debit(String userId, long cents, WalletTransactionType type, String operatorId, String note);

    /**
     * 原子入账（懒创建）+ 记流水：账户不存在先建 0 余额再累加，成功返回 applied=true（记 {@code +cents}）；
     * 存储故障抛 IllegalStateException。
     * 契约：cents 必须为正，违反抛 IllegalArgumentException（Access/内存两实现行为一致）。
     */
    WalletMutation credit(String userId, long cents, WalletTransactionType type, String operatorId, String note);

    /**
     * 原子绝对设置余额 + 记流水：在同一事务/锁内读取实际旧值，流水金额记「新余额 - 实际旧余额」的真实差额，
     * 成功返回 applied=true；存储故障抛 IllegalStateException。
     * 契约：newBalanceCents 必须非负，违反抛 IllegalArgumentException（Access/内存两实现行为一致）。
     */
    WalletMutation setBalance(String userId, long newBalanceCents, WalletTransactionType type, String operatorId,
            String note);
}

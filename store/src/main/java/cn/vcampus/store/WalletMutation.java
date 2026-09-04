package cn.vcampus.store;

/**
 * 钱包原子变动结果，承载「余额变动 + 流水记账」在同一事务/锁内完成后的实际数值。
 * applied=false 只表示业务拒绝（当前仅扣款余额不足），此时余额不变、不记流水；
 * 存储层故障（如流水表缺失/只读）由仓储回滚事务后抛 IllegalStateException，不用本对象表达。
 * balanceBeforeCents 是变动前实际余额（校正时为事务内读到的真实旧值），
 * balanceAfterCents 是变动后实际余额，二者之差恒等于本笔流水的带符号金额，可直接对账。
 */
public final class WalletMutation {
    private final boolean applied;// 是否真正落地：false 表示被守卫拒绝（余额不足）
    private final long balanceBeforeCents;// 变动前实际余额，单位分
    private final long balanceAfterCents;// 变动后实际余额，单位分

    private WalletMutation(boolean applied, long balanceBeforeCents, long balanceAfterCents) {
        this.applied = applied;
        this.balanceBeforeCents = balanceBeforeCents;
        this.balanceAfterCents = balanceAfterCents;
    }

    /** 变动已落地：余额由 before 变为 after，并已记一条金额 = after-before 的流水。 */
    public static WalletMutation applied(long balanceBeforeCents, long balanceAfterCents) {
        return new WalletMutation(true, balanceBeforeCents, balanceAfterCents);
    }

    /** 变动被拒绝：余额保持 before 不变，不记流水。 */
    public static WalletMutation rejected(long balanceBeforeCents) {
        return new WalletMutation(false, balanceBeforeCents, balanceBeforeCents);
    }

    public boolean isApplied() {
        return applied;
    }

    public long getBalanceBeforeCents() {
        return balanceBeforeCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }
}

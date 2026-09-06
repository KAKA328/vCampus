package cn.vcampus.store;

/**
 * 校园钱包流水类型，决定 amountCents 的符号含义。
 * RECHARGE / REFUND 为入账（amountCents 为正），PURCHASE / CHECKOUT 为扣款（amountCents
 * 为负），
 * ADJUST 为管理员校正，amountCents 记录「校正后 - 校正前」的差额，可正可负可为零。
 */
public enum WalletTransactionType {
    RECHARGE, // 本人充值
    PURCHASE, // 直接购买扣款
    CHECKOUT, // 购物车结账扣款
    REFUND, // 补偿退款入账
    ADJUST// 管理员校正差额
}

package cn.vcampus.library;

/** A loss bill remains payable until its settlement has committed. */
public enum CompensationStatus {
    /** 赔偿账单待支付。 */
    PENDING,
    /** 赔偿账单已结清。 */
    PAID
}

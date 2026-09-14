package cn.vcampus.library;

/** Lifecycle state of a borrowing record. */
public enum BorrowStatus {
    /** 仍在借阅，含已逾期但未归还的记录。 */
    BORROWED,
    /** 已正常归还。 */
    RETURNED,
    /** 已登记遗失，等待本人赔偿。 */
    LOST,
    /** 遗失赔偿已结清。 */
    COMPENSATED
}

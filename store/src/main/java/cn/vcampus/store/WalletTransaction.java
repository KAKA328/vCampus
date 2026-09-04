package cn.vcampus.store;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 校园钱包流水不可变值对象，全部金额以「分」为单位的 long 传递。
 * amountCents 带符号：入账为正、扣款为负、校正为差额，因此一段流水的金额可直接累加对账。
 * balanceAfterCents 是写入余额后回读到的实际余额，并发场景下可能不等于「本次变动前 + 本次金额」，
 * 仅作展示参考，不作对账依据。
 * 流水只追加、不修改、不删除，用于回答「这笔钱是谁、什么时候、因为什么变动的」。
 */
public final class WalletTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String transactionId;// 流水编号
    private final String userId;// 账户所属用户编号
    private final WalletTransactionType type;// 流水类型
    private final long amountCents;// 变动金额，单位分，带符号
    private final long balanceAfterCents;// 变动后余额，单位分，恒非负
    private final String operatorId;// 操作者编号：本人操作为 userId，管理员校正为管理员编号
    private final String note;// 备注，可空
    private final LocalDateTime createdAt;// 记账时间

    public WalletTransaction(String transactionId, String userId, WalletTransactionType type, long amountCents,
            long balanceAfterCents, String operatorId, String note, LocalDateTime createdAt) {
        this.transactionId = checkStr(transactionId, "transactionId");
        this.userId = checkStr(userId, "userId");
        if (type == null)
            throw new IllegalArgumentException("type cannot be null");
        this.type = type;
        this.amountCents = amountCents;
        if (balanceAfterCents < 0)
            throw new IllegalArgumentException("balanceAfterCents cannot be negative");
        this.balanceAfterCents = balanceAfterCents;
        this.operatorId = checkStr(operatorId, "operatorId");
        this.note = note == null ? null : note.trim();
        if (createdAt == null)
            throw new IllegalArgumentException("createdAt cannot be null");
        this.createdAt = createdAt;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public long getBalanceAfterCents() {
        return balanceAfterCents;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // 检查字符串合法性
    private static String checkStr(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }
}

package cn.vcampus.store;

import java.io.Serializable;

/**
 * 银行账户不可变值对象。
 * 余额以「分」为单位的 long 存储（balanceCents），避免 double 元的浮点误差累积漂移；
 * 对象只读，改值需新建对象覆盖；balanceCents 恒非负。
 */
public final class BankAccount implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String userId;// 用户ID
    private final long balanceCents;// 余额，单位分

    public BankAccount(String userId, long balanceCents) {
        this.userId = checkStr(userId, "userId");
        if (balanceCents < 0)
            throw new IllegalArgumentException("Balance cannot be negative");
        this.balanceCents = balanceCents;
    }

    public String getUserId() {
        return userId;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    private static String checkStr(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value.trim();
    }
}

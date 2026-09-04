-- 校园钱包流水：只追加、不修改、不删除。
-- amount_cents 带符号（入账为正、扣款为负、校正为差额），因此一段流水可直接累加对账；
-- balance_after_cents 是余额写入后回读的实际余额，并发下仅供展示，不作对账依据；
-- operator_id 记录操作者：本人操作为账户本人，管理员校正为管理员编号。
CREATE TABLE tblWalletTransaction (
    transaction_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    amount_cents BIGINT NOT NULL,
    balance_after_cents BIGINT NOT NULL,
    operator_id VARCHAR(32) NOT NULL,
    note VARCHAR(200),
    created_at DATETIME NOT NULL,
    PRIMARY KEY (transaction_id)
);

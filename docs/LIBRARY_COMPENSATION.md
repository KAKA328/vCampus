# 图书遗失原价赔偿与校园钱包对接

## 规则与使用流程

1. 图书管理员或系统管理员在借阅记录中选择仍在借阅的记录，确认遗失。普通学生、教师不能自行修改遗失状态。
2. 服务器读取该图书当前录入价格，按人民币两位小数四舍五入生成不可变金额快照，作为赔偿原价；不额外收取罚金。客户端不提交金额或扣款用户编号。
3. 确认遗失立即把借阅状态改为 `LOST`（遗失待赔偿），馆藏总册数减少 1，可借册数不增加。每条借阅记录最多生成一张赔偿单，重复确认返回同一张单。
4. 借阅人登录自己的账户，在“遗失赔偿”中核对书名、金额后确认支付；余额来自商店使用的同一校园钱包。余额不足时先在商店钱包充值，再返回刷新并支付。
5. 支付成功后，赔偿单为 `PAID`，借阅记录为 `COMPENSATED`（已赔偿），钱包增加一条“图书赔偿”扣款流水。重复付款请求返回已支付账单，不再次扣款。
6. 遗失或已赔偿记录不再产生普通到期归还提醒，不能通过“归还”恢复库存。零元图书允许确认结清，不扣余额、不制造零元钱包流水。

价格在管理员确认遗失时锁定，而非付款时重新计算。修改图书信息不改变既有账单。暂不包含赔偿撤销、找回图书、赔款退款、逾期罚金、候补预约或欠款限制借阅等规则；如需这些流程，应另行定义权限和状态转换，不能直接修改钱包余额模拟处理。

## 账户、权限与金额边界

- 账户键是登录会话中的 `User.userId`，不是学号，也不是独立商店账号。
- 服务器启动时由 `LibraryWalletRuntime` 创建共享的图书仓库和钱包实例；商店及赔偿组件复用同一个钱包。内存模式不能分别创建两份钱包。
- 管理员只能确认遗失及查看全量赔偿，不能凭管理权限替其他用户扣款。支付时服务器再次核对账单归属。
- 余额和赔偿金额使用 `long` 分；图书的现有 `double price` 仅在生成账单时经 `BigDecimal.valueOf` 转换并 HALF_UP 舍入。超出可表示范围或非法价格拒绝生成，不改变任何库存和记录。
- 钱包流水类型为 `LIBRARY_LOSS`，仍适配 `transaction_type VARCHAR(16)`。赔偿单号与其扣款流水相互对应。

## 原子性与并发范围

Access 模式确认遗失使用一个 JDBC 事务写入赔偿单、借阅状态与馆藏总册数；付款使用同一个连接事务完成条件扣余额、追加钱包流水、结清赔偿单和更新借阅状态。任一步失败均回滚，而不是先独立扣钱再补写图书记录。

在当前单服务器部署中，锁顺序固定为共享图书仓库锁、共享钱包锁。内存模式使用相同共享锁边界，并在扣款前预先验证、构造所有后续状态；Access 另有条件更新和赔偿单借阅记录唯一约束。余额不足不结清，重试同一已支付账单不重复扣款。

原有借阅使用共享仓库同步锁、条件扣库存和批量事务。因此最后一本书被成功借出后，其他竞争请求失败属于正常库存不足；并发保护不保证每人都借到，也不承诺按点击顺序公平排队。新增并发测试覆盖最后一本、多副本、同用户重复借阅、重复归还，以及批量争抢失败不产生部分借阅。

当前保证范围是单服务器共享实例。不据此宣称多服务器、跨进程分布式锁或已完成生产级压力测试。

## 新协议与兼容方式

| 消息 | 请求类 | 成功响应 | 权限 |
|---|---|---|---|
| `LIBRARY_HISTORY_V3` | `LibraryHistoryV3Command(token, targetUserId?, allUsers)` | `List<BorrowRecord>` | 本人 LIBRARY_READ；他人/全部 LIBRARY_MANAGE |
| `LIBRARY_LOSS_DECLARE_V3` | `LibraryLossDeclareV3Command(token, recordId)` | `LibraryCompensation` | LIBRARY_MANAGE |
| `LIBRARY_COMPENSATION_LIST_V3` | `LibraryCompensationListV3Command(token, allUsers)` | `List<LibraryCompensation>` | 本人 LIBRARY_READ；全部 LIBRARY_MANAGE |
| `LIBRARY_COMPENSATION_PAY_V3` | `LibraryCompensationPayV3Command(token, compensationId)` | `LibraryCompensation` | LIBRARY_BORROW，且账单必须属于本人 |
| `LIBRARY_WALLET_QUERY_V3` | `LibraryWalletQueryV3Command(token)` | `Long`，单位分 | LIBRARY_READ，仅本人 |
| `STORE_ACCOUNT_LEDGER_V2` | `StoreAccountLedgerV2Command(token)` | `List<WalletTransaction>` | STORE_READ，仅本人 |

赔偿 DTO 含赔偿单号、借阅记录号、用户编号、书号、书名快照、原价分金额、PENDING/PAID 状态、确认管理员、创建时间及支付时间。支付和确认遗失命令均不接受自报金额。

旧 `LIBRARY_HISTORY_V2` 在结果包含 LOST/COMPENSATED 时返回 CONFLICT 和升级提示，不向旧客户端序列化未知枚举；无新状态的历史仍按旧协议返回。旧 `STORE_ACCOUNT_LEDGER` 在包含 LIBRARY_LOSS 时同样返回升级提示。新版客户端使用新消息；客户端与服务端应配套升级，新客户端不向旧服务端自动回退执行付款。

## 数据库升级与验收

- 新建库使用完整 `database/schema.sql` 和 `database/seed.sql`，仍保留 50 种馆藏及原有演示借阅。
- 已有图书价格、钱包及流水表的旧库，停止服务器并备份后，执行一次 `database/migrations/015_library_compensation.up.sql`。不要用重建种子覆盖已有借阅、余额或流水。
- 上线前依次验证：管理员确认遗失；学生/教师本人支付；余额不足；相同账单重复请求；他人及管理员代付被拒；支付后商店余额和流水可见；重启后状态持久化；事务故障不留下半笔账。
- 自动测试使用临时数据库及测试账户，不向实际使用中的钱包扣款。实际执行结果以本轮测试报告为准。
- 本地演示库升级、备份位置、人工操作步骤及 795 项回归报告说明见 [赔偿与并发验收记录](../test-data/LIBRARY_COMPENSATION_ACCEPTANCE.md)。

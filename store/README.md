# 商店模块（store）

校园商店提供商品浏览、直接购买、购物车结算、订单查询、管理员商品/库存维护、**校园钱包**（假银行账户）与**钱包流水审计**能力。模块只描述业务，不依赖 Swing、Socket 或 Access；服务器端通过 `StoreMessageHandler` 接入，客户端通过 `RemoteStoreService` + `StorePanel` 接入。

## 1. 领域组成

| 类型 | 主要类 | 说明 |
|---|---|---|
| 业务接口 | `StoreService` | 商品/订单/购物车/钱包的统一入口，全部返回 `ServiceResult<T>`（`getBalance` 例外，直接返回 `long` 分） |
| 默认实现 | `DefaultStoreService` | 注入 5 个仓储，落地购买/结账的补偿顺序、并发保护与流水记账 |
| 转发实现 | `InMemoryStoreService` | 原样转发给 `delegate`，自带内存仓储，用于演示与测试 |
| 实体 | `Product`、`Order`、`CartItem`、`BankAccount`、`WalletTransaction` | 值对象，`final` + `Serializable` |
| 读模型 | `CartLine` | 购物车明细，读取时与商品联表得到，**不落库** |
| 仓储接口 | `ProductRepository`、`OrderRepository`、`CartRepository`、`BankAccountRepository`、`WalletTransactionRepository` | 数据访问契约 |
| 内存仓储 | `InMemory*Repository` | `ConcurrentHashMap` + 写方法 `synchronized` |
| 命令对象 | `Store*Command`、`Cart*Command`、`StoreAccount*Command` | token-only 或带业务参数，`final` + `Serializable` + `checkStr` 校验 |

Access 持久化实现位于 `server` 模块：`AccessProductRepository`、`AccessOrderRepository`、`AccessCartRepository`、`AccessBankAccountRepository`、`AccessWalletTransactionRepository`。

## 2. 校园钱包（假银行账户）

### 2.1 余额以「分」为单位存 long

钱包余额一律用**「分」为单位的 `long`**：实体字段 `BankAccount.balanceCents`、数据库列 `tblBankAccount.balance_cents BIGINT NOT NULL`、服务/命令/仓储接口全部传 `long` 分。**禁止 `double` 余额**。

原因：`double` 是 IEEE-754 浮点数，`0.1 + 0.2 != 0.3`，反复加减会把舍入误差累积进账本，导致对不上账。用整数「分」做加减法是精确的，展示时再 `/100` 转元。

既有 `Product.price`、`Order.totalPrice`/`unitPrice` 仍是 `double` 保持不动，只在**支付边界**换算一次：

```java
long totalCents = Math.round(order.getTotalPrice() * 100);
```

换算只发生一次，误差不进余额账本。

### 2.2 四个钱包操作

| 操作 | 服务方法 | 消息类型 | 语义 |
|---|---|---|---|
| 查余额 | `long getBalance(userId)` | `STORE_ACCOUNT_QUERY` | 无账户返回 0 |
| 充值 | `recharge(userId, cents)` | `STORE_ACCOUNT_RECHARGE` | 仅本人、仅增加、`cents > 0`，账户不存在时**懒创建** |
| 校正 | `adjustBalance(adminId, userId, newBalanceCents)` | `STORE_ACCOUNT_ADJUST` | 管理员把目标余额设为绝对值，`newBalanceCents >= 0` |
| 查流水 | `listTransactions(userId)` | `STORE_ACCOUNT_LEDGER` | 仅本人，按记账时间升序，无流水返回空列表 |

`BankAccountRepository` 的写原语：`credit`（累加，懒创建）、`debit`（守卫 `balance_cents >= cents`，不足返回 `false` 且不改余额）、`setBalance`（绝对值）、`save`（upsert）。

### 2.3 钱包流水（审计）

每一次资金变动都追加一条 `WalletTransaction`，**只追加、不修改、不删除**，回答「这笔钱是谁、什么时候、因为什么变动的」。

| 字段 | 说明 |
|---|---|
| `type` | `RECHARGE` 充值、`PURCHASE` 直接购买、`CHECKOUT` 购物车结账、`REFUND` 补偿退款、`ADJUST` 管理员校正 |
| `amountCents` | **带符号**：入账为正、扣款为负、校正为「校正后 - 校正前」的差额，因此一段流水可直接累加对账 |
| `balanceAfterCents` | 余额写入后**回读**的实际余额；并发下可能不等于「变动前 + 本次金额」，**仅作展示，不作对账依据** |
| `operatorId` | 本人操作为账户本人，管理员校正为**管理员编号**，校正不再丢失「谁改的」 |
| `note` | 备注，可空；购买/结账写入 `order <订单编号>`，实现「这笔扣款对应哪张订单」的双向追溯 |

记账是**尽力而为**的（`DefaultStoreService.recordLedger`）：

- 在钱**已实际扣走/已实际入账之后**才记，后续建单失败会再记一笔 `REFUND`，账面上正负相抵；
- `append` 返回 `false` 或抛 `RuntimeException` 都**只记日志**，绝不因审计写不进去而回滚一笔已成功的资金变动；
- 流水与余额写入**不在同一事务内**（Access 每仓储独立 JDBC 连接、无跨表事务）。

## 3. 购买/结账的补偿一致性（非数据库事务）

> ⚠️ 口径统一：这是**单 JVM 下的补偿一致性**，**不是数据库事务**，不涉及 ACID。文档、JavaDoc、PR 一律不使用「原子事务」「ACID」等措辞。

Access 每个仓储是**独立的 JDBC 连接**，没有跨表事务能力。因此 `purchase`/`checkout` 用**应用层补偿**保证一致：按固定顺序执行，任一步失败就**按序回滚此前已扣的项**。

补偿顺序：

```text
预检(仅提示) → 原子 deductStock → 原子 debit → 建单(UUID) → 清空购物车
```

- **预检只作提示**：库存/余额预检只用来改善提示信息（`NOT_FOUND`/`CONFLICT`/`PAYMENT_REQUIRED`），真正成功由原子 `deductStock`（`WHERE stock >= qty`）和原子 `debit`（`WHERE balance_cents >= ?`）决定。并发下预检结果会过期，不能作为成功依据。
- **每个补偿都检查返回值**：退款 `credit`、回补库存 `addStock`、撤单 `deleteById` 都要判断是否成功；**任一补偿失败仍返回 `CONFLICT`** 并记日志，**绝不返回成功**。
- **`purchase`/`checkout` 共用一把 `synchronized` 锁**：在单 JVM 内串行化复合操作，避免「查余额→算→写」之间的竞态。跨进程仍依赖数据库层的守卫式 UPDATE。
- **订单唯一业务编号 = 现有 UUID `orderId`**：`tblOrder` 以 `order_id` 为主键，重试是全新 UUID，不会与回滚残留的订单撞号。

## 4. 购物车：改数量与明细联表

### 4.1 修改数量

`updateCartQuantity(userId, cartItemId, newQuantity)` 对应 `STORE_CART_UPDATE`。`CartRepository.updateQuantity` 在内存版与 Access 版都已存在，本次只是把上层链路（服务方法 + 消息类型 + 命令对象 + Handler case + 路由白名单）接通。

**安全要点**：必须校验条目**归属本人**。实现上沿用 `removeFromCart` 的遍历匹配模式——先 `cart.findByUserId(userId)` 找到属于本人的条目，命中才调 `updateQuantity`；条目不属于本人一律返回 `NOT_FOUND`，**不区分「不存在」与「不是你的」**，避免通过探测响应码枚举他人购物车条目。

### 4.2 明细读模型 `CartLine`

`getCartDetails(userId)` 对应 `STORE_CART_DETAIL`，在**读取时**把购物车条目与商品实时联表，返回带商品名、单价、小计的 `CartLine`。

为何**不给 `CartItem` 加快照字段**：

1. **破坏面过大**：全仓 22 处 `new CartItem(...)` 调用点（含 19 处测试）全要改；
2. **要改表**：`tblCartItem` 得加列（又一条迁移），而快照一旦落库就会与商品表不一致，还得额外写同步逻辑；
3. **读模型更诚实**：购物车是**待下单**状态，商品改名或调价后应该立刻显示新值；而 `Order` 是**已成交凭证**，必须冻结当时快照——两者语义本来就不同。

代价：商品被物理删除后该行会消失（`getCartDetails` 直接 `continue` 跳过）。当前下架走 `deactivateProduct`（只置 `active=false`）而非删除，因此实际不会丢行；`CartLine.active` 仍会把下架状态透给前端，便于结账前提示。

### 4.3 金额只能累加 `subtotalCents`

`subtotalCents` 与 `checkout` 实扣金额**同式计算**（`Math.round(单价元 × 数量 × 100)`），是唯一可对账的金额。`unitPriceCents` 仅供展示，`unitPriceCents × quantity` 可能因四舍五入与 `subtotalCents` 相差一两分，**前端合计必须累加 `subtotalCents`**。

## 5. 权限矩阵（服务端强校验）

权限在**服务端** `StoreMessageHandler` 强制校验，客户端隐藏按钮只是 UX。

| 操作 | 权限 | 额外角色门槛 | 身份来源 |
|---|---|---|---|
| 查商品/订单/购物车/购物车明细/热销/余额/流水 | `STORE_READ` | — | `userId` 取自 token |
| 购买/加购/移除/改数量/结账/充值 | `STORE_PURCHASE` | — | `userId` 取自 token（仅本人） |
| 商品/库存维护、全量订单 | `STORE_MANAGE` | — | `userId` 取自 token |
| 校正余额 `STORE_ACCOUNT_ADJUST` | `STORE_MANAGE` | 角色 ∈ {`ADMIN`, `STORE_MANAGER`} | `targetUserId` 取自 payload，仅管理员可指定他人 |

`adjustBalance` 的**双重门槛**（权限 + 显式角色）是纵深防御：即使将来 `STORE_MANAGE` 被误授给别的角色，显式角色校验仍能拦住。普通用户身份一律取自 token，不能通过传 `targetUserId` 改别人的钱。

**本次无需新增 `Permission` 枚举值**：`ADMIN` 是 `EnumSet.allOf`，`STORE_MANAGER` 已有 `STORE_MANAGE`；改数量用 `STORE_PURCHASE`、查明细/查流水用 `STORE_READ` 即可。

## 6. 数据库

- 建表见 [`../database/schema.sql`](../database/schema.sql)：`tblBankAccount(user_id VARCHAR(32) PRIMARY KEY, balance_cents BIGINT NOT NULL)`、`tblWalletTransaction(transaction_id VARCHAR(36) PRIMARY KEY, ..., amount_cents BIGINT, balance_after_cents BIGINT, ...)`。
- 迁移见 [`009_store_bank_account`](../database/migrations/009_store_bank_account.up.sql) 与 [`011_store_wallet_transaction`](../database/migrations/011_store_wallet_transaction.up.sql)（010 已被学籍 `010_student_academic` 占用，不碰）。
- 主键即唯一性约束：**UCanAccess 4.0.4 不支持 `CREATE UNIQUE INDEX`**（抛 `FeatureNotSupportedException`），唯一性只能靠主键、建表内联 `CONSTRAINT ... UNIQUE`，或非唯一的 `CREATE INDEX`（`idx_tblWalletTransaction_user` 仅加速按用户查流水，不承担唯一性）。
- `CartItem` **未加列**：购物车明细走读取时联表（见 4.2），`tblCartItem` 结构不变，旧库无需迁移即可用。
- 数据库必须按最新 `schema.sql` 重建，不兼容旧 `.accdb`，详见 [`../database/README.md`](../database/README.md)。

## 7. 测试

| 测试类 | 覆盖 |
|---|---|
| `BankAccountTest` | 负余额/空 `userId` 构造抛 `IllegalArgumentException` |
| `WalletTransactionTest` | 流水构造校验、`amountCents` 带符号、`balanceAfterCents` 非负、空备注 |
| `InMemoryBankAccountRepositoryTest` | 懒创建、累加、守卫拒绝、绝对值校正 |
| `InMemoryProductRepositoryTest` | `deductStock` 守卫拒绝/成功 |
| `StoreServiceTest` | 余额、五类补偿失败、唯一业务编号、购物车改数量与归属校验、明细联表与小计、流水记账、防御性拷贝 |
| `StoreConcurrencyTest` | `CountDownLatch` 三场景并发不超卖、余额与成功订单一致 |

Access 持久化与消息路由测试见 `server` 模块的 `AccessStoreRepositoryTest`（含钱包流水临时库实测）与 `StoreMessageHandlerTest`（含三个新消息类型的授权/未授权用例）。运行：

```powershell
mvn clean test
```

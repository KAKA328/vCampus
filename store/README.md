# 商店模块（store）

校园商店提供商品浏览、直接购买、购物车结算、订单查询、管理员商品/库存维护，以及本次新增的**校园钱包**（假银行账户）能力。模块只描述业务，不依赖 Swing、Socket 或 Access；服务器端通过 `StoreMessageHandler` 接入，客户端通过 `RemoteStoreService` + `StorePanel` 接入。

## 1. 领域组成

| 类型 | 主要类 | 说明 |
|---|---|---|
| 业务接口 | `StoreService` | 商品/订单/购物车/钱包的统一入口，全部返回 `ServiceResult<T>`（`getBalance` 例外，直接返回 `long` 分） |
| 默认实现 | `DefaultStoreService` | 注入 4 个仓储，落地购买/结账的补偿顺序与并发保护 |
| 转发实现 | `InMemoryStoreService` | 原样转发给 `delegate`，自带内存仓储，用于演示与测试 |
| 实体 | `Product`、`Order`、`CartItem`、`BankAccount` | 值对象，`final` + `Serializable` |
| 仓储接口 | `ProductRepository`、`OrderRepository`、`CartRepository`、`BankAccountRepository` | 数据访问契约 |
| 内存仓储 | `InMemory*Repository` | `ConcurrentHashMap` + 写方法 `synchronized` |
| 命令对象 | `Store*Command`、`Cart*Command`、`StoreAccount*Command` | token-only 或带业务参数，`final` + `Serializable` + `checkStr` 校验 |

Access 持久化实现位于 `server` 模块：`AccessProductRepository`、`AccessOrderRepository`、`AccessCartRepository`、`AccessBankAccountRepository`。

## 2. 校园钱包（假银行账户）

### 2.1 余额以「分」为单位存 long

钱包余额一律用**「分」为单位的 `long`**：实体字段 `BankAccount.balanceCents`、数据库列 `tblBankAccount.balance_cents BIGINT NOT NULL`、服务/命令/仓储接口全部传 `long` 分。**禁止 `double` 余额**。

原因：`double` 是 IEEE-754 浮点数，`0.1 + 0.2 != 0.3`，反复加减会把舍入误差累积进账本，导致对不上账。用整数「分」做加减法是精确的，展示时再 `/100` 转元。

既有 `Product.price`、`Order.totalPrice`/`unitPrice` 仍是 `double` 保持不动，只在**支付边界**换算一次：

```java
long totalCents = Math.round(order.getTotalPrice() * 100);
```

换算只发生一次，误差不进余额账本。

### 2.2 三个钱包操作

| 操作 | 服务方法 | 消息类型 | 语义 |
|---|---|---|---|
| 查余额 | `long getBalance(userId)` | `STORE_ACCOUNT_QUERY` | 无账户返回 0 |
| 充值 | `recharge(userId, cents)` | `STORE_ACCOUNT_RECHARGE` | 仅本人、仅增加、`cents > 0`，账户不存在时**懒创建** |
| 校正 | `adjustBalance(adminId, userId, newBalanceCents)` | `STORE_ACCOUNT_ADJUST` | 管理员把目标余额设为绝对值，`newBalanceCents >= 0` |

`BankAccountRepository` 的写原语：`credit`（累加，懒创建）、`debit`（守卫 `balance_cents >= cents`，不足返回 `false` 且不改余额）、`setBalance`（绝对值）、`save`（upsert）。

### 2.3 暂无账户流水/审计

当前钱包**只有余额**，没有充值/消费/校正的流水明细表，也没有审计日志。校正余额是直接覆盖绝对值。若后续需要对账，应另建流水表，不在本次范围。

## 3. 购买/结账的补偿一致性（非数据库事务）

> ⚠️ 口径统一：这是**单 JVM 下的补偿一致性**，**不是数据库事务**，不涉及 ACID。文档、JavaDoc、PR 一律不使用「原子事务」「ACID」等措辞。

Access 每个仓储是**独立的 JDBC 连接**，没有跨表事务能力。因此 `purchase`/`checkout` 用**应用层补偿**保证一致：按固定顺序执行，任一步失败就**按序回滚此前已扣的项**。

补偿顺序：

```text
预检(仅提示) → 原子 deductStock → 原子 debit → 建单(UUID) → 清空购物车
```

- **预检只作提示**：库存/余额预检只用来改善提示信息（`NOT_FOUND`/`BAD_REQUEST`/`PAYMENT_REQUIRED`），真正成功由原子 `deductStock`（`WHERE stock >= qty`）和原子 `debit`（`WHERE balance_cents >= ?`）决定。并发下预检结果会过期，不能作为成功依据。
- **每个补偿都检查返回值**：退款 `credit`、回补库存 `addStock`、撤单 `deleteById` 都要判断是否成功；**任一补偿失败仍返回 `CONFLICT`** 并记日志，**绝不返回成功**。
- **`purchase`/`checkout` 共用一把 `synchronized` 锁**：在单 JVM 内串行化复合操作，避免「查余额→算→写」之间的竞态。跨进程仍依赖数据库层的守卫式 UPDATE。
- **订单唯一业务编号 = 现有 UUID `orderId`**：`tblOrder` 以 `order_id` 为主键，重试是全新 UUID，不会与回滚残留的订单撞号。

## 4. 权限矩阵（服务端强校验）

权限在**服务端** `StoreMessageHandler` 强制校验，客户端隐藏按钮只是 UX。

| 操作 | 权限 | 额外角色门槛 | 身份来源 |
|---|---|---|---|
| 查商品/订单/购物车/热销/余额 | `STORE_READ` | — | `userId` 取自 token |
| 购买/加购/移除/结账/充值 | `STORE_PURCHASE` | — | `userId` 取自 token（仅本人） |
| 商品/库存维护、全量订单 | `STORE_MANAGE` | — | `userId` 取自 token |
| 校正余额 `STORE_ACCOUNT_ADJUST` | `STORE_MANAGE` | 角色 ∈ {`ADMIN`, `STORE_MANAGER`} | `targetUserId` 取自 payload，仅管理员可指定他人 |

`adjustBalance` 的**双重门槛**（权限 + 显式角色）是纵深防御：即使将来 `STORE_MANAGE` 被误授给别的角色，显式角色校验仍能拦住。普通用户身份一律取自 token，不能通过传 `targetUserId` 改别人的钱。

## 5. 数据库

- 建表见 [`../database/schema.sql`](../database/schema.sql)：`tblBankAccount(user_id VARCHAR(32) PRIMARY KEY, balance_cents BIGINT NOT NULL)`。
- 迁移见 [`../database/migrations/009_store_bank_account.up.sql`](../database/migrations/009_store_bank_account.up.sql)。
- `user_id` 是主键：懒创建 upsert 依赖主键去重。**UCanAccess 4.0.4 不支持 `CREATE UNIQUE INDEX`**（抛 `FeatureNotSupportedException`），唯一性只能靠主键或建表内联 `CONSTRAINT ... UNIQUE`。
- 数据库必须按最新 `schema.sql` 重建，不兼容旧 `.accdb`，详见 [`../database/README.md`](../database/README.md)。

## 6. 测试

| 测试类 | 覆盖 |
|---|---|
| `BankAccountTest` | 负余额/空 `userId` 构造抛 `IllegalArgumentException` |
| `InMemoryBankAccountRepositoryTest` | 懒创建、累加、守卫拒绝、绝对值校正 |
| `InMemoryProductRepositoryTest` | `deductStock` 守卫拒绝/成功 |
| `StoreServiceTest` | 余额、五类补偿失败、唯一业务编号 |
| `StoreConcurrencyTest` | `CountDownLatch` 三场景并发不超卖、余额与成功订单一致 |

Access 持久化测试见 `server` 模块的 `AccessStoreRepositoryTest`。运行：

```powershell
mvn clean test
```

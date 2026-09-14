# 商店模块测试数据说明

面向组长与验收同学。本文说明商店模块**已有哪些测试数据**、本次**新增了哪些并发/竞态专用数据**、以及**怎么用它们跑出一组可判定的竞态场景**。

配套脚本：[`database/store-test-data.sql`](../database/store-test-data.sql)

---

## 1. 结论速览

1. 商店**已有长期演示数据**，不需要重复建设：`seed.sql` 预置 105 种商品（P001..P105，8 个类别）、4 个钱包账户、演示订单与购物车。
2. 但已有商品**库存都在 60~300 之间**，没有任何低库存商品，**无法用于并发/竞态验证**。本次补齐这一块。
3. 本次新增 **10 件限量商品（P901..P910）+ 4 个并发买家钱包**，放在**专项脚本**里，一键重建出独立测试库 `database/store-test.accdb`，**不覆盖主演示库、不改动任何生产代码**。
4. 已实跑验证：重建后商品总数 **115**（105 + 10）、钱包账户 **8**（4 + 4）、演示订单 3，与设计一致。
5. 第 6 节给出 **14 组场景 + 预期状态码**，包含「库存只剩 1 件、一人买 2 件、一人买 1 件同时提交」这类典型竞态。
6. 第 7 节的 **`AccessStoreConcurrencyTest`（9 个用例）已在真实 Access 库上把这些场景中可自动判定的部分全部跑绿**，含故障注入确定触发的跨资源回滚；手工场景表用于演示与端到端复核。
7. 已核对：`seed.sql` 的 105 种商品库存最小 60、最大 300，**无一个个位数库存**，这正是必须新增限量商品的原因。
8. 专项脚本**只能对全新库执行**，复位只能靠重建；第 5.1 节写明失败中断后的清理方式，以及「绝不能碰 `database/vCampus.accdb`」这条硬边界。

---

## 2. 现有测试数据资产盘点

| 资产 | 位置 | 内容 | 用途 |
| --- | --- | --- | --- |
| 商品演示数据 | `database/seed.sql` 商店段 | P001..P105 共 105 种在售商品，8 个类别（文具/零食饮料/日用品/数码配件/体育用品/个人护理/宿舍生活/文创纪念品），库存最小 60、最大 300（无个位数库存） | 列表、分页、搜索、热销 Top10、类别与价格区间筛选 |
| 钱包演示数据 | `database/seed.sql` | `demo_student`、`demo_teacher` 各 10000 元；`demo_admin` 200 元；`demo_store_manager` 500 元（单位：分） | 购买、结算、流水、余额校正 |
| 演示订单/购物车 | `database/seed.sql` | 引用 P001..P003，共 3 张订单 | 「我的订单」「全部订单」展示 |
| 演示账号 | 仓库根 `README.md`、`database/README.md` | `demo_student`、`demo_teacher`、`demo_admin`、`demo_store_manager`、`demo_student_new`、`demo_student_retake`、`demo_student_elective`、`demo_student_cross`、`demo_teacher_002/003` 等，初始密码统一 `Demo123` | 多角色、多买家登录 |
| 商品批量导入模板 | `test-data/商品批量导入示例.csv` | 列：名称,价格,库存,类别,说明；6 件演示商品（库存 40~300） | 「商店管理 → 批量导入」。注意该文件不是 UTF-8，PowerShell 下用 `-Encoding Default` 读取才正常；其类别用词（生活）与 seed 的 8 类（日用品）不完全一致，导入后属新类别，不影响功能 |
| 运行库 | `database/vCampus.accdb` | 已入库的运行数据库 | 服务器 `--db` 连接；UI 写入落此文件，重启不丢 |
| 全新库自检 | `server/src/test/java/.../AccessDatabaseSchemaTest.java` | 临时目录跑完整 `schema.sql` + `seed.sql`，**断言商品数为 105** | 保证重建结果确定 |
| 已有自动化竞态覆盖（内存层） | `store/src/test/java/.../StoreServiceTest.java` → `checkoutItemsSerializesConcurrentQuantityUpdate`、`StoreConcurrencyTest.java` | 用 `CountDownLatch` 卡住子集结算的批量删除，验证并发改数量被同一把锁串行化 | 单元层竞态回归 |
| **新增：Access 真实库并发验收** | `server/src/test/java/.../AccessStoreConcurrencyTest.java` | 9 个用例，每个用例建一次性临时库（`schema.sql` + `seed.sql` + `store-test-data.sql`），断言**落盘**的库存/订单/余额/流水/购物车 | 本次交付的自动化验收，详见第 7 节 |
| 竞态测试范式参考 | `server/src/test/java/.../StudentGraduationConcurrencyTest.java`、`AccessLibraryBorrowConcurrencyTest.java` | 学籍、图书馆模块的并发竞态测试 | 上述新测试即照此范式扩展 |

---

## 3. 为什么限量商品放专项脚本，而不是直接改 `seed.sql`

| 考量 | 说明 |
| --- | --- |
| 不牵动主线测试 | `AccessDatabaseSchemaTest` 硬断言 seed 后商品数为 **105**。改 `seed.sql` 就必须同步改该断言，属于「动其他模块的测试」，本次按要求不做 |
| 竞态数据会被消耗 | `stock=1` 的商品被抢完就没了，**必须能一键复位**。独立 `store-test.accdb` 重跑一次 rebuild 即回到初始库存，主演示库完全不受影响 |
| 与既有做法一致 | 学籍模块的专项数据就是 `database/student-test-data.sql` + `rebuild.ps1 -AdditionalScript`，本脚本沿用同一套机制 |
| 不污染演示库 | 主演示库里出现一堆 `stock=0` 的「限量盲盒」会影响正常演示观感 |

**如果组长希望限量商品长期出现在主演示库**：把 `store-test-data.sql` 里的商品 `INSERT` 追加到 `seed.sql` 商店段末尾，并把 `AccessDatabaseSchemaTest` 第 61 行的 `105` 改成 `115` 即可（钱包那 4 条同理追加，账户数断言若有也需同步）。本次未做，避免牵动其他模块。

---

## 4. 新增数据清单

### 4.1 限量商品（P9xx 段，避开 P001..P105）

| 编号 | 名称 | 库存 | 单价（元） | 类别 | 上架 | 设计意图 |
| --- | --- | --- | --- | --- | --- | --- |
| P901 | 限量盲盒 仅剩一件 | **1** | 9.90 | 文创纪念品 | 是 | 最小竞态单元：两人抢一件；也用于「买 2 件 vs 买 1 件」的顺序对照 |
| P902 | 秒杀保温杯 仅剩两件 | **2** | 39.00 | 日用品 | 是 | 需求总量（2+1=3）超库存，验证**不拆分成交** |
| P903 | 限量球鞋 五双 | **5** | 99.00 | 体育用品 | 是 | 三人各买两双（需求 6 > 5），验证**部分成功**且剩余量精确 |
| P904 | 天价手办 预检拒绝专用 | 3 | **9999.00** | 文创纪念品 | 是 | 任何演示账号都买不起：**只**用于验证 `PAYMENT_REQUIRED` 在预检阶段即拦下（不扣库存、不建单、不记流水） |
| P905 | 已售罄限量徽章 | **0** | 5.00 | 文创纪念品 | 是 | 售罄边界：可加入购物车，但直购/结算必须被拦 |
| P906 | 已下架限量海报 | 10 | 15.00 | 文创纪念品 | **否** | 下架边界：默认列表不可见，直购/结算按商品不存在处理 |
| P907 | 竞态结算矿泉水 三瓶 | **3** | 2.00 | 零食饮料 | 是 | 购物车**子集批量结算**、重复条目去重、结算时库存被抢走的回滚 |
| P908 | 压测小饼干 二十包 | **20** | 0.50 | 零食饮料 | 是 | 多人高频齐射，验证「成功订单数 = 库存扣减量」的累计一致性 |
| P909 | 半分糖果 金额换算边界 | 2 | **0.004** | 零食饮料 | 是 | 单价低于半分：买 1 件换算为 0 分应被拒，买 2 件换算为 1 分可成交 |
| P910 | **一元面包 余额边界专用** | 5 | **1.00** | 零食饮料 | 是 | 单价适中：让「余额刚好买得起一次 → 第二次 `PAYMENT_REQUIRED`」可**确定复现**，也是余额与流水逐笔对账的最小单元 |

> P909 在界面上可能显示为 `¥0.00`，这是**预期现象**（金额换算统一走 `Money.toCents(yuan) = Math.round(yuan * 100)`），它专门用于覆盖「换算后非正的退化商品必须拒绝」这条代码路径。

### 4.2 并发买家钱包

`seed.sql` 只给 4 个账号预置了钱包，凑不出「三四个学生同时抢」的局面，因此补 4 个：

| 账号 | 角色 | 余额 | 定位 |
| --- | --- | --- | --- |
| `demo_student` | 学生 | 10000 元 | 买家 1（seed 已有） |
| `demo_teacher` | 教师 | 10000 元 | 买家 2（seed 已有） |
| `demo_student_new` | 学生 | **500 元** | 买家 3（本次新增） |
| `demo_student_retake` | 学生 | **500 元** | 买家 4（本次新增） |
| `demo_student_elective` | 学生 | **1.50 元** | 余额边界买家：买 1 件 P910 后剩 0.50 元，第二次即 `PAYMENT_REQUIRED`；也买得起 3 包 P908（本次新增） |
| `demo_student_cross` | 学生 | **1.00 元** | 余额边界买家：与 P910 单价**恰好相等**，首次 `OK` 后余额归零，第二次必被拒（本次新增） |

初始密码统一 `Demo123`。这 4 个账号在 `seed.sql` 中已存在，本脚本只补 `tblBankAccount` 记录，不新建登录账号。两位低余额买家（1.50 元 / 1.00 元）都买不起 P904（9999 元），所以 **P904 恒定停在预检阶段**，不会进入「扣款失败 → 回滚」路径；那条路径改由第 7 节的故障注入用例确定触发。

---

## 5. 一键构建与运行

```powershell
# ① 构建独立测试库（按序执行 schema.sql → seed.sql → store-test-data.sql）
.\database\rebuild.ps1 -DatabasePath database\store-test.accdb -AdditionalScript database\store-test-data.sql

# ② 启动服务器连测试库（必须带 --db；不带则走内存演示数据，进程退出即丢，竞态测试无从谈起）
java -jar server/target/vCampusServer.jar --db database/store-test.accdb --port 19090

# ③ 启动 2~4 个客户端，分别登录不同演示账号（初始密码 Demo123）
java -jar client/target/vCampusClient.jar --host 127.0.0.1 --port 19090
```

- **库存/余额复位**：重跑 ①（`rebuild.ps1` 会先把旧库备份成 `.bak` 再重建）。
- **只想复位余额**：用 `demo_store_manager` 登录「商店管理 → 商品维护」的余额校正，无需重建整库。
- `store-test.accdb` 是生成物，**不入库**（与 `student-test.accdb` 一致，各自本地重建）。

### 5.1 可重复性、复位与安全边界（验收必读）

| 事项 | 规定 |
| --- | --- |
| **必须使用全新测试库** | 专项脚本是纯 `INSERT`，对已建好的库重跑必然主键冲突（`P901`、`user_id` 重复）而失败。这是**有意设计**：宁可失败，也不让同一个库里出现两份互相矛盾的限量数据。`AccessStoreConcurrencyTest.specializedScriptBuildsRepeatableFreshDatabaseAndRejectsSecondRun` 已把这条约束固化成断言 |
| **复位只能靠重建** | 限量库存一旦被抢完，没有任何「补库存」的测试入口；唯一复位方式是重跑 ①（`rebuild.ps1` 会先把旧库改名成 `.bak` 再重建），或删掉 `store-test.accdb` 后重建。重建后每轮演示都得到**同一初始状态** |
| **失败中断后如何清理** | `rebuild.ps1` 中途失败时，`database\store-test.accdb` 可能停在半建状态（表已建、数据不全），**必须删除后重来**。自动化测试用的是 JUnit `@TempDir` 一次性目录，无论成功失败都由 JUnit 自动回收，不留残余文件 |
| **绝不能碰 `database/vCampus.accdb`** | 所有命令都必须显式写 `-DatabasePath database\store-test.accdb` 与 `--db database/store-test.accdb`。`vCampus.accdb` 是**已入库**的主演示库，被竞态数据污染后无法复位、且改动会进版本库。`.gitignore` 对 `database/*.accdb` 开了例外（`!database/*.accdb`），所以 `store-test.accdb` 生成后**必须确保不被提交**（提交时不要用 `git add .`，或先把该文件删除） |
| **重复执行的判定口径** | 想复现某轮竞态结果就重建一次再跑一次；不要拿「上一轮剩下的库」继续测——剩余库存与余额已不是脚本预置值，第 6 节的预期状态码也就不再适用 |

### 结果在哪里看

| 观察点 | 入口 |
| --- | --- |
| 剩余库存 | 商店页商品列表/详情的库存列；管理端「商品维护」 |
| 成功订单数 | 各买家「我的订单」；管理端「全部订单」 |
| 余额与流水 | 各买家「钱包」页（每笔扣款/退款都有流水，可与订单双向对账） |
| 数据库层核对 | 直接查 `store-test.accdb` 的 `tblProduct.stock`、`tblOrder`、`tblWalletTransaction` |

---

## 6. 并发/竞态/边界场景表（14 组）

状态码语义（`cn.vcampus.common.StatusCode`）：`OK` 成功；`CONFLICT` 请求合法但资源状态冲突（库存被抢、余额被花掉、清理购物车失败等，**可重试**）；`PAYMENT_REQUIRED` 余额不足；`NOT_FOUND` 商品不存在或已下架、购物车条目不属于本人；`BAD_REQUEST` 入参非法；`SERVER_ERROR` 补偿不完整，需人工对账。

| # | 场景 | 数据 | 操作 | 预期结果 | 验证的不变量 |
| --- | --- | --- | --- | --- | --- |
| A1 | 单件抢购 | P901（库存 1） | 买家 1、买家 2 各买 1 件，尽量同时提交 | 恰好一人 `OK`，另一人 `CONFLICT` | 库存最终 = 0，订单 = 1 笔，**库存不为负** |
| A2 | **买 2 件 vs 买 1 件**（组长关心的典型例子） | P901（库存 1） | 买家 1 买 **2** 件、买家 2 买 **1** 件，同时提交 | 买 2 件者**恒** `CONFLICT`（预检 1 < 2）；买 1 件者视先后可能 `OK`。无论谁先到，都不会出现「两人都成功」或「库存 -1」 | 需求量超过库存的请求**永远不会部分成交**；库存 ≥ 0 |
| B | 需求总量超库存 | P902（库存 2） | 买家 1 买 2 件、买家 2 买 1 件，同时提交 | 至多一人 `OK`。若买 1 件者先成功 → 库存剩 1，买 2 件者 `CONFLICT`；若买 2 件者先成功 → 库存 0，买 1 件者 `CONFLICT` | 订单数 ≤ 1，库存 ∈ {0, 1}，**绝不出现 2 笔订单** |
| C | 多人齐射部分成功 | P903（库存 5） | 3 位买家各买 2 双，同时提交 | 最多 2 人 `OK`、1 人 `CONFLICT` | 订单 = 2 笔，库存最终 = **1**，扣款笔数 = 订单数 |
| D | 购物车子集批量结算 | P907（库存 3）+ P908（库存 20） | 同一买家加购 P907 ×3 与 P908 ×2（两条**不同商品**的条目），只勾选 P907 结算；再把请求里的 `cartItemId` 重复传两次 | 勾选条目结算 `OK`，未勾选的 P908 **留在购物车**；重复 `cartItemId` 只结算一次 | 库存扣减量 = 实际结算数量；**重复条目不会重复扣款/扣库存** |
| D2 | 结算时库存被抢走 | P907（库存 3） | 买家 1、买家 2 各自加购 3 瓶，然后同时结算 | 一人 `OK`（库存 0），另一人 `CONFLICT`（预检库存不足，或原子扣减被抢先后**整单回滚**） | 回滚后：无订单、余额未变（或有等额退款流水）、库存 = 0、购物车条目仍在可重试 |
| E1 | 售罄商品 | P905（库存 0） | 直购任意数量；或加购后结算 | 直购 `CONFLICT`（库存不足）；**加购仍可成功**（加购不校验库存），结算被预检拦下 `CONFLICT` | 库存恒为 0，无订单、无扣款 |
| E2 | 已下架商品 | P906（`active=0`） | 默认列表查找；再在「视图与筛选… → 显示已下架」下查看；尝试直购/结算 | 默认列表**不可见**；勾选显示已下架后可见；直购与结算均 `NOT_FOUND` | 下架商品不可成交，但对管理员可见可维护 |
| F1 | 余额不足（预检即拦） | P904（9999 元）+ `demo_student_elective`（1.50 元） | 直购 1 件 | `PAYMENT_REQUIRED`（"Insufficient balance"） | 库存未扣（仍为 3）、无订单、无流水、余额未变 |
| F2 | **余额刚好买得起一次** | P910（1.00 元）+ `demo_student_cross`（1.00 元） | 直购 1 件；成功后再直购 1 件 | 首次 `OK`（余额归 0）；第二次 `PAYMENT_REQUIRED` | 首次后库存 5→4、订单 1 笔、流水 1 笔 `-100` 且 `balance_after_cents=0`；第二次库存/订单/流水**一律不变** |
| F3 | **预检通过但扣款失败 → 跨资源回滚** | P908（0.50 元）+ P907（2.00 元） | **无法用手工点击复现**（原因见下表后说明），由第 7 节故障注入用例确定触发 | 第 1 条目已扣库存并建单、第 2 条目 `debit` 返回 `applied=false` → 整单 `CONFLICT`（"Insufficient balance; checkout rolled back"） | **已扣库存全部回补**、已建订单被撤销、退款与扣款流水**等额相抵**（净额 0）、购物车条目保留可重试 |
| G | 高频齐射累计一致性 | P908（库存 20） | 4 位买家轮流/同时各买 1 包，重复多轮 | 每轮成功数 = 当轮可用库存；库存耗尽后全部 `CONFLICT` | 成功订单总数 = 20 − 剩余库存 = 扣款流水笔数，**无一超卖** |
| H | 金额换算边界 | P909（单价 0.004 元） | 买 1 件；再买 2 件 | 买 1 件 `BAD_REQUEST`（换算 0 分，"computed total must be positive"）；买 2 件 `OK`（换算 1 分） | 不会把 `debit(0)` 送进钱包触发契约异常 |
| I | 越权与入参 | 任意 P9xx | 用买家 A 的会话传买家 B 的 `cartItemId` 结算；数量填 0 / 负数 / 超过 100000 | 他人条目 `NOT_FOUND`；数量非法 `BAD_REQUEST`（上限 `MAX_QUANTITY = 100000`） | 归属校验按「不存在」处理，不泄露他人条目；非法入参在扣库存前就被拦下 |

> 场景 A2/B/C 的操作要点：**两个客户端窗口并排摆放，数到三同时点「购买」**。若观察不到交错也没关系——见第 9 节的判定口径。
>
> 场景 D 的要点：`tblCartItem` 上有 `CONSTRAINT uk_tblCartItem_user_product UNIQUE (user_id, product_id)`，**同一买家对同一商品重复加购会累加成一行的数量**，购物车里不可能出现两条同商品记录。所以「只结算一部分」必须用**不同商品**的条目来演示；「重复结算同一条目」则靠在请求里把同一个 `cartItemId` 传两次（服务端用 `Set` 去重）。
>
> 场景 F3 为什么手工点不出来：`checkoutInternal` 全程运行在 `DefaultStoreService` 的同一把 `synchronized` 锁内，预检估算金额与逐项扣款金额用的是同一个算式，单进程下并发请求**无法在预检与扣款之间插入**一次余额变化。这条回滚路径是面向「多进程共用同一库」或「有人直接改库」的防御性代码，因此改用**故障注入**（让钱包第 2 次 `debit` 返回 `applied=false`）在自动化测试里确定触发，而不是让验收同学去点一个点不出来的场景。

---

## 7. 自动化验收测试（Access 真实库）

`server/src/test/java/cn/vcampus/server/AccessStoreConcurrencyTest.java` —— **9 个用例，全部跑在真实 Access 库上**，数据就是本专项脚本：每个用例先用 `schema.sql` → `seed.sql` → `store-test-data.sql` 建一次性临时库（与 `rebuild.ps1` 完全同序），跑完由 JUnit `@TempDir` 自动回收。

所有断言都通过**重新打开的仓储**读取数据库文件，即断言**落盘结果**而非进程内缓存；每个场景都覆盖库存、订单、钱包余额、钱包流水、购物车五个最终状态。

运行：

```powershell
mvn -o -pl server -am "-Dtest=AccessStoreConcurrencyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

| 用例 | 数据 | 验证内容 | 对应场景 |
| --- | --- | --- | --- |
| `simultaneousBuyersRacingForLastUnitProduceExactlyOneOrder` | P901（库存 1） | 6 个买家栅栏式齐射：**恰好 1 单成交**；赢家扣款 1 笔 `PURCHASE` 且 `balance_after_cents` 精确，输家余额/流水/订单一概不动；库存归 0 不为负 | A1 |
| `quantityTwoNeverBeatsQuantityOneForTheLastUnit` | P901（库存 1） | 「买 2 件」与「买 1 件」同时提交：买 2 件者**恒** `CONFLICT`、买 1 件者**恒** `OK`，失败方分文未动 | **A2** |
| `simultaneousBuyersCannotTakeMoreThanAvailableStock` | P903（库存 5） | 6 人各买 2 双：**恰好 2 人成功**、库存剩 1；核心不变量「初始库存 − 剩余库存 = 成功订单购买量之和」 | C |
| `highFrequencySalvoKeepsOrdersEqualToStockDeductions` | P908（库存 20） | 8 人齐射全部成功：**成功订单数 = 库存扣减量 = 扣款流水笔数** | G |
| `concurrentCartCheckoutsCompetingForLastStockLeaveLoserCartRetryable` | P907（库存 3） | 两人都加购 3 瓶后同时整单结算：一人 `OK`（购物车清空、1 笔 `CHECKOUT`），另一人 `CONFLICT` 且**购物车条目原样保留**；随后**失败重试**仍被拒且不留任何副作用 | D2 |
| `subsetCheckoutWithDuplicateCartItemIdSettlesEachItemOnce` | P907 + P908 | 子集结算时把同一 `cartItemId` 传两次：库存各只扣一次、**流水恰好 2 笔**、金额 700 分（多扣会立刻暴露）、购物车清空 | D |
| `walletDebitRejectionAfterStockDeductionRollsBackStockOrderAndLedger` | P908 + P907 | **故障注入**：钱包第 2 次 `debit` 返回 `applied=false` → `CONFLICT` + 库存全回补 + 订单全撤销 + 一扣一退**净额为 0** + 购物车保留；恢复后**重试成功**，余额与流水重新对齐 | **F3** |
| `balanceExactlyAffordsOneUnitThenRejectsSecondPurchase` | P910 + P905/P906 | `demo_student_cross`（1.00 元）首次 `OK` 余额归 0、第二次 `PAYMENT_REQUIRED`；`demo_student_elective`（1.50 元）买一次剩 0.50 元后同样被拒；售罄 P905 → `CONFLICT`、下架 P906 → `NOT_FOUND`，均无副作用 | F1/F2/E1/E2 |
| `specializedScriptBuildsRepeatableFreshDatabaseAndRejectsSecondRun` | 专项脚本本身 | 全新库每次得到**同一初始状态**（商品 115、钱包 8、P901 库存 1、P910 库存 5、P905 库存 0、`demo_student_cross` 余额 100 分）；库存被消耗后**只有重建能复位**；对已建库重跑脚本**必然主键冲突失败** | 第 5.1 节 |

实测：`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`（约 65 秒，大部分是建库耗时）。

**手工场景表（第 6 节）与自动化用例的关系**：A1/A2/C/D/D2/E1/E2/F1/F2/F3/G 已由上表在真实 Access 库上确定覆盖；B 与 A2 是同一不变量的另一种数量组合；H（单价换算边界）与 I（越权与入参）属服务层入参/换算校验，`StoreServiceTest`（`MAX_QUANTITY + 1` → `BAD_REQUEST`、余额不足 → `PAYMENT_REQUIRED`、`testCheckoutItemsNotOwnedOrMissing` → `NOT_FOUND`、`testCheckoutItemsDeduplicatesRepeatedIds`）与 `MoneyTest` 已有确定性单测，本次不重复建设。

---

## 8. 预期结果的机制依据（为什么可以断言「绝不超卖」）

竞态测试的目的不是「把系统搞崩」，而是**验证防护确实生效**。商店有三道防线：

1. **服务层串行化**：`DefaultStoreService` 的 `purchase` / `addToCart` / `checkout` / `checkoutItems` / `recharge` 全部是 `synchronized`，同一服务实例内所有商店写入共用一把锁，天然串行。
2. **数据层原子守卫**：`AccessProductRepository.deductStock` 用单条条件更新
   `UPDATE tblProduct SET stock=stock-? WHERE product_id=? AND stock>=?`
   —— 「检查 + 扣减」在一条 SQL 内原子完成，影响 0 行即判定被并发抢先，服务层归为 `CONFLICT`，**永远不会写出负库存**。商品信息修改另有 `WHERE version=?` 的字段级乐观并发。
3. **钱包事务 + 跨资源补偿**：余额与流水由 `AccessWalletRepository` 在同一 JDBC 事务内提交（流水写失败即回滚余额）；`checkoutInternal` 按「扣库存 → 扣款 → 建单 → 清理购物车」逐项推进，任一步失败即 `rollbackCheckoutResult` 撤销已建订单、回补库存、退款；补偿本身不完整时升级为 `SERVER_ERROR` 并明确要求人工对账，**不会把永久不一致伪装成普通冲突**。

由此得到本组数据的**核心不变量**（任何并发组合下都必须成立）：

- `库存 ≥ 0`，且 `初始库存 − 剩余库存 = 成功订单的购买量之和`
- `成功订单数 = 扣款流水笔数`，余额变动与流水逐笔可对账
- 失败请求**不留下任何副作用**（无订单、无扣款、库存不变），或副作用被完整回滚

---

## 9. 已知限制（判定口径）

- **竞态窗口很窄**：Access 是单文件数据库，UCanAccess 写入本身趋于串行；叠加服务层 `synchronized`，UI 上「同时点击」大概率表现为先后到达，**不一定能观察到真正的交错执行**。
- 因此手工验证以**结果不变量**为准（第 8 节三条），而不是「必须看到两个请求打架」。要放大窗口：多客户端同时提交、或用 P903 / P908 做多人齐射。
- **真交错执行与落盘一致性已由第 7 节的 Access 真实库测试确定覆盖**：栅栏式 `CountDownLatch` 齐射 + 重新打开仓储断言落盘状态。手工场景表的定位是**演示、端到端复核与验收签字**，两者互补不重复；服务层的库存不足、余额不足、回滚路径另有 `StoreServiceTest` / `StoreConcurrencyTest` 确定性单测。
- 服务层锁是**单进程**语义：若将来出现多个服务端进程共用同一个 `.accdb`，`synchronized` 不再有效，届时只剩 `WHERE stock>=?` 这道数据层守卫（它仍保证不超卖，但 F3 那条回滚路径会从「防御性代码」变成「真实可达路径」）。当前部署是单进程，故不在本次验证范围。

---

## 10. 交付范围与验证记录

**本次新增 3 个文件，未改动任何既有文件**：

- `database/store-test-data.sql`（限量商品 + 并发买家钱包）
- `test-data/STORE_TEST_DATA.md`（本文）
- `server/src/test/java/cn/vcampus/server/AccessStoreConcurrencyTest.java`（Access 真实库并发验收测试，按评审第 2 条新增）

未改动：`seed.sql`、`schema.sql`、`rebuild.ps1`、`AccessDatabaseSchemaTest`、任何生产代码、任何其他模块。新增测试只放在 `server` 模块的 `src/test` 下，不影响打包产物。

**验证记录**（实际执行结果）：

```
① 专项脚本可执行性（rebuild.ps1 全链路）
.\database\rebuild.ps1 -DatabasePath database\store-test.accdb -AdditionalScript database\store-test-data.sql
→ Database rebuilt successfully（EXIT=0；新增 P910 与说明注释后仍全链路通过）

② 自动化验收测试（Access 真实库）
mvn -o -pl server -am "-Dtest=AccessStoreConcurrencyTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
→ Tests run: 9, Failures: 0, Errors: 0, Skipped: 0（65.08 s）；BUILD SUCCESS

③ 与本次改动相关的既有测试
mvn -o -pl server -am "-Dtest=AccessDatabaseSchemaTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
→ Tests run: 3, Failures: 0, Errors: 0（该测试断言 seed 商品数为 105，专项脚本不参与其建库，结果不受影响）

④ 重建后核对（以下数字现由 AccessStoreConcurrencyTest 第 9 个用例自动断言，不再依赖人工核对）
  商品总数     = 115（seed 105 + 新增 10）
  钱包账户总数 = 8（seed 4 + 新增 4）
  订单总数     = 3（seed 演示订单，未受影响）
  P901..P910   = 库存 1/2/5/3/0/10/3/20/2/5、单价与上架状态均与设计一致
  新增钱包     = demo_student_new 50000 分、demo_student_retake 50000 分、
                 demo_student_elective 150 分、demo_student_cross 100 分

⑤ seed.sql 原有商品库存核对（脚本扫描 105 条 INSERT）：
  最小库存 = 60，最大库存 = 300，库存小于 10 的商品 = 0 个
  → 确认原有演示数据无法支撑竞态验证，本次新增是必要的
```

生成物不入库：`database/store-test.accdb` 是本地重建产物，本 PR 只提交下面这 3 个文件（用**显式路径** `git add`，不使用 `git add .`，因此即使生成物还在本地也不会被误收）。

有问题随时找我，场景表里任何一条跑不出预期结果都算 bug，我这边跟进。

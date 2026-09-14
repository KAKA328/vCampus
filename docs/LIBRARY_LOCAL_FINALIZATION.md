# 图书馆子系统本地收尾记录

> 本文记录第一阶段（暂缓接口改动）的完成情况。后续已获授权继续三项 V4 扩展，当前状态、迁移说明及本轮验证见 [图书馆 V4 扩展](LIBRARY_V4_CATALOG.md)。下文“暂缓”“未改变接口”和 61/883 等数字仅对应第一阶段。

日期：2026-09-14。基线：主线提交 `1e3e546`（PR #70 合并后）。
本地分支：`feature/library-local-finalization`。

本轮仅修改图书馆子系统及相关测试、文档。未提交 commit、未推送 GitHub、未创建 PR。
使用独立工作目录，没有覆盖原来的演示代码、数据库或已经打开的客户端。

## 1. 完成范围

### 代码拆分和 JavaDoc

将超长页面、仓库及赔偿实现按职责拆分，保留原有调用入口。
图书馆生产 Java 文件共 61 个，含注释、空行均不超过 200 行。
不是把业务改成新的通信接口，而是把已有内部实现移入协作类。

| 原入口 | 原行数 | 当前行数 | 拆出的职责 |
| --- | ---: | ---: | --- |
| LibraryPanel | 1408 | 134 | 页面状态、布局、表格、馆藏操作、借还操作、赔偿、提醒、后台请求 |
| AccessLibraryRepository | 368 | 192 | 馆藏维护、SQL、行映射及连接清理 |
| InMemoryLibraryRepository | 348 | 192 | 演示馆藏及赔偿状态管理 |
| AccessLibraryCompensationService | 258 | 156 | 遗失登记事务、赔偿 SQL |
| LibraryReminderState | 233 | 197 | 本机提醒文件存储 |
| LibraryMessageHandler | 216 | 178 | 历史查询、管理员代还和 V2 兼容处理 |

主要内部协作关系：

- `LibraryPanel` 保留构造方法和页面生命周期，`LibraryViewState` 持有每个页面独立的控件与状态。
- `LibraryPageLayout`、`LibraryWorkspaceLayout`、`LibraryWidgets`、`LibraryTableStyles` 负责显示。
- `LibraryCatalogActions`、`LibraryCirculationActions`、`LibraryCompensationActions` 负责对应交互。
- `LibraryRequestRunner` 保留单次后台请求、忙碌状态及失败提示；付款和补库存不自动重试。
- `LibraryReminderController` 与 `LibraryReminderFileStorage` 分离提醒显示和本机持久化。
- `LibraryAccessCatalog`、`LibraryAccessSql`、`LibraryCompensationSql` 使用调用方的仓库或连接，不另建独立事务破坏原子性。

为实体、命令、接口和内部协作者补齐类、字段、构造方法、方法说明。
AST 检查覆盖 676 个具名声明，无缺失 JavaDoc；HTML 包括内部实现，方便课程讲解。
生成时进行 JavaDoc 结构、引用检查；声明说明由独立检查器检查，不要求每个简单 getter 重复书写 `@return` 标签。

### 允许新增 0 元图书

- 管理端新增表单允许 `0`、`0.00`，并直接给出说明和输入提示。
- 仍拒绝负数、空值、非数字、NaN 和无穷大；初始册数仍必须是正整数。
- 后端 `Book` 原本就接受有限非负价格，本轮不改变实体字段或价格含义。
- 学生无权新增图书；馆员通过原有新增命令可保存 0 元图书。
- 0 元价格不意味着免费修改他人借阅记录；遗失、赔偿和授权流程沿用现有实现。

### 四个图书管理改进项的处理

| 项目 | 本轮状态 | 原因或规则 |
| --- | --- | --- |
| 每人同时在借数量上限 | 已完成 | 仓库内部实现，不增加消息或 DTO 字段 |
| 实体副本管理、一册一码 | 暂缓 | 需要副本表、标识及借还契约调整 |
| 借阅时书名快照 | 暂缓 | 需要调整 BorrowRecord 和持久化字段 |
| 编辑已有图书资料与价格 | 暂缓 | 需要新增或扩展管理命令、服务入口及审计规则 |

这三项遵照“涉及接口改动的先不改”保留待办，不计入已完成。
现有借阅历史仍通过查询馆藏补齐书名，不等于保存了借阅当时的书名快照。
赔偿单已有自己的书名和金额快照，未改动。

## 2. 借阅上限规则

暂按默认方案：学生、教师统一最多同时在借 5 本。这个数值可以在服务端配置，未新增按角色差异化配置接口。

- 统计该读者状态为 `BORROWED` 的所有记录，逾期仍算在借。
- 已归还、已登记遗失和已赔偿不占在借额度；本轮未另外引入“有欠款就禁止借书”的规则。
- 剩余额度不足时整批拒绝，不会只借出部分图书，也不会扣减库存。
- 归还后释放额度，不同读者独立计数。
- 配置在仓库实例创建时读取，修改配置需要重启对应服务器。
- 旧库中如果已经超过上限，已有借阅不被删除，只是暂不能新增借阅。

不指定参数时为 5；例如改为 8，应把 JVM 参数放在 `-jar` 前面，并保留原来的服务器启动参数：

```powershell
java "-Dvcampus.library.maxActiveLoans=8" -jar server/target/vCampusServer.jar <原有服务器参数>
```

上面最后一项是说明性占位，不要照字面输入。仅客户端增加此参数不能改变服务器规则。
配置必须是正整数；空值、零、负值、非整数或超出整数范围将使仓库初始化失败，避免静默取消限制。

## 3. 并发、事务与兼容性

Access 和内存仓库均在原有共享图书仓库锁内完成“查询在借数量、检查上限、检查库存、创建借阅”。
Access 额度查询和库存、记录写入在同一连接事务中，失败全部回滚。
因此同一账号同时借不同图书也不能利用竞态突破数量上限。

赔偿付款保持先图书锁、后钱包锁的顺序，借阅状态、赔偿账单、钱包余额和钱包流水仍在同一 Access 事务提交。
拆分不改变原有扣款幂等性及失败回滚行为。

并发保障沿用“一台服务器内共享仓库实例”的部署方式；这不是为多个独立服务器进程同时写同一个 Access 文件设计的分布式锁。

未修改 `common` 消息类型、既有 DTO 字段、序列化版本、服务接口签名或数据库表结构。
对 23 个原有实体、命令、接口及基础服务文件进行忽略注释与空白的词法比较，业务代码与基线一致。
超限沿用 `CONFLICT` 状态及字符串提示，现有客户端能够显示。

今日已读仍按本机、服务器、用户隔离保存，次日再次提醒，不宣称跨机器同步。

## 4. 实际验证

环境：Windows、JDK 8u502、Maven 3.9.16；使用已有本机 Maven 缓存离线运行。

完整构建与测试命令（仓库根目录）：

```powershell
mvn -o -q "-Dmaven.repo.local=C:\Users\ASUS\.m2\repository" package "-DforkCount=0"
```

结果：进程退出码 0；883 项测试，0 失败、0 错误、0 跳过。

| 模块 | 测试数 |
| --- | ---: |
| common | 4 |
| user-management | 44 |
| student-management | 22 |
| course-selection | 78 |
| library | 40 |
| store | 180 |
| server | 312 |
| client | 203 |

本轮新增 13 项测试：

- `LibraryBookInputTest`：3 项，零元、普通价格和非法输入。
- `LibraryBorrowLimitTest`：5 项，配置、上限、批量原子性、归还与遗失释放额度、12 路同账号并发。
- `AccessLibraryBorrowLimitTest`：3 项，重开仓库后的额度、逾期计数、整批拒绝与归还、8 路并发及持久化结果。
- `LibraryMessageHandlerTest`：新增 2 项，零元新增权限及学生、教师通过旧命令受到相同上限约束。

现有界面布局、分类、提醒、书名、赔偿交互、库存并发及扣款失败回滚测试一并通过。
补充表单提示和整理注释后，再运行 `Library*` 定向测试并重新打包客户端。
完整日志保存在本地 `target/library-finalization-regression.log`；定向日志为 `target/library-finalization-focused.log`。
故障注入测试会打印预期异常栈，应以退出码和 Surefire XML 报告为准，不能把每个异常日志当成测试失败。

新增 Access 测试使用 JUnit 临时数据库，不导入、扣款或清空正在演示的数据库。
本次未重新开启多机器人工演示；自动化 UI 测试不能代替最终答辩前的实机联调。

## 5. 文档与构建产物

生成 JavaDoc（仓库根目录，JDK 8 的 `java`、`javac`、`javadoc` 和 Maven 位于 PATH）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Build-LibraryJavadoc.ps1
```

使用已有依赖缓存：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Build-LibraryJavadoc.ps1 -Offline -MavenRepository "C:\Users\ASUS\.m2\repository"
```

刚完成构建时可用 `-SkipBuild` 只生成说明。脚本同时检查 200 行限制和 JavaDoc 声明覆盖。

- JavaDoc：`target/library-javadoc/index.html`。
- 客户端：`client/target/vCampusClient.jar`。
- 服务器：`server/target/vCampusServer.jar`。
- 辅助源码：`scripts/LibraryDocInventory.java`，仅解析 Java 源文件，不执行项目业务、不修改数据库。

JAR、JavaDoc HTML 输出、测试日志和临时数据库都不是提交目标；今后提交时只包含源码、测试与必要文档。

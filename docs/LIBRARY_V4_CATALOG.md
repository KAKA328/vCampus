# 图书馆 V4：实体册、借阅书名快照与资料编辑

日期：2026-09-14。本轮接续分支 `feature/library-local-finalization`，在此前代码拆分、JavaDoc、0 元图书和借阅上限的基础上继续完成三项扩展。没有改动正在演示的数据库或关闭已有客户端。

本页保留 V4 功能阶段的验证记录；后续验收数据生成、32 项功能场景、10 组并发/联机场景及推送前最新结果见 [图书馆验收清单](../test-data/LIBRARY_ACCEPTANCE.md)。

## 完成的业务

### 1. 实体副本管理

- 每册书有独立的 `CP-UUID` 编号；“图书号”仍表示书目种类，不再与实体册混淆。
- 馆员在馆藏页选择图书，点击“实体副本”查看该书每册的编号及状态。
- 新增图书和增加库存同时创建新的实体册，编号不会复用。
- 借阅由服务器自动分配可借实体册，同批操作、同用户额度、库存及实体册预占在同一锁和事务内处理。
- 归还恢复该笔借阅绑定的实体册；重复归还不重复入库。
- 确认遗失把对应实体册标记为 `LOST`，减少总馆藏，不增加可借量。支付赔偿不把遗失册变回可借。
- 实体册查询不返回借阅人；只有 `LIBRARY_MANAGE` 权限可以查看整份副本清单。
- 原始库存中不可借但没有对应借阅记录的差额，标记为 `UNAVAILABLE`（旧库存待核对），不擅自释放为可借。核对后的人工纠错不在本次自动迁移范围内。
- 单次新增或补库最多生成 10,000 册，拒绝一次请求分配数十亿条实体记录；数值实体仍保留整数溢出校验。正常课程演示的册数不受影响。
- 旧书首次生成实体册时也适用 10,000 册的批次限制；若历史单种图书总量超过该值，本工具会拒绝回填，需要另行设计分批迁移，不能直接对正式库试错。

### 2. 借阅时书名快照

- 真正发起新借阅时，保存书名和实体册编号，后续改名、归还、遗失或赔偿都不重写该快照。
- 读者与管理员的新版历史使用 `LibraryLoanSnapshot`，显示“实体册编号”“书名来源”。
- 旧记录没有保存原始书名，迁移只能从迁移当时的馆藏回填，界面明确标记“迁移回填”。
- 已归还的旧记录无法推断当年实际拿走的是哪一册，副本编号保留 null，显示“历史未记录”，不编造关联。
- 仍在借的旧记录会分配迁移实体册编号，供后续准确归还；旧遗失记录保留遗失实体册。
- 改名前必须先完成该书旧记录的回填，避免用改名后的名称填充旧历史。
- 客户端展示优先使用快照，不再因馆藏名称变化或额外目录查询失败而丢失书名。

### 3. 编辑已有图书资料与价格

馆员在馆藏页选择图书，点击“编辑资料”，可以修改书名、作者、ISBN、分类、出版社、参考价格、馆藏位置。分类使用下拉框，价格允许 0 元。

图书号和库存不可编辑；补库继续使用“增加库存”。服务端重新校验字段长度、必填文字、有限非负价格，不信任客户端构造方法或隐藏按钮。

编辑请求包含打开表单时的原资料。另一管理员已修改资料时返回 `CONFLICT`，要求重新读取；并发借还或补库只改变数量时，不产生无意义冲突，保存时使用服务器最新库存。

Access 中资料更新和审计记录同一事务提交；审计保存服务器会话推导的操作者、时间、完整可编辑资料前后值。审计或提交失败则全部回滚。内存演示也记录本进程内的编辑审计，但不宣称重启后持久化。

价格规则仍与原赔偿方案一致：确认遗失时按当时馆藏原价生成金额快照，不是借阅日价格。编辑不改变已经生成的赔偿单，也不直接操作钱包。

## 对接与兼容性

| 新消息 | 请求 | 成功响应 | 服务端权限 |
| --- | --- | --- | --- |
| `LIBRARY_COPIES_V4` | `LibraryCopiesV4Command(token, bookId)` | `List<LibraryCopy>` | LIBRARY_MANAGE |
| `LIBRARY_HISTORY_V4` | `LibraryHistoryV4Command(token, targetUserId, allUsers)` | `List<LibraryLoanSnapshot>` | 本人 LIBRARY_READ；他人或全校 LIBRARY_MANAGE |
| `LIBRARY_BOOK_UPDATE_V4` | `LibraryBookUpdateV4Command(token, expected, replacement)` | 最新 `Book` | LIBRARY_MANAGE |

内部通过独立的 `LibraryCatalogV4` 扩展边界接入。旧 `LibraryService`、`LibraryRepository` 的方法签名保留；旧 `BorrowRecord`、`Book` 字段及序列化版本没有更改。现有 V2 借还、新增和补库在已经迁移的库上同步维护实体册；旧 V2/V3 历史响应仍为旧 DTO，不把新对象塞入旧消息。

`MessageType`、`ServerApplication` 分发、V4 Handler、服务实现及客户端调用一起更新。新增 DTO 必须与新客户端、服务端一起发布；不宣称新客户端可以直接连接不认识 V4 枚举的旧服务器。

没有迁移的旧库仍可使用既有 V2/V3 基础功能，V4 操作会明确失败并提示核对迁移，不能把旧库存当作已经跟踪实体册。部分建表的失败副本不应运行；应从未受影响的源库重新生成全新输出。

并发保障仍是“一台服务器进程内共享图书仓库实例”。不支持多个独立旧、新服务器同时写同一份 Access 数据库。旧客户端连接新版服务器可以继续旧业务；旧服务器不得写迁移后的副本。

## 数据库存储与升级

新增四张图书馆专属表，不修改商店、用户、学籍或选课表：

| 表 | 作用 |
| --- | --- |
| `tblBookCopy` | 实体册编号、书目编号、当前状态 |
| `tblLibraryLoanSnapshot` | 借阅记录号、保存的书名、可空副本号、回填标志 |
| `tblLibraryCopyCatalog` | 每个书目是否已经完成回填，含零库存书目 |
| `tblLibraryBookEdit` | 元数据编辑操作者、时间及前后值 |

正式 DDL 为 `database/migrations/017_library_copies_v4.up.sql`，也纳入新建库 `database/schema.sql`。不修改或重放 `seed.sql` 来“升级”旧库。

适配项目当前 UCanAccess 4.0.4：使用实测支持的主键和 MEMO 类型；本迁移不使用测试中未通过的命名外键/CREATE INDEX 语句。关联由共享仓库事务、条件状态更新及 `LibraryCopyIntegrity` 双向检查保障。不能据此认为直接在 Access 中随意改表仍然安全。类型和 SQL 背景可参考 [UCanAccess 官方说明](https://ucanaccess.sourceforge.net/site.html)；本项目是否可用以本机临时库测试为准。

### 推荐升级：保留源库，生成新副本

1. 停止所有使用源库的服务器，保证源文件不是运行中的写入快照。
2. 使用本分支构建好的服务端 JAR。
3. 在仓库根目录运行下面的脚本；输出必须是不存在的新文件。
4. 只有脚本成功且验收通过后，才让新版服务器指向新副本。原库继续保留作为回退依据。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Upgrade-LibraryDatabase.ps1 -Source "D:\demo\library-old.accdb" -Output "D:\demo\library-v4.accdb" -SourceStopped
```

路径只是示例，要替换为实际文件位置。`-SourceStopped` 表示操作者已经停止所有写入源库的服务器，不是脚本替你停服务。

工具拒绝原地覆盖、拒绝覆盖已有输出；只对新复制出来的文件执行建表和回填。失败时原库不变，失败副本留作诊断，不能启动使用。没有自动删除数据或盲目重建的回退脚本。

迁移检查包括：原库存能够容纳仍在借记录、实体册与可借/总量一致、同一实体册最多对应一条仍在借记录、关联两端存在、遗失状态一致。失败则回滚回填，不自动修改原库存“凑平”。

在已经迁移的源库上再次运行工具生成另一新副本，保留原实体册编号和快照，不重复创建。新增库加载 schema 和 seed 后，会在首次对应操作中在事务内完成回填；正式演示前建议先完成一次馆员实体册及全量历史检查。

## 验证与文档

功能专项测试已覆盖：

- 内存、Access 的一册一码、新增和补库、真实借阅时名称、精确归还、遗失后不复活。
- 两名管理员同时编辑只有一人成功，旧表单不会覆盖新资料；并发库存变化可保留。
- 多读者并发争抢不同实体册，不超卖、不重复分配。
- 借阅、归还、遗失、编辑提交失败时的副本、库存、记录、账单、审计回滚。
- 资料编辑后的既有赔偿金额、扣款幂等性及共享钱包流水。
- 学生/教师越权管理被拒、本人/他人/全校历史范围、非法载荷、真实主服务器分发。
- V4 命令及响应的序列化、客户端网络封装、旧 DTO 版本保留。
- 迁移不修改源库、重复迁移不重复编号、不一致库存拒绝、未知占用不擅自释放。
- 新管理按钮、忙碌状态、0 元编辑、书名来源及副本表格可读性。

完整构建通过：**913 项测试，0 失败、0 错误、0 跳过**，进程退出码 0；本轮新增 30 项测试。

| 模块 | 测试数 |
| --- | ---: |
| common | 4 |
| user-management | 44 |
| student-management | 22 |
| course-selection | 78 |
| library | 49 |
| store | 180 |
| server | 328 |
| client | 208 |

随后针对全部 V4 用例、迁移、实体册遗失、书名历史及布局测试再次定向运行并打包，退出码 0。检查源码时保留了上一轮拆分约束：**83 个图书馆生产 Java 文件均不超过 200 行；856 个具名声明均有 JavaDoc**，HTML 已重新生成。

升级脚本另在独立模拟旧库上执行成功，生成 `target/qa/upgrade-verified.accdb`；升级前后源文件 SHA-256 完全一致。这是验证用数据库，不是实际演示库，也不能替代完整项目数据。

```powershell
mvn -o -q "-Dmaven.repo.local=C:\Users\ASUS\.m2\repository" package "-DforkCount=0"
powershell -ExecutionPolicy Bypass -File scripts/Build-LibraryJavadoc.ps1 -SkipBuild
```

最终定向复核命令：

```powershell
mvn -o -q "-Dmaven.repo.local=C:\Users\ASUS\.m2\repository" -pl client -am "-Dtest=*Library*V4*Test,LibraryCopyMigrationTest,AccessLibraryCopyLossTest,LibraryHistoryTitleTest,LibraryPanelTest,LibraryInteractionTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DforkCount=0" package
```

输出位置：

- 新客户端：`client/target/vCampusClient.jar`
- 新服务器：`server/target/vCampusServer.jar`
- JavaDoc：`target/library-javadoc/index.html`
- 完整测试日志：`target/library-v4-regression.log`
- 功能专项测试日志：`target/library-v4-focused-tests.log`
- 最终定向复核日志：`target/library-v4-final-verification.log`
- 离屏界面检查：`target/qa/`

离屏检查使用真实 Swing 控件和独立内存数据，不连接实际服务器或扣款。JDK 8 的 Windows 本机主题在 headless 环境出现原生崩溃，因此离屏渲染通过 `-Dswing.noxp=true` 禁用该环境下的 XP 主题读取；未修改生产主题或客户端启动参数，也不把离屏截图当作多机器人工联调结果。

所有构建产物、临时数据库、截图和崩溃诊断日志都留在被忽略的 target 目录，不纳入源码提交。

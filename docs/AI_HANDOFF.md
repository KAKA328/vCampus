# Codex 项目交接

## 当前目标

基于最新 `origin/main` 整理 PR24 的选课公共 Socket 协议，完成选课、商店及整体模块的接口一致性审查，并在冲突解决、文档同步和测试通过后重新提交中文 PR。`main` 必须继续通过 Pull Request 更新。

## 已完成工作

- 已确认 GitHub 规则集已启用：`main` 禁止删除、禁止非快进强推、必须通过 PR，且至少需要 1 个审批。
- 已审查 PR24：PR `#24`（标题“升级选课Socket公共协议为V2”）仍为 `OPEN`，GitHub 状态为 `CONFLICTING` / `DIRTY`；CI `Java CI / build` 当前成功，但没有审查意见，不能直接合并。
- 已在分支 `codex/course-protocol-contract-cleanup`（基于 `origin/main` 的 `d438aa2`）整理显式 V2 选课协议。旧 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 恢复为旧课程级语义并标记弃用；完整流程改用查询轮次/教学班/已选记录、按教学班选课、按记录退选的三类 V2 消息。
- 已同步 `docs/SYSTEM_DESIGN.md`、`docs/MODULE_INTEGRATION_GUIDE.md` 和 `docs/whole-vCampus.puml`：旧协议、V2 协议和 `COURSE_MANAGE` 的边界、分发路由及客户端命令关系已统一；V2 客户端示例使用真实的 token 命令载荷。
- 已提交 `8155ef1` 并推送 `codex/course-protocol-contract-cleanup`，创建替代 PR `#25`（“整理选课 Socket 协议版本边界”）；后续商店修复提交为 `ed2d64c`、`4629b65`、`50105c7`。PR25 当前 `MERGEABLE`，CI 已通过，仅因等待审查而为 `BLOCKED`。
- 已检查商店模块：商品查询/分类、购买、本人订单、购物车、商品维护、全量订单和热销排行均已补齐服务接口、内存实现、服务端分发与权限校验；Access 商品/订单仓储已覆盖 `active`、全量订单和销量统计。历史上存在商店相关无 PR 直接提交（如 `83980dd`），但当前规则已对后续主线更新形成约束。
- 已审查各业务模块：用户管理和选课已有服务器/客户端主流程；学生学籍和图书馆仍只有基础接口/实体与部分内存实现，缺少完整 Handler、Access 仓储和客户端页面；选课真实 Access 闭环、档案绑定和教务管理仍未完成。服务器选课仍使用演示内存实现；使用 `--db` 时商店商品与订单改由 Access 仓储提供，购物车仍为进程内实现。
- 已检查 Swing 主题：`VCampusTheme` 已覆盖登录、主界面、用户管理、商店、选课页面；本次为 `CourseSelectionPanel` 和 `CourseManagementPanel` 统一按钮、表格、字体、边框和状态色，并增加主题回归测试。学生学籍/图书馆页面尚不存在，暂无主题接入点。
- 已新增本项目长期协作规范 `AGENTS.md`，约束主线保护、公共协议版本化、跨模块对接和验证要求。

## 已修改文件

当前协议整理工作区的代码和文档改动如下：

- 公共协议：`common/src/main/java/cn/vcampus/common/MessageType.java`、`common/src/main/java/cn/vcampus/common/README.md`。
- 选课命令：`course-selection/src/main/java/cn/vcampus/course/CourseQueryCommand.java`、`CourseSelectionCommand.java`、`CourseDropCommand.java`，以及新增 `CourseSelectionQueryV2Command.java`、`CourseSelectOfferingV2Command.java`、`CourseDropRecordV2Command.java`。
- 客户端与服务端：`client/src/main/java/cn/vcampus/client/service/RemoteCourseService.java`、`server/src/main/java/cn/vcampus/server/CourseMessageHandler.java`、`server/src/main/java/cn/vcampus/server/ServerApplication.java`、`server/src/main/java/cn/vcampus/server/StoreServiceFactory.java`。
- 测试：`common/src/test/java/cn/vcampus/common/MessageTest.java`；`course-selection/src/test/java/cn/vcampus/course/CourseQueryCommandTest.java`、`CourseSelectionCommandTest.java`、`CourseDropCommandTest.java`；`server/src/test/java/cn/vcampus/server/CourseMessageHandlerTest.java`、`ServerApplicationDispatchTest.java`。
- 文档：`docs/INTERFACES.md`、`docs/MODULE_INTEGRATION_GUIDE.md`、`docs/SYSTEM_DESIGN.md`、`docs/whole-vCampus.puml`、本文件和根目录 `AGENTS.md`。

协议整理、商店、数据库、主题和启动工厂改动均已提交并推送到 PR25，尚未进入 `main`。

## 已验证内容

- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn -q -pl common,course-selection,server,client -am test`，退出码为 `0`。
- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn clean test`，9 个模块全部成功，测试总数 211，失败/错误为 `0`。
- 执行 `git diff --check`，未发现空白错误。
- GitHub PR24 的 `Java CI / build` 检查为 `SUCCESS`，但 PR 仍为 `CONFLICTING` / `DIRTY` 而不可合并；PR25 最新 `Java CI / build` 也为 `SUCCESS`。

## 尚未解决的问题

- PR25 尚未获得至少一名成员审查，当前 `OPEN`、`MERGEABLE`、`BLOCKED`；PR24 仍保持 `OPEN`、`CONFLICTING`、`DIRTY`，不应强制合并。
- `docs/MODULE_INTEGRATION_GUIDE.md` 的旧协议处理器示例已明确标注为升级提示路径；`docs/SYSTEM_DESIGN.md` 和 `docs/whole-vCampus.puml` 已同步 V2 命名。
- 选课 V2 的真实 Access 数据联调、非演示学生档案绑定、并发选课及客户端页面刷新竞态仍需在整体验收前验证。
- 商店服务和服务端管理命令已实现并有内存/Access/Handler 测试；`RemoteStoreService` 和 `StorePanel` 目前只暴露查询、购买和本人订单，管理员维护、购物车客户端页面以及 Access 购物车持久化仍需补齐。`--db` 模式下商品和订单已持久化，但购物车结账跨多个 JDBC 连接，当前仍不能视为跨进程原子事务。
- 数据库迁移已修正为：全新数据库由 `schema.sql` 创建 `active`，旧商店库使用 `007_store_product_active.up.sql`；不要同时在 `004_store.up.sql` 和 `007` 重复添加字段。
- 根工作区 `D:\codex\java协作` 当前在分支 `codex/course-protocol-v2`，仅有未跟踪目录 `vcampus\`；该目录是本地项目副本，不应在本次协议 PR 中误加入。

## PR24 当前判断

暂不通过。GitHub 仍明确报告 `mergeable=CONFLICTING`、`mergeStateStatus=DIRTY`。CI 通过不能替代冲突解决和公共协议审查。PR24 与当前 V2 实现存在多处公共协议、客户端命令、Handler 和测试冲突；应关闭或以 PR25 替代，而不是强制合并旧 PR24。

## 下一步建议

1. 等待 PR25 至少一名成员审查；保持 `MERGEABLE` 后再通过 Pull Request 合并，不能强制合并 PR24。
2. PR25 合并后做选课、商店及用户会话的主线联调；确认 PR24 仍冲突后关闭旧 PR24。
4. 后续独立处理选课 V2 的真实 Access 数据联调、非演示学生档案绑定、并发选课与客户端刷新竞态，学生学籍/图书馆完整接入，以及商店管理员/购物车客户端和 Access 持久化。

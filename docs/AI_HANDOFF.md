# Codex 项目交接

## 当前目标

基于最新 `origin/main` 整理 PR24 的选课公共 Socket 协议，完成选课、商店及整体模块的接口一致性审查，并在冲突解决、文档同步和测试通过后重新提交中文 PR。`main` 必须继续通过 Pull Request 更新。

## 已完成工作

- 已确认 GitHub 规则集已启用：`main` 禁止删除、禁止非快进强推、必须通过 PR，且至少需要 1 个审批。
- 已审查 PR24：PR `#24`（标题“升级选课Socket公共协议为V2”）仍为 `OPEN`，GitHub 状态为 `CONFLICTING` / `DIRTY`；CI `Java CI / build` 当前成功，但没有审查意见，不能直接合并。
- 已在分支 `codex/course-protocol-contract-cleanup`（基于 `origin/main` 的 `d438aa2`）整理显式 V2 选课协议。旧 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 恢复为旧课程级语义并标记弃用；完整流程改用查询轮次/教学班/已选记录、按教学班选课、按记录退选的三类 V2 消息。
- 已同步 `docs/SYSTEM_DESIGN.md`、`docs/MODULE_INTEGRATION_GUIDE.md` 和 `docs/whole-vCampus.puml`：旧协议、V2 协议和 `COURSE_MANAGE` 的边界、分发路由及客户端命令关系已统一；V2 客户端示例使用真实的 token 命令载荷。
- 已提交 `8155ef1` 并推送 `codex/course-protocol-contract-cleanup`，创建替代 PR `#25`（“整理选课 Socket 协议版本边界”）。PR25 当前 `MERGEABLE`，CI 已通过，仅因等待审查而为 `BLOCKED`。
- 已检查商店模块：当前 `STORE_QUERY`、`STORE_PURCHASE`、`STORE_ORDER_QUERY` 使用 token-only 命令，服务端按会话解析 `userId`，未发现与选课相同的协议破坏点。历史上存在商店相关无 PR 直接提交（如 `83980dd`），但当前规则已对后续主线更新形成约束。
- 已新增本项目长期协作规范 `AGENTS.md`，约束主线保护、公共协议版本化、跨模块对接和验证要求。

## 已修改文件

当前协议整理工作区的代码和文档改动如下：

- 公共协议：`common/src/main/java/cn/vcampus/common/MessageType.java`、`common/src/main/java/cn/vcampus/common/README.md`。
- 选课命令：`course-selection/src/main/java/cn/vcampus/course/CourseQueryCommand.java`、`CourseSelectionCommand.java`、`CourseDropCommand.java`，以及新增 `CourseSelectionQueryV2Command.java`、`CourseSelectOfferingV2Command.java`、`CourseDropRecordV2Command.java`。
- 客户端与服务端：`client/src/main/java/cn/vcampus/client/service/RemoteCourseService.java`、`server/src/main/java/cn/vcampus/server/CourseMessageHandler.java`、`server/src/main/java/cn/vcampus/server/ServerApplication.java`。
- 测试：`common/src/test/java/cn/vcampus/common/MessageTest.java`；`course-selection/src/test/java/cn/vcampus/course/CourseQueryCommandTest.java`、`CourseSelectionCommandTest.java`、`CourseDropCommandTest.java`；`server/src/test/java/cn/vcampus/server/CourseMessageHandlerTest.java`、`ServerApplicationDispatchTest.java`。
- 文档：`docs/INTERFACES.md`、`docs/MODULE_INTEGRATION_GUIDE.md`、`docs/SYSTEM_DESIGN.md`、`docs/whole-vCampus.puml`、本文件和根目录 `AGENTS.md`。

上述文件中的协议整理改动尚未提交到分支；不要将它们误认为已进入 `main` 或 PR24。

## 已验证内容

- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn -q -pl common,course-selection,server,client -am test`，退出码为 `0`。
- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn clean test`，9 个模块全部成功，测试总数 199，失败/错误为 `0`。
- 执行 `git diff --check`，未发现空白错误。
- GitHub PR24 的 `Java CI / build` 检查为 `SUCCESS`，但 PR 仍因与最新 `main` 冲突而不可合并。

## 尚未解决的问题

- PR25 尚未获得至少一名成员审查；PR24 仍保持 `OPEN`、`CONFLICTING`、`DIRTY`，待 PR25 审查通过后关闭或替代。
- `docs/MODULE_INTEGRATION_GUIDE.md` 的旧协议处理器示例已明确标注为升级提示路径；`docs/SYSTEM_DESIGN.md` 和 `docs/whole-vCampus.puml` 已同步 V2 命名。
- 选课 V2 的真实 Access 数据联调、非演示学生档案绑定、并发选课及客户端页面刷新竞态仍需在整体验收前验证。
- 商店模块目前主要完成查询、购买和本人订单查询；管理员商品/库存维护及跨表事务的完整验收仍需由商店负责人补充证据。
- 根工作区 `D:\codex\java协作` 当前在分支 `codex/course-protocol-v2`，仅有未跟踪目录 `vcampus\`；该目录是本地项目副本，不应在本次协议 PR 中误加入。

## PR24 当前判断

暂不通过。原因是 GitHub 明确报告 `mergeable=CONFLICTING`、`mergeStateStatus=DIRTY`。CI 通过不能替代冲突解决和公共协议审查。应以最新 `origin/main` 为基线重新整理，而不是直接合并旧 PR24。

## 下一步建议

1. 等待 PR25 CI 完成并获得至少一名成员审查；确认仍与最新 `main` 无冲突后，再关闭或替代 PR24。
2. PR25 合并后做选课、商店及用户会话的主线联调。
3. 后续独立处理选课 V2 的真实 Access 数据联调、非演示学生档案绑定、并发选课与客户端刷新竞态，以及商店管理员库存维护验收。

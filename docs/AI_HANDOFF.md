# Codex 项目交接

## 当前状态

当前 `main` 已包含 PR25 的协议整理、商店修复、数据库说明和主题接入工作。后续继续遵守 `AGENTS.md`：不得直接提交或强推 `main`，所有主线更新都通过短分支和 Pull Request 进入。

当前文档修正分支为 `codex/update-handoff-pr25-status`，只更新交接状态，不携带此前临时补出的学籍兜底代码。

## PR 与分支状态

- PR25（“整理选课 Socket 协议版本边界”）已于 2026-08-31 合并到 `main`，合并提交为 `1a458ebba21447b078b6851d4eb2d5f34272bc48`，GitHub `Java CI / build` 为 `SUCCESS`。
- PR24 已关闭，关闭前状态为 `CONFLICTING` / `DIRTY`，不得再强制合并或作为主线入口。
- PR26 已关闭，基于较旧 `feature/store`，其商店功能已由 PR25/main 覆盖。
- PR27 已关闭。PR27 是主线联调时临时补出的“学籍基础闭环”草稿，不再作为合并入口，避免影响学生学籍子系统同学的正式分支合并；如需参考，可在本地分支 `codex/mainline-access-integration` 查看。

## 已进入 main 的主要工作

- 选课 Socket 协议已显式升级为 V2：
  - `COURSE_SELECTION_QUERY_V2`
  - `COURSE_SELECT_OFFERING_V2`
  - `COURSE_DROP_RECORD_V2`
- 旧 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 恢复为旧课程级语义并标记弃用，服务端收到旧选课消息时返回升级提示，不再混用 V2 字段。
- 商店已补齐商品查询/分类、购买、本人订单、购物车、商品维护、全量订单、热销排行协议；服务端分发、权限校验、内存服务和 Access 商品/订单仓储已同步。
- `--db` 模式下用户、商店商品和订单使用 Access；购物车仍为进程内实现，选课仍为演示内存实现。
- `VCampusTheme` 已覆盖登录、主界面、用户管理、商店、选课页面；`CourseSelectionPanel` 与 `CourseManagementPanel` 已统一按钮、表格、字体、边框和状态色。
- 数据库迁移已修正为：全新数据库由 `schema.sql` 创建 `active`，旧商店库使用 `007_store_product_active.up.sql`；不要同时在 `004_store.up.sql` 和 `007` 重复添加字段。

## 已验证内容

- PR25 合并前在 `D:\codex\vcampus-protocol-cleanup` 执行过 `mvn clean test`，9 个模块全部成功，测试总数 213，失败/错误为 `0`。
- PR25 的 GitHub `Java CI / build` 已成功。
- PR25 合并后曾做过一次本地草稿性质的 Access 主线联调和学籍兜底实现，验证登录、选课查询、商店查询/购买/订单、学生本人学籍查询可以跑通；该草稿未进入 main，不能作为当前主线已完成事实。

## 仍需处理的问题

- 学生学籍模块应优先由 `origin/feature/student-management` 的正式分支承接。后续要先审查该分支与当前 `main` 的差异，再决定是否拆分吸收其中的 Repository、Access 迁移、学业记录和页面改动。
- 图书馆仍缺少完整 Handler、Access 仓储、远程客户端服务和 Swing 页面。
- 选课 V2 仍需真实 Access 仓储、非演示学生档案绑定、并发选课和客户端刷新竞态验证。
- 商店客户端目前只暴露查询、购买和本人订单；商店管理员页面、购物车页面、Access 购物车持久化和严格跨 JDBC 连接事务仍待后续 PR。
- 还需要用真实服务器进程 + Swing 客户端 + 机房 `.accdb` 文件做人工演示截图。

## 下一步建议

1. 先审查 `origin/feature/student-management`，确认学生同学分支相对当前 `main` 新增了什么、冲突在哪里、哪些内容适合拆进主线。
2. 如果学生分支可整理，优先做“只补集成 glue code”的小 PR，不抢写子系统主体实现。
3. 如果学生分支短期不能合，再选择图书馆闭环作为下一个验收缺口：`LibraryMessageHandler`、Access 图书/借阅仓储、`RemoteLibraryService`、`LibraryPanel` 和借还测试。
4. 商店管理员/购物车界面、购物车持久化，以及选课 Access 仓储放到后续独立 PR。

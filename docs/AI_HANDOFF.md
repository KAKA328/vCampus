# Codex 项目交接

## 当前目标

基于 PR25 合并后的最新 `origin/main` 推进主线联调和验收缺口收敛。当前工作分支为 `codex/mainline-access-integration`，已完成 `--db` Access 模式下登录、选课查询、商店查询/购买/订单的自动化主线联调，并优先选择学生学籍模块打通基础闭环。`main` 必须继续通过 Pull Request 更新。

## 已完成工作

- 已确认 GitHub 规则集已启用：`main` 禁止删除、禁止非快进强推、必须通过 PR，且至少需要 1 个审批。
- 已审查 PR24：PR `#24`（标题“升级选课Socket公共协议为V2”）曾为 `CONFLICTING` / `DIRTY`，已由 PR25 替代并关闭，不能再作为合并入口。
- 已在分支 `codex/course-protocol-contract-cleanup`（基于 `origin/main` 的 `d438aa2`）整理显式 V2 选课协议。旧 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 恢复为旧课程级语义并标记弃用；完整流程改用查询轮次/教学班/已选记录、按教学班选课、按记录退选的三类 V2 消息。
- 已同步 `docs/SYSTEM_DESIGN.md`、`docs/MODULE_INTEGRATION_GUIDE.md` 和 `docs/whole-vCampus.puml`：旧协议、V2 协议和 `COURSE_MANAGE` 的边界、分发路由及客户端命令关系已统一；V2 客户端示例使用真实的 token 命令载荷。
- 已提交 `8155ef1` 并推送 `codex/course-protocol-contract-cleanup`，创建替代 PR `#25`（“整理选课 Socket 协议版本边界”）；后续商店修复提交为 `ed2d64c`、`4629b65`、`50105c7`、`ac338ef`、`d952999`。PR25 已于 2026-08-31 合并到 `main`，合并提交为 `1a458eb`，CI 已通过。
- 已检查商店模块：商品查询/分类、购买、本人订单、购物车、商品维护、全量订单和热销排行均已补齐服务接口、内存实现、服务端分发与权限校验；Access 商品/订单仓储已覆盖 `active`、全量订单和销量统计。历史上存在商店相关无 PR 直接提交（如 `83980dd`），但当前规则已对后续主线更新形成约束。
- 已审查 PR26：PR `#26` 基于较旧 `feature/store`，其商店功能已由 PR25/main 覆盖；PR26 与当前 `main` 冲突，已评论说明并关闭。
- 已审查各业务模块：用户管理、选课和商店已有服务器/客户端主流程；服务器选课仍使用演示内存实现；使用 `--db` 时用户、学生学籍、商店商品与订单改由 Access 仓储提供，购物车仍为进程内实现。
- 已检查 Swing 主题：`VCampusTheme` 已覆盖登录、主界面、用户管理、商店、选课页面；本次为 `CourseSelectionPanel` 和 `CourseManagementPanel` 统一按钮、表格、字体、边框和状态色，并增加主题回归测试。
- 本分支已完成学生学籍基础闭环：新增 `StudentQueryCommand`、`StudentUpdateCommand`、`InMemoryStudentManagementService`、`AccessStudentManagementService`、`StudentServiceFactory`、`StudentMessageHandler`、`RemoteStudentService` 和 `StudentInfoPanel`；学生本人查询由服务端按 token 绑定到 `tblStudent.user_id`，教务/管理员可按学号或班级查询并保存学生档案。
- 已新增本项目长期协作规范 `AGENTS.md`，约束主线保护、公共协议版本化、跨模块对接和验证要求。

## 已修改文件

PR25 已进入 `main` 的协议整理、商店、数据库、主题和启动工厂改动如下：

- 公共协议：`common/src/main/java/cn/vcampus/common/MessageType.java`、`common/src/main/java/cn/vcampus/common/README.md`。
- 选课命令：`course-selection/src/main/java/cn/vcampus/course/CourseQueryCommand.java`、`CourseSelectionCommand.java`、`CourseDropCommand.java`，以及新增 `CourseSelectionQueryV2Command.java`、`CourseSelectOfferingV2Command.java`、`CourseDropRecordV2Command.java`。
- 客户端与服务端：`client/src/main/java/cn/vcampus/client/service/RemoteCourseService.java`、`server/src/main/java/cn/vcampus/server/CourseMessageHandler.java`、`server/src/main/java/cn/vcampus/server/ServerApplication.java`、`server/src/main/java/cn/vcampus/server/StoreServiceFactory.java`。
- 测试：`common/src/test/java/cn/vcampus/common/MessageTest.java`；`course-selection/src/test/java/cn/vcampus/course/CourseQueryCommandTest.java`、`CourseSelectionCommandTest.java`、`CourseDropCommandTest.java`；`store/src/test/java/cn/vcampus/store/StoreServiceTest.java`；`server/src/test/java/cn/vcampus/server/CourseMessageHandlerTest.java`、`ServerApplicationDispatchTest.java`、`UserServiceFactoryTest.java`。
- 文档：`docs/INTERFACES.md`、`docs/MODULE_INTEGRATION_GUIDE.md`、`docs/SYSTEM_DESIGN.md`、`docs/whole-vCampus.puml`、本文件和根目录 `AGENTS.md`。

本分支新增改动如下：

- 学籍命令与服务：`student-management/src/main/java/cn/vcampus/student/StudentManagementService.java`、`StudentQueryCommand.java`、`StudentUpdateCommand.java`、`InMemoryStudentManagementService.java`。
- 学籍服务端：`server/src/main/java/cn/vcampus/server/AccessStudentManagementService.java`、`StudentServiceFactory.java`、`StudentMessageHandler.java`、`ServerApplication.java`、`server/pom.xml`。
- 学籍客户端：`client/src/main/java/cn/vcampus/client/service/RemoteStudentService.java`、`client/src/main/java/cn/vcampus/client/view/StudentInfoPanel.java`、`MainFrame.java`、`ModuleNavigationModel.java`、`client/pom.xml`。
- 联调与回归测试：`server/src/test/java/cn/vcampus/server/MainlineAccessIntegrationTest.java`、`StudentMessageHandlerTest.java`、`client/src/test/java/cn/vcampus/client/view/ModuleNavigationModelTest.java`。
- 文档：`README.md`、`common/src/main/java/cn/vcampus/common/README.md`、`database/README.md`、`docs/INTERFACES.md`、`docs/MODULE_INTEGRATION_GUIDE.md`、本文件。

## 已验证内容

- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn -q -pl common,course-selection,server,client -am test`，退出码为 `0`。
- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn clean test`，9 个模块全部成功，测试总数 213，失败/错误为 `0`。
- 本分支新增 `server/src/test/java/cn/vcampus/server/MainlineAccessIntegrationTest.java`，用临时 `.accdb` 初始化 `database/schema.sql` / `seed.sql`，在 `--db` Access 模式下覆盖 `demo_student` 登录、选课 V2 查询、学生本人档案查询、商店商品查询、购买和本人订单查询。
- 本分支新增学籍 Handler/客户端路由测试：学生只能查询 token 对应本人档案，教务管理员可按班级查询和保存，学生无权保存；学籍导航改为进入真实 `StudentInfoPanel`。
- 在 `D:\codex\vcampus-protocol-cleanup` 执行：`mvn clean test`，9 个模块全部成功，测试总数 219，失败/错误为 `0`。
- 执行 `git diff --check`，未发现空白错误。
- GitHub PR25 最新 `Java CI / build` 为 `SUCCESS`，PR25 已合并；PR24 和 PR26 已关闭。

## 尚未解决的问题

- 当前已完成自动化 Access 主线联调；还需要在真实服务器进程 + Swing 客户端上做人工演示截图，确认窗口交互、端口参数和机房 `.accdb` 文件路径无误。
- `docs/MODULE_INTEGRATION_GUIDE.md` 的旧协议处理器示例已明确标注为升级提示路径；`docs/SYSTEM_DESIGN.md` 和 `docs/whole-vCampus.puml` 已同步 V2 命名。
- 选课 V2 的真实 Access 仓储、非演示学生档案绑定、并发选课及客户端页面刷新竞态仍需在整体验收前验证；当前服务器选课仍是演示内存实现。
- 商店服务和服务端管理命令已实现并有内存/Access/Handler 测试；商品 ID 已限制为数据库 `VARCHAR(32)` 可容纳的长度，订单写入异常会触发库存补偿。`RemoteStoreService` 和 `StorePanel` 目前只暴露查询、购买和本人订单，管理员维护、购物车客户端页面以及 Access 购物车持久化仍需补齐。`--db` 模式下商品和订单已持久化，但购物车结账跨多个 JDBC 连接，当前仍不能视为跨进程原子事务。
- 学籍基础闭环已接入；课程历史、成绩、学业审查、教师档案绑定和独立账号-档案绑定页面仍未完成。当前 `AccessStudentManagementService.save` 插入新学生时默认以 `student_id` 写入 `user_id`，适合演示账号同名场景；正式绑定仍应补显式绑定命令或管理页面。
- 图书馆仍只有接口和实体，缺少完整 Handler、Access 仓储和客户端页面。
- 数据库迁移已修正为：全新数据库由 `schema.sql` 创建 `active`，旧商店库使用 `007_store_product_active.up.sql`；不要同时在 `004_store.up.sql` 和 `007` 重复添加字段。
- 自动化临时 Access 初始化会跳过 `CREATE INDEX` / `CREATE UNIQUE INDEX`，因为 UCanAccess 4.0.4 对部分 Access 索引 DDL 返回不支持；正式交付库可在 Access UI 中建索引，或在演示规模下先依赖主键和业务校验。
- 根工作区 `D:\codex\java协作` 当前在分支 `codex/course-protocol-v2`，仅有未跟踪目录 `vcampus\`；该目录是本地项目副本，不应在本次协议 PR 中误加入。

## PR24 当前判断

已关闭。PR24 与当前 V2 实现存在多处公共协议、客户端命令、Handler 和测试冲突，已由 PR25 替代；后续不得再强制合并旧 PR24。

## 下一步建议

1. 在真实服务器进程和 Swing 客户端上用 `--db` 手工跑一遍并截图：`demo_student` 登录、学籍本人页、选课查询、商店查询/购买/订单；再用 `demo_academic_admin` 验证学籍按学号/班级查询和保存。
2. 若继续补最影响验收的缺口，建议下一步打通图书馆闭环：`LibraryMessageHandler`、Access 图书/借阅仓储、`RemoteLibraryService`、`LibraryPanel` 和借还测试。
3. 之后再做选课 Access 仓储和真实学生档案绑定，让选课从演示内存数据迁移到 `tblCourse` / `tblCourseOffering` / `tblCourseSelection`。
4. 商店后续独立处理管理员/购物车客户端页面、Access 购物车持久化，以及跨 JDBC 连接的严格事务。

# 用户管理 E1-E3 阶段实验报告

## 1. 实验目标

本阶段围绕组长负责的用户管理模块推进，覆盖注册、登录、授权、登出、注销、角色权限、数据持久化接口、审计记录、服务器通信和 Swing 客户端入口。范围仍限定在用户管理与总控框架，不实现学生学籍、选课、图书馆、商店四个成员模块的具体业务逻辑。

## 2. 实验环境

| 项目 | 实际情况 |
|---|---|
| 操作系统 | Windows |
| Java | `25.0.2`，项目源码按 Java 8 兼容方式编写 |
| Maven | Apache Maven 3.9.16，已可执行 `mvn clean test` |
| Git 分支 | `feature/user-management` |
| 数据库目标 | Microsoft Access，已预留 `tblUser`、`tblAuditLog` 表结构与 UCanAccess 接入代码 |
| GUI 技术 | Java Swing，已完成登录页、注册页、主界面工作台式布局优化和输入提示优化 |
| Socket 演示端口 | 临时端口 `19192` / GUI 验证端口 `19195` |

## 3. E1 完成情况：内存版用户管理闭环

E1 已完成用户管理核心流程，确保在不依赖数据库和 GUI 的情况下，业务规则可以独立运行和测试。

| 编号 | 功能/用例 | 结果 |
|---|---|---|
| U01 | 同一账号重复注册，第二次返回 `CONFLICT` | 通过 |
| U02 | 未知账号和错误密码登录，均返回 `UNAUTHORIZED` 且提示一致 | 通过 |
| U03 | 正常登出后再次登出，第二次返回 `UNAUTHORIZED` | 通过 |
| U04 | A 的 token 尝试注销 B，返回 `UNAUTHORIZED`，B 仍可登录 | 通过 |
| U05 | 用户自注销后账号不可登录，且该用户旧会话全部失效 | 通过 |
| U06 | 无效角色编码注册，返回 `BAD_REQUEST` | 通过 |
| U07 | 公开注册尝试声明教师或管理员角色，返回 `BAD_REQUEST` | 通过 |

角色权限已拆分为 `Permission` 与 `RolePermissionPolicy`，避免权限判断散落在业务代码中。

| 角色 | 已验证允许权限 |
|---|---|
| `ADMIN` | 全部已定义权限 |
| `STUDENT` | 本人学籍、课程查询/选退课、图书查询/借还、商店查询/购买、自身账号 |
| `TEACHER` | 授课范围学籍/课程查询、成绩录入、图书查询/借还、商店查询/购买、自身账号 |
| `ACADEMIC_ADMIN` | 学籍维护、课程维护、成绩复核和学业审查 |
| `LIBRARIAN` | 图书查询/借还、图书管理 |
| `STORE_MANAGER` | 商品查询/购买、商品管理；无选课权限 |

完整权限编码和数据范围见 [`PERMISSIONS.md`](PERMISSIONS.md)。

## 4. E2 完成情况：持久化接口、审计与管理员注销

E2 已完成用户管理服务的分层改造，为 Access 数据库接入做好准备。

| 项目 | 完成内容 |
|---|---|
| 用户仓储接口 | 新增 `UserRepository`，把用户账号读取、保存、删除从业务服务中抽离 |
| 内存仓储实现 | 新增 `InMemoryUserRepository`，用于测试和无数据库运行 |
| 审计仓储接口 | 新增 `AuditLogRepository`，统一记录注册、登录、登出、注销等事件 |
| 内存审计实现 | 新增 `InMemoryAuditLogRepository`，用于验证审计记录 |
| 默认业务服务 | 新增 `DefaultUserManagementService`，统一承载注册、登录、授权、注销逻辑 |
| 会话管理 | 新增 `SessionManager`，管理 token 与用户会话 |
| 管理员注销 | 支持管理员注销他人账号，并清理该用户全部会话 |
| Access 接入代码 | 新增 `AccessUserRepository`、`AccessAuditLogRepository`、`UserServiceFactory` |
| 数据库脚本 | 更新 `database/schema.sql`，新增迁移脚本 `001_user_audit.up.sql` / `001_user_audit.down.sql` |

E2 新增自动化测试：

| 测试项 | 结果 |
|---|---|
| 注册账号可被另一个服务实例读取并登录 | 通过 |
| 管理员可注销其他用户并生成审计记录 | 通过 |
| 普通用户不能注销其他用户 | 通过 |

## 5. E3 完成情况：Swing 客户端与总控入口

E3 已完成客户端 GUI 框架和角色菜单入口，为后续成员模块接入预留位置。

| 项目 | 完成内容 |
|---|---|
| Swing 登录界面 | 新增 `LoginFrame`，支持账号、密码输入；服务器地址由启动参数配置，不在界面显示 |
| Swing 注册界面 | 新增 `RegisterDialog`，公开注册固定为学生角色，并提供输入框占位提示和错误提示 |
| 主界面总控 | 新增 `MainFrame`，登录后按角色显示可访问模块 |
| 角色菜单模型 | 新增 `ModuleNavigationModel`，集中控制不同角色能看到的模块 |
| 模块说明模型 | 新增 `ModuleDescriptor`，为主界面模块卡片提供标题、说明和接入状态 |
| GUI 主题 | 新增 `VCampusTheme`，统一颜色、字体、按钮和边距 |
| 远程用户服务 | 新增 `RemoteUserService`，封装注册、登录、登出请求 |
| Socket 客户端 | 新增 `SocketMessageClient`，负责客户端与服务器消息收发 |
| 客户端启动方式 | 默认启动 Swing GUI；使用 `--demo` 可运行命令行通信演示 |
| 界面优化 | 登录页改为左右分栏，注册页增加状态提示，主界面改为模块卡片式工作台 |
| 输入提示优化 | 账号、姓名、密码输入框增加灰色占位提示，输入内容后自动消失 |

E3 新增自动化测试：

| 测试项 | 结果 |
|---|---|
| 学生登录后可看到学籍信息、选课系统、图书馆、商店入口 | 通过 |
| 学生登录后不能看到用户管理入口 | 通过 |
| 管理员登录后可看到用户管理、学籍管理、选课管理、图书管理、商店管理入口 | 通过 |
| 学生模块卡片具备可读的说明文字和接入状态 | 通过 |
| 账号规则提示包含 1-32 位字母、数字或下划线要求 | 通过 |
| 姓名规则提示包含 1-64 位中文或英文要求 | 通过 |
| 密码规则提示包含 6-16 位要求 | 通过 |
| 用户注册对象拒绝中文账号、超长账号和超长姓名 | 通过 |

## 6. 服务器消息分发验证

服务器端已通过 `UserMessageHandlerTest` 验证用户相关消息分发。

| 请求 | 预期 | 结果 |
|---|---|---|
| `REGISTER + UserCredentials` | `OK` | 通过 |
| `LOGIN + UserCredentials` | `OK`，payload 为 `Session` | 通过 |
| `LOGIN + String` | `BAD_REQUEST` | 通过 |
| `AUTHORIZE + AuthorizationRequest` | 与用户服务结果一致 | 通过 |
| `COURSE_QUERY` 送入用户处理器 | `NOT_FOUND` | 通过 |
| `null` 请求 | `BAD_REQUEST` | 通过 |

## 7. Socket 五步演示结果

服务器启动：

```powershell
java -cp "common\target\classes;user-management\target\classes;server\target\classes" cn.vcampus.server.ServerApplication 19192
```

客户端运行：

```powershell
java -cp "common\target\classes;user-management\target\classes;client\target\classes" cn.vcampus.client.ClientApplication --demo --host 127.0.0.1 --port 19192
```

实际输出：

```text
demo-register REGISTER actual=OK expected=OK
demo-login LOGIN actual=OK expected=OK
demo-authorize-course-select AUTHORIZE COURSE_SELECT actual=OK expected=OK
demo-logout LOGOUT actual=OK expected=OK
demo-authorize-old-token AUTHORIZE OLD_TOKEN actual=UNAUTHORIZED expected=UNAUTHORIZED
```

结果满足 `OK -> OK -> OK -> OK -> UNAUTHORIZED`。

## 8. Maven 自动化测试结果

已执行：

```powershell
mvn clean test
```

本阶段测试全部通过：

| 模块 | 测试数量 | 结果 |
|---|---:|---|
| `common` | 2 | 通过 |
| `user-management` | 14 | 通过 |
| `server` | 5 | 通过 |
| `client` | 4 | 通过 |
| 合计 | 25 | 通过 |

## 9. 安全与规范检查

- 密码通过 PBKDF2 哈希保存，不保存明文密码。
- 登录失败统一返回 `invalid credentials`，避免泄露账号是否存在。
- token 通过 `SecureRandom` 生成，并在登出、注销后失效。
- 自注销和管理员注销都会清理对应用户全部会话。
- 审计记录已覆盖注册、登录、登出、注销等关键操作。
- GUI 不在界面中显示密码或 token；密码字段读取后会清空临时字符数组。
- 用户显示名进入 HTML 占位说明前会进行转义，避免被界面解析为标签。
- Access 数据库接入代码不把数据库路径硬编码死，支持通过启动参数传入。
- 当前 Java 主代码文件体量可控，没有单个文件过度膨胀。

## 10. 当前限制与下一步

| 阶段 | 后续事项 |
|---|---|
| E2 | 准备实际 `.accdb` 文件后，使用 `--db 数据库路径` 启动服务器，进行真实 Access 读写验证 |
| E3 | 启动服务器后运行客户端 GUI，人工检查登录、注册、角色菜单、退出登录体验 |
| E4 | 打包可运行程序，补充用户使用说明书、最终测试截图和验收清单 |
| 集成 | 等其他组员模块完成后，把学籍、选课、图书馆、商店页面替换到主界面占位面板中 |

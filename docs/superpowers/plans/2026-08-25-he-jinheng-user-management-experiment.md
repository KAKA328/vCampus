# 何锦恒用户管理与总控实验实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> 设计口径更新：本文件是 2026-08-25 的阶段实施计划，保留当时“注册”一词的历史语境。当前主线设计已调整为“登录页不提供公开自助注册；账号由系统管理员开户注册或由初始化脚本预置”，详见 `docs/EXTERNAL_VCAMPUS_RESEARCH.md`、`docs/SYSTEM_DESIGN.md` 和 `docs/PERMISSIONS.md`。

**Goal:** 先在今晚完成用户注册、登录、授权、登出和注销的可测试 Socket 闭环，再按后续实验接入 Access、Swing 界面和全系统集成。

**Architecture:** 客户端只构造 `Message` 并显示结果，服务器统一完成载荷校验、会话校验、权限判定和业务调用。用户业务依赖 `UserRepository`、`SessionManager` 和 `PermissionPolicy`，先使用内存实现保证联调，再替换为 Access 持久化，不改变客户端协议。

**Tech Stack:** Java 8、Maven 多模块、JUnit 5、Socket + ObjectInputStream/ObjectOutputStream、Swing、Access、Git/GitHub。

---

## 0. 当前基线与边界

已存在：

- `common` 中的 `Message`、`MessageType`、`StatusCode`、`Role`、`User`。
- `user-management` 中的五个业务接口、内存实现、PBKDF2 密码哈希和两个基础测试。
- `server` 中的用户消息分发和多客户端 Socket 骨架。
- `client` 中的注册+登录命令行演示。
- `database/schema.sql` 中的 `tblUser` 初始表结构。

当前缺口：

- 权限规则还是硬编码字符串，无完整角色权限矩阵。
- 账号、会话、权限全部挤在一个内存 Service 中，不利于替换 Access。
- 服务器消息分发和 Socket 完整流程缺少自动化验证。
- 管理员注销他人账号、审计记录、Access 持久化、Swing 页面和可独立运行的最终 JAR 尚未完成。

今晚只做 **E1 内存版用户管理闭环**，不修改学籍、选课、图书馆和商店模块，不开始 Swing 美化。

2026-08-25 环境实测：本机 Java 25.0.2 可用，可通过 `--release 8` 检查 Java 8 兼容性；`mvn` 命令尚未安装。

## 1. 文件责任图

### 今晚修改

- Modify: `user-management/src/main/java/cn/vcampus/user/InMemoryUserManagementService.java` — 收敛输入校验、会话失效和授权行为。
- Create: `user-management/src/main/java/cn/vcampus/user/Permission.java` — 定义稳定权限编码，消除散落字符串。
- Create: `user-management/src/main/java/cn/vcampus/user/RolePermissionPolicy.java` — 集中管理角色权限矩阵。
- Modify: `user-management/src/test/java/cn/vcampus/user/InMemoryUserManagementServiceTest.java` — 补全五类业务测试。
- Create: `server/src/test/java/cn/vcampus/server/UserMessageHandlerTest.java` — 验证协议载荷和状态码映射。
- Modify: `server/pom.xml` — 为服务器模块加入 JUnit 5 测试依赖。
- Modify: `client/src/main/java/cn/vcampus/client/ClientApplication.java` — 输出完整五步演示结果。
- Create: `docs/USER_MANAGEMENT_TEST_REPORT.md` — 记录命令、用例、实际结果和未完成项。

### 后续实验新增

- `user-management/.../UserRepository.java` — 用户持久化抽象。
- `user-management/.../InMemoryUserRepository.java` — 内存测试替身。
- `server/.../AccessUserRepository.java` — Access/JDBC 实现。
- `server/.../SessionManager.java` — 会话创建、查询、过期和销毁。
- `client/.../LoginFrame.java`、`RegisterDialog.java`、`MainFrame.java` — Swing 用户入口。
- `database/schema.sql`、`database/seed.sql` — `tblUser`、`tblAuditLog` 和脱敏演示数据。

## 2. 今晚 E1 时间表（约 3 小时 30 分）

| 开始后 | 内容 | 阶段证据 |
|---|---|---|
| 0:00-0:20 | 保护现有文件、同步 Git、创建用户模块分支 | `git status` 显示已在 `feature/user-management` |
| 0:20-1:10 | 先写失败测试，补齐注册/登录/登出/注销边界 | 测试名和预期状态码清晰 |
| 1:10-2:00 | 实现 `Permission` 与角色权限矩阵 | 管理员允许、学生允许/拒绝测试通过 |
| 2:00-2:10 | 休息并复查改动边界 | 未修改其他四个业务模块 |
| 2:10-2:50 | 补服务器消息分发测试 | 错误载荷返回 `BAD_REQUEST` |
| 2:50-3:15 | 扩展客户端五步 Socket 演示 | 终端显示注册、登录、授权、登出、失效令牌结果 |
| 3:15-3:30 | 写测试报告、查差异、分次提交 | 工作树只剩明确保留的草稿 |

## 3. Task 1：保护现场并创建功能分支（20 分钟）

**Files:**

- Preserve: `docs/软件使用说明书草稿.md`
- Preserve: `docs/项目进度安排草稿.md`

- [ ] **Step 1: 在 VSCode 底部终端确认现场**

```powershell
cd 'D:\codex\java协作'
git status --short --branch
```

Expected: 当前基线是 `main...origin/main [ahead 1]`，并有两个未跟踪草稿。不删除、不改名、不用 `git add .` 误收进代码提交。

- [ ] **Step 2: 推送已经提交的 main 基线**

```powershell
git push origin main
```

Expected: 远端 `main` 前进到本地最新提交，不会包含两个未跟踪草稿。

- [ ] **Step 3: 查看功能分支是否已存在**

```powershell
git branch --list feature/user-management
```

Expected: 无输出则创建分支；有输出则直接切换。

```powershell
git switch -c feature/user-management
```

如分支已存在，改用：

```powershell
git switch feature/user-management
```

- [ ] **Step 4: 检查 Java 和 Maven**

```powershell
java -version
mvn -version
```

Expected: Java 可用；Maven 如仍未安装，今晚可先用现有 `javac --release 8` 编译校验，但本地 JUnit 结果标记为“待 Maven 验证”，并以 GitHub Actions 为补充证据。

Maven 不可用时的精确编译备用命令：

```powershell
$vcampusSources = rg --files -g '*.java' -g '!**/src/test/**'
New-Item -ItemType Directory -Force '.tmp-classes-user-e1' | Out-Null
javac --release 8 -encoding UTF-8 -d '.tmp-classes-user-e1' $vcampusSources
```

Expected: 命令退出码为 0，没有 Java 编译错误。`.tmp-classes-user-e1` 已被 `.gitignore` 覆盖，不会进入提交。

- [ ] **Step 5: 在功能分支提交本计划**

```powershell
git add docs/superpowers/plans/2026-08-25-he-jinheng-user-management-experiment.md
git commit -m "docs(plan): add user management experiment plan"
```

Expected: 计划进入 `feature/user-management`，两个原有草稿仍保持未跟踪。

## 4. Task 2：补齐用户业务测试（50 分钟）

**Files:**

- Modify: `user-management/src/test/java/cn/vcampus/user/InMemoryUserManagementServiceTest.java`
- Modify: `user-management/src/main/java/cn/vcampus/user/InMemoryUserManagementService.java`

- [ ] **Step 1: 先增加以下失败用例**

| 用例 | 操作 | 预期 |
|---|---|---|
| U01 | 同一账号注册两次 | 第二次 `CONFLICT` |
| U02 | 未知账号和错误密码登录 | 都是 `UNAUTHORIZED` 且提示一致 |
| U03 | 登出后再登出 | 第二次 `UNAUTHORIZED` |
| U04 | A 的 token 尝试注销 B | `UNAUTHORIZED` 或 `FORBIDDEN`，B 仍可登录 |
| U05 | 自注销后再登录 | `UNAUTHORIZED` |
| U06 | 无效角色代码注册 | `BAD_REQUEST` |

- [ ] **Step 2: 运行单模块测试并确认新用例先失败**

```powershell
mvn -pl user-management -am test
```

Expected: 新增且尚未实现的边界用例失败；不允许因测试本身编译错误而失败。

- [ ] **Step 3: 只修改 Service 使上述用例通过**

实现约束：

- 登录失败一律返回 `invalid credentials`，不暴露账号是否存在。
- 自注销成功后删除账号并销毁该账号的全部会话。
- 他人的 token 不能自注销目标账号。
- 密码仍只保存 PBKDF2 哈希，日志和响应中不输出明文密码。

- [ ] **Step 4: 再次运行测试**

```powershell
mvn -pl user-management -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 精确提交用户核心行为**

```powershell
git add user-management/src/main/java/cn/vcampus/user/InMemoryUserManagementService.java user-management/src/test/java/cn/vcampus/user/InMemoryUserManagementServiceTest.java
git commit -m "test(user): cover account and session lifecycle"
```

## 5. Task 3：建立角色权限矩阵（50 分钟）

**Files:**

- Create: `user-management/src/main/java/cn/vcampus/user/Permission.java`
- Create: `user-management/src/main/java/cn/vcampus/user/RolePermissionPolicy.java`
- Modify: `user-management/src/main/java/cn/vcampus/user/InMemoryUserManagementService.java`
- Modify: `user-management/src/test/java/cn/vcampus/user/InMemoryUserManagementServiceTest.java`

- [ ] **Step 1: 写权限矩阵测试**

| 角色 | 应允许 | 应拒绝 |
|---|---|---|
| `ADMIN` | 任意已定义权限 | 无效权限编码 |
| `STUDENT` | `USER_SELF_READ`、`COURSE_READ`、`COURSE_SELECT`、`LIBRARY_READ`、`LIBRARY_BORROW`、`STORE_READ`、`STORE_PURCHASE` | `USER_MANAGE`、`STUDENT_WRITE`、`LIBRARY_MANAGE`、`STORE_MANAGE` |
| `TEACHER` | `USER_SELF_READ`、`STUDENT_READ`、`COURSE_READ` | `USER_MANAGE`、`STORE_MANAGE` |
| `LIBRARIAN` | `LIBRARY_READ`、`LIBRARY_MANAGE` | `USER_MANAGE`、`STORE_MANAGE` |
| `STORE_MANAGER` | `STORE_READ`、`STORE_MANAGE` | `USER_MANAGE`、`LIBRARY_MANAGE` |

- [ ] **Step 2: 建立权限枚举与集中策略**

`Permission` 不存储显示文本，只定义稳定业务编码。`RolePermissionPolicy` 只提供一个纯函数式判定：

```java
boolean isAllowed(Role role, Permission permission)
```

`UserManagementService.authorize(String token, String permission)` 暂时保持协议兼容，在服务内将字符串转为 `Permission`；无效编码返回 `BAD_REQUEST`，已登录但无权返回 `FORBIDDEN`。

- [ ] **Step 3: 运行权限和用户模块测试**

```powershell
mvn -pl user-management -am test
```

Expected: 未登录为 `UNAUTHORIZED`，无效权限编码为 `BAD_REQUEST`，已登录但无权为 `FORBIDDEN`，其余为 `OK`。

- [ ] **Step 4: 提交权限基线**

```powershell
git add user-management/src/main/java/cn/vcampus/user/Permission.java user-management/src/main/java/cn/vcampus/user/RolePermissionPolicy.java user-management/src/main/java/cn/vcampus/user/InMemoryUserManagementService.java user-management/src/test/java/cn/vcampus/user/InMemoryUserManagementServiceTest.java
git commit -m "feat(auth): add role permission policy"
```

## 6. Task 4：验证服务器消息分发（40 分钟）

**Files:**

- Modify: `server/pom.xml`
- Create: `server/src/test/java/cn/vcampus/server/UserMessageHandlerTest.java`

- [ ] **Step 1: 在 `server/pom.xml` 增加 JUnit 5 测试依赖**

依赖版本继承父 POM 的 `${junit.version}`，不在子模块重复硬编码版本。

- [ ] **Step 2: 先写处理器测试**

| 请求 | 预期 |
|---|---|
| `REGISTER + UserCredentials` | `OK` |
| `LOGIN + UserCredentials` | `OK` 且 payload 为 `Session` |
| `LOGIN + String` | `BAD_REQUEST` |
| `AUTHORIZE + AuthorizationRequest` | 与 Service 结果一致 |
| `COURSE_QUERY` 送入用户处理器 | `NOT_FOUND` |
| `null` 请求 | `BAD_REQUEST` |

- [ ] **Step 3: 运行服务器及依赖模块测试**

```powershell
mvn -pl server -am test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 4: 提交协议边界测试**

```powershell
git add server/pom.xml server/src/test/java/cn/vcampus/server/UserMessageHandlerTest.java
git commit -m "test(server): verify user message dispatch"
```

## 7. Task 5：完成 Socket 五步演示（25 分钟）

**Files:**

- Modify: `client/src/main/java/cn/vcampus/client/ClientApplication.java`

- [ ] **Step 1: 将演示流程固定为五步**

1. 注册演示学生，期望 `OK`。
2. 登录并从响应中取得 `Session.token`，期望 `OK`。
3. 用 token 检查 `COURSE_SELECT`，期望 `OK`。
4. 登出，期望 `OK`。
5. 再用旧 token 检查 `COURSE_SELECT`，期望 `UNAUTHORIZED`。

每一步输出 `requestId + operation + actual + expected`，不输出密码和完整 token。

- [ ] **Step 2: 先启动服务器，再在第二个 VSCode 终端启动客户端**

Maven 可用时：

```powershell
mvn -pl server -am package -DskipTests
mvn -pl client -am package -DskipTests
```

当前 JAR 仍可能是不含依赖的薄 JAR，因此 E1 可继续使用 IDE 运行 `ServerApplication.main` 和 `ClientApplication.main`。可独立运行双 JAR 在 E4 统一解决。

Expected terminal output:

```text
REGISTER actual=OK expected=OK
LOGIN actual=OK expected=OK
AUTHORIZE COURSE_SELECT actual=OK expected=OK
LOGOUT actual=OK expected=OK
AUTHORIZE OLD_TOKEN actual=UNAUTHORIZED expected=UNAUTHORIZED
```

- [ ] **Step 3: 提交演示客户端**

```powershell
git add client/src/main/java/cn/vcampus/client/ClientApplication.java
git commit -m "feat(client): demonstrate user session lifecycle"
```

## 8. Task 6：记录实验并推送分支（15 分钟）

**Files:**

- Create: `docs/USER_MANAGEMENT_TEST_REPORT.md`

- [ ] **Step 1: 写测试报告**

报告必须包含：实验目标、环境、实际执行命令、U01-U06 结果、Socket 五步结果、当前限制、下一步 Access 计划。如 Maven 未安装，不得写“JUnit 全部通过”，应如实写“源码编译/Socket 手工验证通过，JUnit 待 Maven 或 CI 验证”。

- [ ] **Step 2: 运行全仓验证**

```powershell
mvn clean test
```

Expected: Maven 可用时为 `BUILD SUCCESS`；否则运行现有 Java 8 兼容编译流程，并在报告中记录局限。

- [ ] **Step 3: 检查差异**

```powershell
git status --short
git diff --check
git log --oneline -5
```

Expected: 没有空白错误；未跟踪草稿仍可保留，不误入用户模块提交。

- [ ] **Step 4: 提交报告并推送分支**

```powershell
git add docs/USER_MANAGEMENT_TEST_REPORT.md
git commit -m "docs(user): record user management experiment"
git push -u origin feature/user-management
```

Expected: GitHub 上出现 `feature/user-management`，但今晚不直接向 `main` 强制推送。测试绿灯后再建 Pull Request。

## 9. 今晚停止条件

同时满足以下条件即结束 E1，不临时扩大范围：

- 用户注册、登录、登出、自注销和授权的正常/异常行为有明确测试。
- 权限判断不再依赖 `"user:read"` 单个硬编码字符串。
- Socket 终端演示能清楚显示 `OK -> OK -> OK -> OK -> UNAUTHORIZED`。
- 密码不明文存储，日志不输出密码和完整 token。
- 修改按 3-4 个小提交推送到 `feature/user-management`。
- `docs/USER_MANAGEMENT_TEST_REPORT.md` 客观记录通过项和未完成项。

## 10. 后续实验安排

| 实验 | 主要任务 | 验收结果 |
|---|---|---|
| E2 持久化（3-4h） | 抽取 `UserRepository`；完成 `AccessUserRepository`；建 `tblUser`、`tblAuditLog`；实现 active 停用/注销和管理员注销他人 | 服务器重启后账号仍可登录，敏感操作有审计记录 |
| E3 客户端界面（3h） | 登录页、注册对话框、主界面用户信息/登出；按权限显示导航 | 无需终端即可演示用户闭环，但服务器仍会二次校验权限 |
| E4 总控与发布（3-4h） | 请求路由、线程池、配置化主机/端口、可独立运行的胖 JAR、启动文档 | `java -jar vCampusServer.jar` 和 `java -jar vCampusClient.jar` 在验收机正常启动 |
| E5 组长集成（持续） | 审查公共 `Message`、状态码和数据库命名变更；合并四个业务模块；跑验收清单 | `main` 始终可构建，五模块通过同一会话和权限机制联调 |

## 11. 验收映射

| 设计说明书要求 | 实验对应 |
|---|---|
| 用户开户注册/注销、登录/登出、授权 | E1 + E2 + E3 |
| 客户端/服务器端逻辑分层 | E1 抽取权限，E2 抽取 Repository/SessionManager |
| `Message` 通信协议 | E1 处理器测试和 Socket 五步演示 |
| 实体类和数据库表结构 | E2 `UserRepository`、`tblUser`、`tblAuditLog` |
| 界面原型 | E3 Swing 登录/注册/主界面 |
| 项目总控、模块集成和进度管理 | E4 + E5，每次 PR 附测试证据和文档更新 |

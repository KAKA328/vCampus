# vCampus

Java 课程实践项目：基于 C/S、Socket 和多线程的虚拟校园系统。

## 模块

- `common`：客户端和服务器端共享的消息协议、实体和通用结果类型。
- `user-management`：用户注册、注销、登录、登出和授权（组长负责）。
- `student-management`：学生学籍管理接口（组员负责）。
- `course-selection`：选课系统接口（组员负责）。
- `library`：图书馆接口（组员负责）。
- `store`：商店接口（组员负责）。
- `server`：服务器启动入口和请求分发骨架。
- `client`：客户端启动入口和调用骨架。

## 环境

- JDK 8 或更高版本，最终验收必须兼容 JDK 8。
- Access 数据库（数据库名约定为 `vCampus`）。
- Maven 3.8+ 或 IDE 内置 Maven。

## 构建与运行

```bash
mvn clean test
mvn package
java -jar server/target/vCampusServer.jar
java -jar client/target/vCampusClient.jar
```

当前骨架提供协议、接口、入口和测试数据脚本；用户管理已有内存演示实现，其他业务模块由各分支通过 Pull Request 实现。

## 初始化系统管理员

公开注册只创建学生账号。首次启动服务器前，在同一个 PowerShell 终端设置初始化管理员；密码只放在当前终端环境变量中，不写入代码或 Git：

```powershell
$env:VCAMPUS_BOOTSTRAP_ADMIN_ID="admin001"
$env:VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD="Admin123"
$env:VCAMPUS_BOOTSTRAP_ADMIN_NAME="系统管理员"
java -cp "common\target\classes;user-management\target\classes;server\target\classes" cn.vcampus.server.ServerApplication --port 19195
```

服务器启动时会创建 `ADMIN` 账号；登录时使用上面的账号和密码。内存模式下服务器关闭后账号会消失，因此下次启动仍需保留这些环境变量；接入 Access 后账号写入数据库，后续启动不会重置已存在账号的密码。用完可在另一个 PowerShell 中执行 `Remove-Item Env:VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD` 清除当前终端变量。

## 设计基线

- [项目进度计划报告](docs/PROJECT_PLAN.md)
- [软件设计说明书草案](docs/SYSTEM_DESIGN.md)
- [接口基线](docs/INTERFACES.md)
- [角色权限矩阵](docs/PERMISSIONS.md)
- [模块对接说明](docs/MODULE_INTEGRATION_GUIDE.md)
- [验收清单](docs/ACCEPTANCE_CHECKLIST.md)

## 协作约定

采用 GitHub Flow：`main` 保持可构建，每项工作从 `main` 创建短期分支，例如：

```bash
git checkout -b feature/student-management
git commit -m "feat(student): add student management contracts"
git push -u origin feature/student-management
```

通过 Pull Request 合并，禁止直接向 `main` 推送未审查代码。提交信息使用 Conventional Commits 格式。

## 目录约定

每个业务模块按 `api`、`domain`、`service`、`repository` 分层；客户端和服务器共享的类型只能放在 `common`，避免循环依赖。

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

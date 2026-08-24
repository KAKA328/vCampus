# 用户管理 E1 实验报告

## 1. 实验目标

本次实验完成“内存版用户管理闭环”，范围限定为用户注册、登录、授权、登出、自注销及服务器 Socket 消息分发验证。不接入 Access 数据库，不制作 Swing 界面，不修改学生学籍、选课、图书馆、商店四个业务模块。

## 2. 实验环境

| 项目 | 实际情况 |
|---|---|
| 操作系统 | Windows |
| Java | `25.0.2`，使用 `javac --release 8` 检查 Java 8 兼容性 |
| Maven | 本机未安装，`mvn clean test` 无法执行 |
| Git 分支 | `feature/user-management` |
| 服务器端口 | Socket 演示使用临时端口 `19191` |

## 3. 实际执行命令

```powershell
git push origin main
git switch -c feature/user-management

$mainSources = rg --files -g '*.java' -g '!**/src/test/**' -g '!**/.tmp-classes-user-e1/**'
javac --release 8 -encoding UTF-8 -d '.tmp-classes-user-e1-final\classes' $mainSources

mvn clean test
```

`javac --release 8` 编译通过，仅出现 Java 25 对 `--release 8` 的过时警告。`mvn clean test` 失败原因是本机未安装 Maven，因此正式 JUnit 测试结果需后续在安装 Maven 后或通过 GitHub Actions 补跑。

## 4. 用户业务用例结果

本地使用临时 Java 验证入口 `UserManagementE1Check` 运行以下场景，结果均通过：

| 编号 | 用例 | 结果 |
|---|---|---|
| U01 | 同一账号注册两次，第二次返回 `CONFLICT` | 通过 |
| U02 | 未知账号和错误密码登录，均返回 `UNAUTHORIZED` 且提示一致 | 通过 |
| U03 | 登出后再次登出，第二次返回 `UNAUTHORIZED` | 通过 |
| U04 | A 的 token 尝试注销 B，返回 `UNAUTHORIZED`，B 仍可登录 | 通过 |
| U05 | 自注销后账号不可登录，且该用户全部旧会话失效 | 通过 |
| U06 | 无效角色代码注册，返回 `BAD_REQUEST` | 通过 |

## 5. 角色权限矩阵结果

已新增 `Permission` 与 `RolePermissionPolicy`，权限判断不再依赖单个硬编码字符串。

| 角色 | 已验证允许 |
|---|---|
| `ADMIN` | 全部已定义权限 |
| `STUDENT` | `USER_SELF_READ`、`COURSE_READ`、`COURSE_SELECT`、`LIBRARY_READ`、`LIBRARY_BORROW`、`STORE_READ`、`STORE_PURCHASE` |
| `TEACHER` | `USER_SELF_READ`、`STUDENT_READ`、`COURSE_READ` |
| `LIBRARIAN` | `LIBRARY_READ`、`LIBRARY_MANAGE` |
| `STORE_MANAGER` | `STORE_READ`、`STORE_MANAGE` |

服务接口 `authorize(String token, String permission)` 仍保持字符串协议兼容：未登录返回 `UNAUTHORIZED`，无效权限编码返回 `BAD_REQUEST`，已登录但无权返回 `FORBIDDEN`。

## 6. 服务器消息分发结果

已新增 `server/src/test/java/cn/vcampus/server/UserMessageHandlerTest.java`，并用临时验证入口 `ServerHandlerCheck` 本地确认以下场景通过：

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
java -cp '.tmp-classes-user-e1-socket-green\classes' cn.vcampus.server.ServerApplication 19191
```

客户端运行：

```powershell
java -cp '.tmp-classes-user-e1-socket-green\classes' cn.vcampus.client.ClientApplication 127.0.0.1 19191
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

## 8. 安全检查

- 密码仍通过 PBKDF2 哈希保存，内存账号对象不保存明文密码。
- 登录失败统一返回 `invalid credentials`，不暴露账号是否存在。
- 客户端演示不输出密码，也不输出完整 token。
- 自注销后会删除账号并销毁该账号全部会话，旧 token 不能继续授权。

## 9. 当前限制与下一步

- 本机 Maven 未安装，正式 JUnit 自动化测试需在 Maven 可用环境或 GitHub Actions 中补跑。
- 当前用户数据仍为内存存储，服务器重启后账号丢失。
- 管理员注销他人账号、审计记录、Access 持久化、Swing 登录/注册界面和可独立运行胖 JAR 不属于 E1，放入后续 E2-E4。

下一步 E2：抽取 `UserRepository`，接入 Access `tblUser` 和 `tblAuditLog`，实现用户持久化、审计记录和管理员注销他人账号。

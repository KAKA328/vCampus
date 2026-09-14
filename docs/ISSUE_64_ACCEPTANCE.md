# Issue #64 学籍档案验收说明

最新 main 的 `database/seed.sql` 已预置 4 个未绑定的在读学生档案，专门用于“建档 → 批量导入账号 → 登录 → 选课联动”验收：

| 学号 | 姓名 | 专业/年级 | 说明 |
|---|---|---|---|
| `20260006` | Demo Registration Student | 计算机科学与技术 / 2026 | 默认注册绑定闭环 |
| `20260007` | Demo Grade Student One | 计算机科学与技术 / 2026 | 成绩审核与教学班场景 |
| `20260008` | Demo Grade Student Two | 计算机科学与技术 / 2026 | 成绩审核与教学班场景 |
| `20260009` | Demo Grade Student Three | 计算机科学与技术 / 2026 | 成绩审核与教学班场景 |

准备独立数据库：

```powershell
.\database\rebuild.ps1 -DatabasePath database\issue-64-test.accdb
```

启动服务端和客户端：

```powershell
java -jar server/target/vCampusServer.jar --db database/issue-64-test.accdb --port 19116
java -jar client/target/vCampusClient.jar --host 127.0.0.1 --port 19116
```

登录 `demo_admin / Demo123`，进入“用户管理 → 批量导入”，选择 `test-data/默认验收库学生导入示例.csv`，即可导入四名学生。该文件只依赖默认 schema/seed，不需要执行专项 SQL。以下为第一行示例：

```csv
账号,姓名,初始密码,角色,档案编号
student_import_01,导入测试学生,Test123,STUDENT,20260006
```

导入成功后登录 `student_import_01 / Test123`：

1. 学籍页面直接显示本人档案，学生端不提供按学号查询入口。
2. 选课页面由服务端按 token 找到 `20260006`，再读取其专业“计算机科学与技术”、入学年份 2026 和“在读”状态。
3. 服务端据此加载 2026-2027-1 的首修培养方案和开放教学班；客户端不能通过修改学号、专业或重修资格绕过服务端校验。
4. 将档案状态改为“休学”后重新进入选课，服务端拒绝新选和退选；恢复为“在读”后再验证。

`20260007`、`20260008` 已预置人工智能教学班选课和成绩草稿，不能当作完全无选课的新生；`20260009` 可用于另一名在读学生的选课操作。四个登录账号初始密码均为 `Test123`。`20260006` 保留给 `--demo` 流程，若已被占用，该行会失败，其余未绑定行仍可导入；建议在新建独立库中验收四行全成功。重复导入应返回逐行失败且不创建重复账号。

原 `test-data/学籍账号批量导入示例.csv` 保留三行学生/教师专项格式校验数据，仍需先加载 `database/student-test-data.sql`，不要与此默认库样例混用。

本说明对应 GitHub Issue #64，数据和导入文件只用于本地验收，不覆盖团队共享数据库。

## 验证结果

2026-09-14，`StudentTestDataImportIntegrationTest` 共 2 项通过，0 失败、0 错误：保留原三账号专项测试，新增默认 schema/seed 的四账号导入与学籍、选课联动测试。使用真实临时 Access 数据库和生产 CSV 解析器、账号绑定与选课服务，验证四账号登录和课程加载、20260006 实际选课、休学后禁止新选/退选，以及重复导入全部失败。测试从登录会话取得账号，再交给选课档案服务读取，不是完整 Socket/UI 点击验收；尚未人工执行本文界面步骤。

命令：`mvn -pl client -am "-Dtest=StudentTestDataImportIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true" test`。本次只增补数据样例、文档和测试，未修改选课业务规则；未提交、推送或关闭远端 Issue。

# 学籍功能、边界与并发验收

最新验证（2026-09-14）：PR71 合并后，已从 main `375bae4` 建立 `fix/student-network-acceptance` 并恢复本轮修复；重新全量构建 886 项测试全部通过，0 失败、0 错误、0 跳过。命令沿用本文 JVM 参数。以下 885 项为同步前阶段记录，当前验收以 886 项为准。代码尚未提交推送。

## 测试范围

以“管理员导入账号并绑定档案 → 师生查询本人 → 修改联系方式/档案 → 查询学分 → 教务审查 → 毕业 → 重启复查”为主线，测试学生、教师、系统管理员和教务管理员权限。

自动联机自检启动独立 Java 服务端进程，使用真实 TCP 连接和临时 Access 数据库。客户端通过 `127.0.0.1` 访问服务端，不直接调用 Handler 代替网络。每次重复测试独立建库，结束后停止自己启动的服务进程；不修改当前手工演示库。

这属于本机多客户端联机测试，不能替代跨电脑、Wi-Fi 丢包、局域网防火墙或公网测试。未进行超出实训规模的压力测试。

## 提前准备数据

新增独立 SQL：`database/student-acceptance-data.sql`，在默认 schema/seed 后执行一次，增加 11 名学生、2 名教师和 12 条正式课程结果。学生统一属于已有“计算机科学与技术、2026 级”培养方案；不会出现因专业年级不匹配而无法验收首修课程的问题。

正常账号文件为 `test-data/学籍完整验收账号.csv`，12 个账号初始密码均为 `Test123`。先导入正常文件，再导入 `test-data/学籍验收错误账号.csv` 验证四行失败；错误文件不能代替正常数据。

| 学号/工号 | 登录账号 | 成绩/学分场景 | 预期 |
|---|---|---|---|
| QA_EMPTY | qa_empty | 无成绩、联系方式为空 | 成绩历史为空，0 学分，不能按 6 学分门槛毕业 |
| QA_EXACT | qa_exact | 60 分及 100 分各一门，合计 6 学分 | 按测试要求 6 学分恰好达标；保存审查后修改档案，旧审查失效 |
| QA_SHORT | qa_short | 一门通过，3 学分 | 要求 6 时差 3；要求 4 时差 1 |
| QA_PENDING | qa_pending | 两门通过共 6 学分，一门真实 0 分未过 | 总分够但仍有 1 门待重修，不可毕业 |
| QA_RETAKE | qa_retake | 同课程 59 分失败、重修 60 分通过 | 3 学分，0 门待重修，1 门历史重修 |
| QA_DUP | qa_dup | 同课程两次通过，各 3 学分 | 学分只计 3，不重复累计 |
| QA_LEAVE | qa_leave | 休学 | 本人可查档案，不可新建毕业审查；选退课限制结合 Issue #64 测试 |
| QA_WITHDRAWN | qa_withdrawn | 退学 | 本人可查档案，不可新建毕业审查 |
| QA_EDIT | qa_edit | 在读、初始合法联系方式 | 两名管理员或学生多个客户端同时保存，只有一个旧快照成功 |
| QA_GRAD | qa_grad | 在读、6 学分、无待重修 | 两个管理员同时确认毕业，只成功一次并保留办理记录 |
| QA_BIND | 不预导入 | 未绑定在读档案 | qa_bind_a/qa_bind_b 同时抢占，仅一个成功，失败账号不可登录 |
| QA_T_ACTIVE | qa_teacher | 在职 | 本人教师资料只读，不能看学生档案 |
| QA_T_INACTIVE | qa_inactive_teacher | 非在职但账号有效 | 可查本人在职状态，不能看学生档案 |

6 学分和 4 学分是验收门槛，不代表真实毕业学分。QA_GRAD 不直接预填“毕业”或伪造审查指纹，应通过实际流程生成审查与毕业记录；之后用它测试已毕业状态。

## 手工执行顺序

在仓库根目录（JDK、Maven 可用）执行：

```powershell
.\database\rebuild.ps1 -DatabasePath database\student-network-test.accdb `
    -AdditionalScript database\student-acceptance-data.sql
java -jar server/target/vCampusServer.jar --db database/student-network-test.accdb --port 19118
```

另开客户端：

```powershell
java -jar client/target/vCampusClient.jar --host 127.0.0.1 --port 19118
```

1. 使用 demo_admin / Demo123 导入正常账号 CSV，应成功 12 行；再导入错误 CSV，应失败 4 行，不能新增残留账号。
2. 使用相应师生账号核对上表，并测试无法读取他人档案。教务使用 demo_academic_admin / Demo123。
3. 并发编辑使用两个独立客户端，分别登录 demo_admin 和 demo_academic_admin。两边先加载 QA_EDIT；A 改电话保存成功后，B 提交原页面，必须冲突且保留 B 输入。B 重新加载后才可保存。
4. 对 QA_GRAD 先保存一次审查，两边都加载该最新记录，勾选人工核查条件后同时确认毕业。只允许一方成功；重启后仍只有一次有效毕业办理记录。
5. 并发绑定使用两个客户端中同一个 demo_admin 会话测试时，应注意项目单账号会话策略；自动测试复用一个合法管理员 token，从独立 TCP 连接同时提交 qa_bind_a/qa_bind_b，避免第二次登录让第一次会话失效。手工测试可预先创建第二个系统管理员账号，再让两名管理员各提交一行绑定 QA_BIND 的导入请求。
6. 首修/选退课、成绩提交与退回继续使用默认库 `20260006—20260009`、demo_teacher 和默认成绩批次，具体见 `ISSUE_64_ACCEPTANCE.md`；不要把教师禁止查看学籍档案误解为禁止在选课模块录入本人教学班成绩。

再次从零验收时使用另一个数据库路径。重建同一路径会备份后替换，原来手工修改和已导入账号不会自动带入新库。QA_BIND 的竞争账号不要混入正常 CSV。

## 测试矩阵

| 分类 | 用例 | 检查结果 |
|---|---|---|
| 导入与绑定 | 12 行正常导入、重复导入 | 登录账号和学号/工号一致；重复导入 0 成功 |
| 错误导入 | 不存在档案、已绑定档案、短密码、非法角色 | 逐行失败，未占用 QA_BIND |
| 权限 | 学生查看他人、教师查看学生、无效 token、登出 token | 返回拒绝，不泄漏完整档案 |
| 联系方式 | 3 位、12 位、含字母、全角手机号；错误邮箱 | BAD_REQUEST，原数据库记录保持不变 |
| 数值边界 | 0、59、60、100 分；空成绩、重复通过、重修通过 | 学分、待重修统计符合上表 |
| 学分审查 | 要求学分大于所得、恰好相等、总分够但有待重修 | 只有满足测试门槛且无待重修者达标 |
| 审查失效 | 审查后修改联系方式，再用旧记录毕业 | CONFLICT，仍为在读 |
| 并发管理员保存 | 5 轮，每轮 4 个连接同时提交相同旧快照、不同电话 | 每轮 1 OK + 3 CONFLICT，最终档案等于赢家 |
| 并发学生保存 | 同一学生 token、两个连接、不同电话 | 1 OK + 1 CONFLICT |
| 并发毕业 | 两名管理员、同一最新审查记录 | 1 OK + 1 CONFLICT，毕业记录唯一 |
| 毕业后旧表单 | 用毕业前档案快照再次保存 | CONFLICT，不能改回在读 |
| 并发绑定 | 两个新账号抢同一未绑定档案 | 1 成功 + 1 失败，仅赢家账号存在且绑定正确 |
| 断开重连/重启 | 每请求建立独立连接；停止服务端并重新启动 | 保存的档案、绑定、审查和毕业记录保留；旧 token 不再有效 |

并发测试共重复三次，每次独立建库和启动服务，使用 CountDownLatch 对齐提交起点。上述断开重连不等于“提交中断网”测试，不能据此断言未知结果请求可以安全自动重试。

## 自动复现命令

```powershell
mvn -pl client -am "-Dtest=StudentNetworkAcceptanceTest" `
    "-Dsurefire.failIfNoSpecifiedTests=false" `
    "-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true" test
```

测试在 `client/src/test/java/cn/vcampus/client/view/StudentNetworkAcceptanceTest.java`。失败日志、诊断信息保存在 `client/target/network-acceptance`（如诊断工具可用），测试断言报告位于 `client/target/surefire-reports`。本机使用 JDK 17 运行。

## 自检发现及修复

实际并发绑定测试发现两个独立账号可能同时报告导入成功，后一个绑定覆盖前一个。根因之一是 AccessProfileBindingRepository 在预检后无条件按档案编号 UPDATE；现改为仅更新未绑定或已经绑定同账号的记录，保留相同绑定的幂等语义。

进一步检查曾发现失败方账号残留并能登录。现将单服务端用户服务中的校验、建账号、绑定和补偿置于同一同步边界，防止不同请求交错完成这一流程；并通过失败方不可登录、仅赢家账号存在及重启后状态验证。本次涉及共享的用户管理创建账号方法和 Access 档案绑定适配器，属于必要的对接修复，需用户管理负责人复核。未修改首页或其他模块页面。

该同步边界是单 UserManagementService 实例；不是跨服务器数据库事务，不能宣称多个服务端进程同时写同一 Access 库也具有相同保证。SQL 条件更新仍是档案占用的最后一道保护。

重复运行中出现过并发保存读取超时。随后完整回归再次复现，使用服务端线程转储确认了驱动死锁，不能将它归结为普通网络延迟：

```text
Thread A: UcanaccessDriver.connect 持有 UcanaccessDriver.class，
          等待 DBReference$MemoryTimer。
Thread B: UcanaccessConnection.close 持有 DBReference$MemoryTimer，
          在 DBReference.shutdown -> DBReferenceSingleton.remove
          等待 UcanaccessDriver.class。
Found 1 deadlock.
```

现由 `AccessDatabaseLease` 在 ServerApplication.main 进入服务循环前持有数据库连接，使每次请求关闭自己的连接时不再触发最后连接的镜像关闭；保活连接不执行业务写入，各业务事务仍独立提交/回滚。内存模式不打开 Access 连接，正常退出主循环或 JVM 正常终止时释放保活连接。该调整涉及共享服务端启动流程，是本次并发自检所发现问题的必要修复。

保活后重启检查又暴露毕业记录写回问题：同进程查询显示已毕业，但重开数据库后 `graduated_by/at` 为空。AccessAcademicAdminStore 原先向 JDBC 传入含亚毫秒的 Instant，而 Access 实际存储精度较低；与保持镜像的后续行匹配不一致。统一审查与毕业时间写入为毫秒后，同一复现连续三次通过。该修复只调整学籍审查库时间写入，不伪造结果或跳过重启检查。

测试重启现在由测试专用子进程入口接收标准输入停止信号，触发 JVM 正常退出清理；网络业务仍由真实 ServerApplication.main 执行，未增加生产远程关机接口。Windows 强制终止进程不属于正常重启，不能用正常重启结果证明强杀或断电安全。

前几次联机运行的失败是实际发现，不计为通过；最终验证结果见下方。此测试不保证所有 UCanAccess 使用方式都不会死锁，直接绕过 ServerApplication.main 自行组装多线程服务时需管理相同的数据库生命周期。

## 仍需人工或后续专项验证

- 两台电脑的真实局域网测试、网络断开/恢复、请求超时后核对最终状态；连接地址应为服务端局域网 IP，端口与服务端一致。当前未修改防火墙。
- UI 弹窗缩放、刷新保留选择、关闭后迟到响应，以及多人真实点击的截图证据。
- 同时审核成绩和办理毕业、毕业后正式成绩退回的业务纠正策略。
- 同时提交同一账号名、多个服务端实例同时运行、长时间压力测试及 Access 文件外部锁占用。
- 专业目录缺表/空目录/停用已由目录专项测试覆盖，不在正常验收 SQL 中故意破坏目录；错误历史 NULL 成绩也应另开异常库，不混入可正常使用的验收库。

## 最终结果（2026-09-14）

完整命令：

```powershell
mvn clean test package "-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true"
```

BUILD SUCCESS，885 项测试、0 失败、0 错误、0 跳过；耗时 6 分 32 秒，完成时间为北京时间 2026-09-14 16:31:29。`git diff --check` 通过。

新增 StudentNetworkAcceptanceTest 在最终全量构建中重复 3 次，全部通过，耗时 48.28 秒。三轮共执行：36 个正常账号导入、12 行错误导入、36 行重复导入拒绝、60 个管理员并发保存请求（15 个四连接组）、6 个学生并发保存请求、6 个毕业竞争请求、6 个绑定竞争请求；每轮重启服务端后核对档案、绑定、毕业记录与 token 状态。最终重复测试中没有再次发现双绑定、旧值覆盖或驱动死锁。

最终构建日志保存在本机 `D:\专业技能实训\student-stale-form-notes-20260914\network-final-regression.log`。失败复现过程和修复原因在上文保留；“最终通过”不表示首次运行就通过，更不代表跨电脑网络和强杀恢复已验收。

本次代码与资料仍为本地未提交改动，没有推送到 PR #71，也没有重启或升级用户已打开的手工测试服务端。

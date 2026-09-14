# 并发自检对接记录

本轮由学籍完整流程真实 TCP 自检触发，涉及共享服务端和用户管理边界。修改未自动推送到 PR #71。

2026-09-14 协作同步：PR #71 已合并，因此本轮后续工作已从最新 `origin/main`（`375bae4`）创建独立分支 `fix/student-network-acceptance`，不再向已合并 PR 追加提交。主线新增的 AccessUserRepository 管理员停用跨进程锁完整保留。

同步后重新执行全量构建：`mvn clean test package "-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true"`，886 项测试全部通过（0 失败、0 错误、0 跳过），包含三轮独立服务端进程/TCP/Access 并发与重启验收。完成时间：2026-09-14 16:53:05，diff --check 通过。此次仅同步、适配、验证，未提交推送；下面的 885 项是同步前记录。

| 范围 | 实际发现 | 修改 | 责任/限制 |
|---|---|---|---|
| 学籍与账号绑定 | 两个账号抢同一未绑定档案，可能都成功并覆盖；失败方可能留下可登录账号 | AccessProfileBindingRepository 增加条件 UPDATE；DefaultUserManagementService 创建/绑定/补偿方法同步 | 用户管理负责人应复核；同步保护限单服务实例；数据库条件保护仍保留 |
| 共享 Access 生命周期 | 并发打开/关闭最后连接导致 UCanAccess Driver.class 与 MemoryTimer 锁反序，jcmd 确认死锁 | ServerApplication.main 持有 AccessDatabaseLease，正常终止释放 | 单服务端运行模式；其他入口自行组装并发服务也应管理数据库生命周期 |
| 学籍毕业记录 | 保活期间完成毕业，重开库后操作人/时间为空 | 审查及毕业 Timestamp 写入统一到毫秒 | 仅修改学籍库写入；多次重启核对通过后才计为修复 |

学生/教师信息权限、培养方案、成绩发布规则未扩展。本轮不自动改动正在运行的演示库或服务进程；新的修复需重新打包并配套启动才能生效。

测试与数据说明见 `STUDENT_NETWORK_ACCEPTANCE.md`。正常测试库包含 11 个学生档案、2 个教师档案和 12 条成绩；12 个正常导入账号与4行错误导入样例分开。QA_GRAD 必须通过审查/毕业流程生成记录，QA_BIND 留给并发绑定，不预置竞争账号。

2026-09-14 最终全仓构建 885 项测试全部通过；真实 TCP 子进程测试三次独立建库与重启全部通过，diff --check 通过。此结果仅适用于本机单服务端、多连接和正常重启，不代表多服务端或强制终止场景。

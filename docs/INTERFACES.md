# 模块接口基线

接口位于各业务模块的 `api` 等价包（当前使用模块根包，后续可按团队约定细分）。接口只描述业务能力，不直接依赖 Swing、Socket 或 Access。

| 模块 | 核心接口 | 初始操作 |
|---|---|---|
| 用户管理 | `UserManagementService` | `register`、`importUsers`、`unregister`、`login`、`currentSession`、`logout`、`authorize`；`register` 作为管理员端开户注册能力，批量导入使用 `USER_IMPORT`，载荷见 `UserCredentials`、`UserImportCommand`、`UserCommand`、`AuthorizationRequest` |
| 学生学籍 | `StudentManagementService` | `findById`、`findByUserId`、`findMyStudentProfile`、`findByClass`、`findByMajor`、`save` |
| 学业审查 | `AcademicReviewService` | `historyFor`、`pendingRetakes`、`review`、`latestReview` |
| 选课 | `CourseSelectionService` | 完整选课流程使用 V2 消息：查询轮次/教学班/已选记录、按教学班选课、按选课记录退选；课程维护消息见下文 |
| 图书馆 | `LibraryService` | `search`/分类筛选、`getBook`、原子 `borrowBatch`、按记录 `returnBook`、本人/全量 `borrowHistory`、`addBook` |
| 商店 | `StoreService` | 商品查询/分类、购买、购物车、钱包、本人/全量订单、热销排行和商品维护；商店消息使用 token-only 命令，用户编号由服务器会话解析 |

所有服务方法返回 `ServiceResult<T>`，由服务器统一映射为 `Message` 响应。服务端必须再次校验会话和权限。

## 学籍与选课对接接口

学籍模块向选课模块提供以下服务能力：

```java
StudentManagementService.findByUserId(String userId)
AcademicReviewService.pendingRetakes(String studentId)
```

`findByUserId` 用于把登录账号映射为学生档案。账号已绑定时返回 `OK + StudentRecord`；未绑定时返回 `NOT_FOUND`，选课模块应提示联系学籍管理员；参数为空返回 `BAD_REQUEST`。

`pendingRetakes` 按 `studentId + courseId` 汇总课程历史：某课程只要有一次 `passed=true` 就不再重修；只有全部尝试均未通过时才返回该课程最新一次失败记录。没有待重修课程返回 `OK + empty list`，参数为空返回 `BAD_REQUEST`。课程历史来源为 `tblCourseResult` 与 `tblCourse`，写入责任仍归选课/教务模块，学籍模块只提供查询和判断。

选课服务应先通过当前会话的 `userId` 调用 `findByUserId`，再使用返回的 `studentId` 进行选课；不得信任客户端自行传入的账号或学号。只有学籍状态为“在读”的学生允许新增选课，休学、毕业和退学学生保留历史但返回 `FORBIDDEN`。

服务器使用 `StudentSelectionProfileAdapter` 完成上述组合：它将 `StudentRecord` 和 `pendingRetakes` 转换为选课V2所需的 `StudentSelectionProfile`。内存启动使用 `InMemoryStudentRepository + InMemoryAcademicReviewService`，`--db` 启动使用 `AccessStudentRepository + AccessAcademicReviewService`；选课模块不直接依赖学籍模块实现。

教务端学籍查询还支持 `findByMajor(majorName)`，仅允许 `ADMIN` 和 `ACADEMIC_ADMIN` 角色调用；学生本人和教师不能按专业批量查询。`findMyStudentProfile(userId)` 是服务器完成 Token 解析后的兼容别名，等价于 `findByUserId(userId)`。

`AcademicReviewService.review(studentId, requiredCredits)` 根据课程结果实时计算学分、挂科和重修统计；`latestReview(studentId)` 读取 `tblAcademicReview` 中按审核时间倒序的最新快照。实时计算不会覆盖历史快照。

## 账号与档案绑定公共契约

账号、学生档案和教师档案的身份字段必须分开使用：`user_id` 是登录身份，`student_id` 是学生学号，`teacher_id` 是教师工号。推荐流程为：先由学籍/教师信息模块建立学生或教师档案，再由管理员创建或批量导入账号，最后通过 `student_id` / `teacher_id` 把档案绑定到 `user_id`。

学生、教师本人操作时，客户端只携带 `token` 和具体业务参数，服务器根据 `token -> user_id` 查出当前账号，再通过 `tblStudent.user_id` 或 `tblTeacher.user_id` 转换为业务档案编号。选课、成绩录入、学籍查询等模块不得直接信任客户端传入的 `studentId`、`teacherId` 或 `userId`。商店订单和图书借阅继续以 `user_id` 作为当前用户身份。

图书馆完整流程使用显式 V2 协议：`LIBRARY_QUERY_V2`、`LIBRARY_DETAIL_V2`、`LIBRARY_BORROW_V2`、`LIBRARY_RETURN_V2`、`LIBRARY_HISTORY_V2` 和 `LIBRARY_ADD_BOOK_V2`。借阅、归还和本人记录中的用户身份仅由服务器根据命令内的 token 解析；查询其他用户或全部借阅记录、增加馆藏需要 `LIBRARY_MANAGE` 权限。

详细对接规范见 [`ACCOUNT_PROFILE_INTEGRATION.md`](ACCOUNT_PROFILE_INTEGRATION.md)。

## 公共消息类型变更规则

`MessageType` 是客户端和服务器共同依赖的公共契约。任何子系统新增能力时，如果需要新增消息类型，不能只修改 `common` 里的枚举，还必须同步补齐：

1. 请求命令对象或 payload 字段说明；
2. 响应数据类型说明；
3. 服务端 Handler 处理逻辑；
4. `ServerApplication` 分发入口；
5. 客户端远程服务或 Swing 页面调用；
6. 权限与数据范围校验；
7. 正常、异常、权限拒绝测试；
8. 本文档中的接口说明。

完整选课流程统一使用以下 Socket 协议：

- `COURSE_SELECTION_QUERY_V2` + `CourseSelectionQueryV2Command(token, roundId?)`：查询可用选课轮次、指定轮次的教学班、本人已选教学班；
- `COURSE_SELECT_OFFERING_V2` + `CourseSelectOfferingV2Command(token, roundId, offeringId)`：在指定轮次选择具体教学班；
- `COURSE_DROP_RECORD_V2` + `CourseDropRecordV2Command(token, recordId)`：按选课记录编号退选。

客户端不提交 `studentId` 作为本人身份，服务端必须根据 `token -> user_id -> student_id` 推导学生档案；退选使用已选记录的 `recordId`。

教务人员维护课程目录、教学班和选课轮次统一使用 `COURSE_MANAGE` 与
`CourseManagementCommand`，服务端要求 `COURSE_MANAGE` 权限。选课轮次相关操作为：

- `LIST_SELECTION_ROUNDS_BY_TERM`：查询某学期全部轮次；
- `CREATE_SELECTION_ROUND`：创建首修或重修轮次；同一学期每种类型最多一个；
- `UPDATE_SELECTION_ROUND_TIME_WINDOW`：仅修改轮次起止时间，不改变学期和轮次类型；
- `CHANGE_SELECTION_ROUND_STATUS`：在草稿、开放、关闭状态之间切换。

轮次操作不新增 `MessageType`，仍由现有 `COURSE_MANAGE` 分发；响应 payload 为
`SelectionRound` 或其列表。数据库表为 `tblSelectionRound`，项目直接按最新版
`schema.sql` 创建数据库，不保留旧表迁移要求。

商店当前使用以下 token-only 命令，服务端必须从 token 对应会话取得 `userId`，不得相信客户端传入的学生/用户编号：

- `STORE_QUERY` + `StoreQueryCommand(token, category?)`：查询在售商品，可按类别过滤；要求 `STORE_READ`。
- `STORE_PURCHASE` + `StorePurchaseCommand(token, productId, quantity)`：直接购买；要求 `STORE_PURCHASE`。
- `STORE_ORDER_QUERY` + `StoreOrderQueryCommand(token)`：查询本人订单；要求 `STORE_READ`。
- `STORE_CART_ADD` / `STORE_CART_REMOVE` / `STORE_CART_QUERY` / `STORE_CART_CHECKOUT`：购物车增删查和结账，分别使用对应 `Cart*Command`；增删/结账要求 `STORE_PURCHASE`，查询要求 `STORE_READ`。
- `STORE_RESTOCK`、`STORE_PRODUCT_ADD`、`STORE_PRODUCT_UPDATE`、`STORE_PRODUCT_DEACTIVATE`：商品和库存维护，使用对应 `Store*Command`；均要求 `STORE_MANAGE`。
- `STORE_ORDER_LIST_ALL` + `StoreOrderListAllCommand(token)`：管理员全量订单；要求 `STORE_MANAGE`。
- `STORE_HOT_PRODUCTS` + `StoreHotProductsCommand(token, limit)`：热销商品排行；要求 `STORE_READ`。
- `STORE_ACCOUNT_QUERY` + `StoreAccountQueryCommand(token)`：查询本人钱包余额，响应 payload 为 `long`（单位「分」）；要求 `STORE_READ`，`userId` 取自 token，无账户时返回 0。
- `STORE_ACCOUNT_RECHARGE` + `StoreAccountRechargeCommand(token, amountCents)`：本人充值，`amountCents` 必须为正（单位「分」）；要求 `STORE_PURCHASE`，`userId` 取自 token，账户不存在时懒创建。
- `STORE_ACCOUNT_ADJUST` + `StoreAccountAdjustCommand(token, targetUserId, newBalanceCents)`：管理员把指定用户余额校正为绝对值 `newBalanceCents`（非负，单位「分」）；要求 `STORE_MANAGE` 且角色 ∈ {`ADMIN`, `STORE_MANAGER`}（双重门槛）；`targetUserId` 取自 payload，仅管理员可指定他人，普通用户身份一律取自 token。

商店钱包余额一律以「分」为单位的 `long` 存储和传输（实体 `BankAccount.balanceCents`、数据库列 `balance_cents BIGINT`、服务/命令/仓储接口全传 `long` 分），禁止 `double` 余额；`Product.price`、`Order.totalPrice`/`unitPrice` 仍是 `double`，只在支付边界 `Math.round(totalPrice * 100)` 换算一次，误差不进余额账本。购买/结账时余额不足返回 `PAYMENT_REQUIRED`；库存或余额在并发下变化、补偿失败时返回 `CONFLICT`。钱包只有余额校正，暂无账户流水/审计表。扣库存和扣款走应用层补偿（每个补偿都检查返回值），这是单 JVM 下的补偿一致性，不是数据库事务。

用户批量导入使用 `USER_IMPORT`。请求 payload 为 `UserImportCommand(token, rows)`，其中 `rows` 是 `UserImportRow(userId, password, displayName, roleCode)` 列表；响应 payload 为 `UserImportResult(importBatchId, totalCount, successCount, failures)`，失败明细为 `UserImportFailure(rowNumber, userId, message)`。客户端用户管理页可从 `.xlsx`、`.csv`、`.tsv` 外部表格读取账号清单并转为 `rows`；这些表格只是导入源文件，不替代 Access 运行数据库。该能力要求 `USER_MANAGE`，服务端会记录导入管理员、导入时间、导入批次，并为每个成功创建的账号写入 `IMPORT_USER` 审计记录。单行失败不会影响同批次其它有效账号。

商店订单查询需要同时明确：

- 学生、教师查询本人订单；
- 当前 `STORE_ORDER_QUERY` 统一只查询 token 对应用户的本人订单；若后续开放商店管理员全量订单查询，应新增独立的管理查询命令和数据范围说明；
- 其他无关角色请求时服务器返回 `FORBIDDEN`；
- 请求中不携带用户编号，服务端始终以 token 对应身份为准。
- 管理员命令必须在调用 `StoreService` 前完成权限校验，拒绝时返回 `FORBIDDEN`，不能依赖客户端隐藏按钮。

## 权限与课程维护公共契约

- 角色新增 `ACADEMIC_ADMIN`；完整角色权限和数据范围以 [`PERMISSIONS.md`](PERMISSIONS.md) 为准。
- 系统不提供面向未登录用户的公开自助注册。`REGISTER` 表示管理员端开户注册能力，必须由已登录管理员发起或由初始化脚本预置测试账号；各业务操作仍必须在服务器端按 token、角色权限和数据范围再次校验。
- 创建学生账号后应通过 `student_id` 绑定已有或新建的 `tblStudent` 学籍档案；创建教师账号后应通过 `teacher_id` 绑定已有或新建的 `tblTeacher` 教师档案。历史成绩、学业审查记录和教师授课关系不由账号创建自动生成，而由学籍/教务模块维护或由演示数据导入。
- `LOGIN` 成功后返回 `Session`；同一账号已有活动会话时再次登录返回 `CONFLICT`，登出或注销后可重新登录。
- 首个系统管理员由服务器读取 `VCAMPUS_BOOTSTRAP_ADMIN_ID`、`VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD`、`VCAMPUS_BOOTSTRAP_ADMIN_NAME` 后在进程内初始化，不通过 Socket 暴露管理员注册接口。
- 权限新增 `COURSE_MANAGE`、`GRADE_WRITE`、`ACADEMIC_REVIEW`。
- 学生完整选课使用 `COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`，查询要求 `COURSE_READ`，选课和退选要求 `COURSE_SELECT`。
- 课程维护使用 `COURSE_MANAGE`，要求 `COURSE_MANAGE` 权限；payload 固定为 `CourseManagementCommand`。当前支持：
  - `LIST_COURSES`：查询全部课程目录，响应 `List<Course>`；
  - `LIST_OFFERINGS_BY_TERM(term)`：按学期查询教学班，响应 `List<CourseOffering>`；
  - `CREATE_COURSE(course)`：新增课程目录，响应 `Course`；
  - `UPDATE_COURSE_DETAILS(courseId, name, credits)`：修改课程名称和学分，响应 `Course`；
  - `CHANGE_COURSE_STATUS(courseId, courseStatus)`：启用或停用课程，响应 `Course`；
  - `CREATE_OFFERING(offering)`：新增教学班，响应 `CourseOffering`；
  - `CHANGE_OFFERING_STATUS(offeringId, offeringStatus)`：开放或关闭教学班，响应 `CourseOffering`；
  - `CHANGE_OFFERING_CAPACITIES(offeringId, requiredCapacity, electiveCapacity, crossMajorCapacity)`：修改三类容量，响应 `CourseOffering`；
  - `UPDATE_OFFERING_TEACHING_INFO(offeringId, teacherId, location)`：仅修改任课教师和上课地点，响应 `CourseOffering`，不得修改既有 `schedule` 文本或 `meetingSchedule` 结构化上课时间。
- 停开课程或教学班时，存在选课或历史记录不得直接物理删除关联数据。
- 选课轮次同样通过 `COURSE_MANAGE` + `CourseManagementCommand` 维护，支持查询某学期轮次、创建首修/重修轮次、修改轮次时间窗口和切换轮次状态；同一学期每种轮次类型最多一个。
- 客户端只负责按角色隐藏无权入口，服务器 Handler 必须在调用业务接口前执行 `authorize`，拒绝时返回 `FORBIDDEN`。

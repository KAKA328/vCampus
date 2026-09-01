# 模块接口基线

接口位于各业务模块的 `api` 等价包（当前使用模块根包，后续可按团队约定细分）。接口只描述业务能力，不直接依赖 Swing、Socket 或 Access。

| 模块 | 核心接口 | 初始操作 |
|---|---|---|
| 用户管理 | `UserManagementService` | `register`、`importUsers`、`unregister`、`login`、`currentSession`、`logout`、`authorize`；`register` 作为管理员端开户注册能力，批量导入使用 `USER_IMPORT`，载荷见 `UserCredentials`、`UserImportCommand`、`UserCommand`、`AuthorizationRequest` |
| 学生学籍 | `StudentManagementService` | `findByUserId`、`findById`、`findByClass`、`save`；Socket 命令见 `StudentQueryCommand`、`StudentUpdateCommand` |
| 选课 | `CourseSelectionService` | 完整选课流程使用 V2 消息：查询轮次/教学班/已选记录、按教学班选课、按选课记录退选；课程维护消息见下文 |
| 图书馆 | `LibraryService` | `search`、`borrow`、`returnBook` |
| 商店 | `StoreService` | 商品查询/分类、购买、购物车、本人/全量订单、热销排行和商品维护；商店消息使用 token-only 命令，用户编号由服务器会话解析 |

所有服务方法返回 `ServiceResult<T>`，由服务器统一映射为 `Message` 响应。服务端必须再次校验会话和权限。

## 账号与档案绑定公共契约

账号、学生档案和教师档案的身份字段必须分开使用：`user_id` 是登录身份，`student_id` 是学生学号，`teacher_id` 是教师工号。推荐流程为：先由学籍/教师信息模块建立学生或教师档案，再由管理员创建或批量导入账号，最后通过 `student_id` / `teacher_id` 把档案绑定到 `user_id`。

学生、教师本人操作时，客户端只携带 `token` 和具体业务参数，服务器根据 `token -> user_id` 查出当前账号，再通过 `tblStudent.user_id` 或 `tblTeacher.user_id` 转换为业务档案编号。选课、成绩录入、学籍查询等模块不得直接信任客户端传入的 `studentId`、`teacherId` 或 `userId`。商店订单和图书借阅继续以 `user_id` 作为当前用户身份。

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

完整选课流程已经升级为显式 V2 Socket 协议。早期 `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` 只表示“课程级简化选课”遗留入口，不再承载带轮次和教学班的完整流程；当前服务端收到旧选课消息会返回清晰的升级提示。新客户端必须使用：

- `COURSE_SELECTION_QUERY_V2` + `CourseSelectionQueryV2Command(token, roundId?)`：查询可用选课轮次、指定轮次的教学班、本人已选教学班；
- `COURSE_SELECT_OFFERING_V2` + `CourseSelectOfferingV2Command(token, roundId, offeringId)`：在指定轮次选择具体教学班；
- `COURSE_DROP_RECORD_V2` + `CourseDropRecordV2Command(token, recordId)`：按选课记录编号退选。

这次变更是公共协议升级。客户端不再提交 `studentId` 作为本人身份，服务端必须根据 `token -> user_id -> student_id` 推导学生档案；退选也不再使用 `courseId`，而是使用已选记录的 `recordId`。如果后续需要兼容旧客户端，应由组内另行实现旧消息的简化流程，不能再把旧消息类型偷偷改成 V2 字段。

商店当前使用以下 token-only 命令，服务端必须从 token 对应会话取得 `userId`，不得相信客户端传入的学生/用户编号：

- `STORE_QUERY` + `StoreQueryCommand(token, category?)`：查询在售商品，可按类别过滤；要求 `STORE_READ`。
- `STORE_PURCHASE` + `StorePurchaseCommand(token, productId, quantity)`：直接购买；要求 `STORE_PURCHASE`。
- `STORE_ORDER_QUERY` + `StoreOrderQueryCommand(token)`：查询本人订单；要求 `STORE_READ`。
- `STORE_CART_ADD` / `STORE_CART_REMOVE` / `STORE_CART_QUERY` / `STORE_CART_CHECKOUT`：购物车增删查和结账，分别使用对应 `Cart*Command`；增删/结账要求 `STORE_PURCHASE`，查询要求 `STORE_READ`。
- `STORE_RESTOCK`、`STORE_PRODUCT_ADD`、`STORE_PRODUCT_UPDATE`、`STORE_PRODUCT_DEACTIVATE`：商品和库存维护，使用对应 `Store*Command`；均要求 `STORE_MANAGE`。
- `STORE_ORDER_LIST_ALL` + `StoreOrderListAllCommand(token)`：管理员全量订单；要求 `STORE_MANAGE`。
- `STORE_HOT_PRODUCTS` + `StoreHotProductsCommand(token, limit)`：热销商品排行；要求 `STORE_READ`。

学籍当前使用以下 token-first 命令，服务端必须先解析会话和权限，再决定查询范围：

- `STUDENT_QUERY` + `StudentQueryCommand.self(token)`：学生本人查询，只返回 token 绑定的 `tblStudent.user_id` 对应档案；要求 `STUDENT_READ`。
- `STUDENT_QUERY` + `StudentQueryCommand.byId(token, studentId)`：按学号查询；要求 `STUDENT_READ`，学生角色不会使用客户端传入的 `studentId`，教务管理员等有权限角色可用。
- `STUDENT_QUERY` + `StudentQueryCommand.byClass(token, classId)`：按班级查询；要求 `STUDENT_READ`，用于教务/教师侧查询。
- `STUDENT_UPDATE` + `StudentUpdateCommand(token, record)`：保存学生档案；要求 `STUDENT_WRITE`，当前 Access 实现写入 `tblStudent` 并以 `record.studentId` 作为默认 `user_id` 绑定值，后续如增加独立绑定页面应改为显式绑定命令。

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
- 课程维护使用 `COURSE_MANAGE`，要求 `COURSE_MANAGE` 权限；历史 `COURSE_CREATE`、`COURSE_UPDATE`、`COURSE_DEACTIVATE` 仅作为早期枚举保留。
- `COURSE_DEACTIVATE` 表示停开；存在选课或历史记录时不得直接删除关联数据。
- 客户端只负责按角色隐藏无权入口，服务器 Handler 必须在调用业务接口前执行 `authorize`，拒绝时返回 `FORBIDDEN`。

# 模块接口基线

接口位于各业务模块的 `api` 等价包（当前使用模块根包，后续可按团队约定细分）。接口只描述业务能力，不直接依赖 Swing、Socket 或 Access。

| 模块 | 核心接口 | 初始操作 |
|---|---|---|
| 用户管理 | `UserManagementService` | `register`、`unregister`、`login`、`currentSession`、`logout`、`authorize`；载荷见 `UserCredentials`、`UserCommand`、`AuthorizationRequest` |
| 学生学籍 | `StudentManagementService` | `findById`、`findByClass`、`save`；学业审查见 `InMemoryAcademicReviewService` |
| 选课 | `CourseSelectionService` | `listCourses`、`select`、`drop`、`selectedCourses`、`createCourse`、`updateCourse`、`deactivateCourse`、`recordGrade` |
| 图书馆 | `LibraryService` | `search`、`borrow`、`returnBook` |
| 商店 | `StoreService` | `listProducts`、`purchase`、`ordersFor`、`allOrders` |

所有服务方法返回 `ServiceResult<T>`，由服务器统一映射为 `Message` 响应。服务端必须再次校验会话和权限。

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

例如商店模块若新增订单查询，可使用 `STORE_ORDER_QUERY` 一类消息类型，但需要同时明确：

- 学生、教师查询本人订单；
- 商店管理员查询商店订单列表；
- 其他无关角色请求时服务器返回 `FORBIDDEN`；
- 请求中即使伪造用户编号，也必须以 token 对应身份为准。

## 当前消息类型和载荷

| 模块 | MessageType | 请求 payload | 响应 payload | 主要权限 |
|---|---|---|---|---|
| 学籍 | `STUDENT_QUERY` | `StudentQueryCommand` | `StudentRecord` 或 `List<StudentRecord>` | `STUDENT_READ`，学生仅本人 |
| 学籍 | `STUDENT_UPDATE` | `StudentSaveCommand` | `StudentRecord` | `STUDENT_WRITE` |
| 学籍 | `STUDENT_REVIEW` | `StudentReviewCommand` | `AcademicReview` | 学生本人或 `ACADEMIC_REVIEW` |
| 选课 | `COURSE_QUERY` | `CourseQueryCommand` | `List<Course>` | 课程列表公开；本人已选课需 `COURSE_READ` |
| 选课 | `COURSE_SELECT` / `COURSE_DROP` | `CourseSelectionCommand` | `null` | `COURSE_SELECT`，学生仅本人 |
| 选课 | `COURSE_CREATE` / `COURSE_UPDATE` / `COURSE_DEACTIVATE` | `CourseManagementCommand` | `null` | `COURSE_MANAGE` |
| 选课 | `COURSE_GRADE_WRITE` | `CourseGradeCommand` | `null` | `GRADE_WRITE`，教师仅本人身份 |
| 图书馆 | `LIBRARY_QUERY` | `LibraryQueryCommand` | `List<Book>` | `LIBRARY_READ` |
| 图书馆 | `LIBRARY_BORROW` / `LIBRARY_RETURN` | `LibraryCommand` | `null` | `LIBRARY_BORROW`，读者仅本人；图书管理员可代办 |
| 商店 | `STORE_QUERY` | token 字符串 | `List<Product>` | `STORE_READ` |
| 商店 | `STORE_PURCHASE` | `StoreCommand` | `StoreOrder` | `STORE_PURCHASE`，买家仅本人；商店管理员可管理 |
| 商店 | `STORE_ORDER_QUERY` | `StoreOrderQueryCommand` | `List<StoreOrder>` | 本人订单需 `STORE_PURCHASE`；全部订单需 `STORE_MANAGE` |

## 权限与课程维护公共契约

- 角色新增 `ACADEMIC_ADMIN`；完整角色权限和数据范围以 [`PERMISSIONS.md`](PERMISSIONS.md) 为准。
- 当前实验版公开 `REGISTER` 按 `UserCredentials.roleCode` 创建所有已定义角色账号，便于成员联调；各业务操作仍必须在服务器端按 token、角色权限和数据范围再次校验。
- `REGISTER` 只创建登录账号，不自动生成学生学籍档案、历史成绩或学业审查记录；这些数据由学籍/教务模块维护或由演示数据导入。
- `LOGIN` 成功后返回 `Session`；同一账号已有活动会话时再次登录返回 `CONFLICT`，登出或注销后可重新登录。
- 首个系统管理员由服务器读取 `VCAMPUS_BOOTSTRAP_ADMIN_ID`、`VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD`、`VCAMPUS_BOOTSTRAP_ADMIN_NAME` 后在进程内初始化，不通过 Socket 暴露管理员注册接口。
- 权限新增 `COURSE_MANAGE`、`GRADE_WRITE`、`ACADEMIC_REVIEW`。
- 课程维护使用 `COURSE_CREATE`、`COURSE_UPDATE`、`COURSE_DEACTIVATE`，均要求 `COURSE_MANAGE`；成绩录入使用 `COURSE_GRADE_WRITE`，要求 `GRADE_WRITE`。
- `COURSE_DEACTIVATE` 表示停开；存在选课或历史记录时不得直接删除关联数据。
- 客户端只负责按角色隐藏无权入口，服务器 Handler 必须在调用业务接口前执行 `authorize`，拒绝时返回 `FORBIDDEN`。

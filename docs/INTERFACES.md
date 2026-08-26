# 模块接口基线

接口位于各业务模块的 `api` 等价包（当前使用模块根包，后续可按团队约定细分）。接口只描述业务能力，不直接依赖 Swing、Socket 或 Access。

| 模块 | 核心接口 | 初始操作 |
|---|---|---|
| 用户管理 | `UserManagementService` | `register`、`unregister`、`login`、`currentSession`、`logout`、`authorize`；载荷见 `UserCredentials`、`UserCommand`、`AuthorizationRequest` |
| 学生学籍 | `StudentManagementService` | `findById`、`findByClass`、`save` |
| 选课 | `CourseSelectionService` | `listCourses`、`select`、`drop`、`selectedCourses`；课程维护消息见下文 |
| 图书馆 | `LibraryService` | `search`、`borrow`、`returnBook` |
| 商店 | `StoreService` | `listProducts`、`purchase` |

所有服务方法返回 `ServiceResult<T>`，由服务器统一映射为 `Message` 响应。服务端必须再次校验会话和权限。

## 权限与课程维护公共契约

- 角色新增 `ACADEMIC_ADMIN`；完整角色权限和数据范围以 [`PERMISSIONS.md`](PERMISSIONS.md) 为准。
- 当前实验版公开 `REGISTER` 按 `UserCredentials.roleCode` 创建所有已定义角色账号，便于成员联调；各业务操作仍必须在服务器端按 token、角色权限和数据范围再次校验。
- `REGISTER` 只创建登录账号，不自动生成学生学籍档案、历史成绩或学业审查记录；这些数据由学籍/教务模块维护或由演示数据导入。
- `LOGIN` 成功后返回 `Session`；同一账号已有活动会话时再次登录返回 `CONFLICT`，登出或注销后可重新登录。
- 首个系统管理员由服务器读取 `VCAMPUS_BOOTSTRAP_ADMIN_ID`、`VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD`、`VCAMPUS_BOOTSTRAP_ADMIN_NAME` 后在进程内初始化，不通过 Socket 暴露管理员注册接口。
- 权限新增 `COURSE_MANAGE`、`GRADE_WRITE`、`ACADEMIC_REVIEW`。
- 课程维护使用 `COURSE_CREATE`、`COURSE_UPDATE`、`COURSE_DEACTIVATE`，均要求 `COURSE_MANAGE`。
- `COURSE_DEACTIVATE` 表示停开；存在选课或历史记录时不得直接删除关联数据。
- 客户端只负责按角色隐藏无权入口，服务器 Handler 必须在调用业务接口前执行 `authorize`，拒绝时返回 `FORBIDDEN`。

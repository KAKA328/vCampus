# 模块接口基线

接口位于各业务模块的 `api` 等价包（当前使用模块根包，后续可按团队约定细分）。接口只描述业务能力，不直接依赖 Swing、Socket 或 Access。

| 模块 | 核心接口 | 初始操作 |
|---|---|---|
| 用户管理 | `UserManagementService` | `register`、`unregister`、`login`、`logout`、`authorize`；载荷见 `UserCredentials`、`UserCommand`、`AuthorizationRequest` |
| 学生学籍 | `StudentManagementService` | `findById`、`findByClass`、`save` |
| 选课 | `CourseSelectionService` | `listCourses`、`select`、`drop`、`selectedCourses` |
| 图书馆 | `LibraryService` | `search`、`borrow`、`returnBook` |
| 商店 | `StoreService` | `listProducts`、`purchase` |

所有服务方法返回 `ServiceResult<T>`，由服务器统一映射为 `Message` 响应。服务端必须再次校验会话和权限。

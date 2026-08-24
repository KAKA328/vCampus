# 协作分工与提交规则

## 建议分工

| 模块 | 负责人 | 分支示例 |
|---|---|---|
| 用户管理、总控、集成 | 组长 | `feature/user-management` |
| 学生学籍管理 | 组员 A | `feature/student-management` |
| 选课系统 | 组员 B | `feature/course-selection` |
| 图书馆 | 组员 C | `feature/library` |
| 商店 | 组员 D | `feature/store` |

## 每个模块交付物

1. API 接口和领域模型实现。
2. Repository/DAO 实现及数据库表说明。
3. 正常、异常和权限场景测试。
4. 模块 README 和 JavaDoc。
5. 客户端调用示例，不能直接修改其他模块的业务代码。

## Pull Request 检查项

- 能在 JDK 8 编译；
- `mvn clean test` 通过；
- 不提交包含真实数据的数据库文件、密码或 IDE 产物；仅可提交 `database/` 下的脱敏测试数据库和脚本；
- 接口变更同步更新 `common` 协议和设计说明书；
- PR 说明包含变更内容、测试方式和已知问题。

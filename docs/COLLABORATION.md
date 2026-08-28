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

## 合并前公共接口核对流程

合并任何子系统 PR 前，组长必须单独检查是否新增或修改了公共接口。公共接口包括但不限于：

- `common/src/main/java/cn/vcampus/common/MessageType.java` 中的消息类型；
- `common/src/main/java/cn/vcampus/common/Permission.java`、`Role.java` 等权限与身份定义；
- 跨模块传输的 `Command`、`DTO`、实体类；
- 服务器 `MessageHandler` 和 `ServerApplication` 分发入口；
- 客户端远程服务调用和 Swing 入口；
- 数据库表结构、字段和约束。

如果某个模块需要新增消息类型，例如商店模块的 `STORE_ORDER_QUERY`，提交 PR 时必须同时说明：

1. 新增的消息类型名称和用途；
2. 请求 payload 类型、字段含义和权限要求；
3. 响应 payload 类型和可能返回的 `StatusCode`；
4. 服务端 Handler 是否已经接入；
5. 客户端页面或远程服务是否已经调用；
6. `docs/INTERFACES.md`、`docs/MODULE_INTEGRATION_GUIDE.md` 是否已同步更新；
7. 是否补充了正常流程、异常流程、权限拒绝流程测试。

缺少以上内容时，不直接合并到 `main`。可以要求组员补充，也可以由组长新建一个小的“公共接口补齐”提交后再合并。

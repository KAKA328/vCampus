# 模块对接说明

本文档用于指导五个业务模块在同一个 vCampus 项目中协作开发和集成。请各模块负责人先阅读本文件，再开始编写代码。

## 1. 当前项目状态

当前仓库已经提供：

- Maven 多模块项目结构；
- 客户端、服务器端、公共协议模块；
- 用户管理模块基础实现；
- Swing 登录、主界面总控框架；开户注册入口收敛到管理员用户管理页面；
- `Message` 消息协议、`ServiceResult` 返回格式和 `StatusCode` 状态码；
- 学生学籍、选课、图书馆、商店四个模块的基础接口和实体；图书馆已补齐内存业务、Access 馆藏/借阅仓储、V2 协议和 Swing 页面；商店服务已补齐内存业务、Access 商品/订单仓储及管理/购物车协议处理；
- 选课模块的轮次查询、教学班查询、学生选课、退选和本人已选教学班查询已接入服务器和学生客户端页面，完整流程使用显式 V2 协议。

当前图书馆已接入目录/详情查询、原子批量借阅、按记录归还、本人/全量记录及新增馆藏的客户端、服务器和 Access 仓储。学生学籍已完成服务器分发、Access 仓储、Token 身份映射和基础 Swing 页面；学业审查已提供历史课程、待重修和实时审查接口。商店服务器分发已覆盖查询、购买、购物车、钱包、商品维护、订单管理和热销排行，客户端已覆盖商品查询、购买、本人订单、购物车和钱包操作，管理员商品维护页面仍待补齐；选课模块仍需补充教师成绩录入和教务复核等管理功能。

## 2. 队友开始开发前要做什么

建议先由组长把最新的 `feature/user-management` 通过 Pull Request 合并到 `main`。合并后，所有成员都从最新 `main` 开始自己的分支。

在 VSCode 打开项目后，在终端执行：

```powershell
git switch main
git pull origin main
```

然后每个成员创建自己的功能分支：

```powershell
git switch -c feature/student-management
git switch -c feature/course-selection
git switch -c feature/library
git switch -c feature/store
```

分支名建议和负责模块一致，不要多人共用一个功能分支。

## 3. 每个模块负责哪些目录

| 模块 | 主要目录 | 说明 |
|---|---|---|
| 用户管理 | `user-management/` | 组长负责，提供管理员开户注册、批量导入、登录、登出、注销、授权 |
| 学生学籍管理 | `student-management/` | 学生信息实体、业务接口、数据库访问 |
| 选课系统 | `course-selection/` | 课程信息、选课、退课、已选课程查询 |
| 图书馆 | `library/` | 图书查询、借阅、归还、借阅记录 |
| 商店 | `store/` | 商品查询、购买、库存、购买记录、购物车、钱包余额 |
| 公共协议 | `common/` | 共享实体、消息类型、状态码，只在确有必要时修改 |
| 服务器 | `server/` | Socket 启动入口、消息处理器、Access 仓储实现 |
| 客户端 | `client/` | Swing 页面、远程服务调用、主界面接入 |
| 数据库 | `database/` | 表结构脚本、迁移脚本、数据库说明 |
| 文档 | `docs/` | 接口说明、设计说明、测试报告、使用说明 |

原则：业务逻辑优先写在自己模块中；公共类型放入 `common` 前要先和组长确认，避免公共协议被频繁改坏。

账号与业务档案的对接统一遵守 [`ACCOUNT_PROFILE_INTEGRATION.md`](ACCOUNT_PROFILE_INTEGRATION.md)：`user_id` 只表示登录账号，`student_id` 只表示学生学号，`teacher_id` 只表示教师工号。学生/教师档案可以先存在，再由管理员创建或导入账号并完成绑定。

## 4. 模块分层约定

每个业务模块建议按以下结构组织：

```text
模块名/
└── src/main/java/cn/vcampus/模块包名/
    ├── XxxService.java        业务接口
    ├── Xxx.java               领域实体
    ├── XxxCommand.java        请求参数，可选
    ├── XxxRepository.java     数据访问接口，可选
    └── InMemoryXxxService.java 或 DefaultXxxService.java
```

服务器端 Access 数据库访问类建议放在：

```text
server/src/main/java/cn/vcampus/server/
```

例如：

```text
AccessStudentRepository.java
AccessCourseRepository.java
AccessBookRepository.java
AccessProductRepository.java
```

客户端 Swing 页面建议放在：

```text
client/src/main/java/cn/vcampus/client/view/
```

例如：

```text
StudentManagementPanel.java
CourseSelectionPanel.java
LibraryPanel.java
StorePanel.java
```

## 5. 公共通信协议

客户端和服务器通过 `Message` 传输请求和响应。

公共类位置：

```text
common/src/main/java/cn/vcampus/common/Message.java
common/src/main/java/cn/vcampus/common/MessageType.java
common/src/main/java/cn/vcampus/common/StatusCode.java
common/src/main/java/cn/vcampus/common/ServiceResult.java
```

### 5.1 Message 字段含义

| 字段 | 含义 |
|---|---|
| `requestId` | 请求编号，用于区分一次请求 |
| `type` | 消息类型，例如 `COURSE_SELECTION_QUERY_V2`、`LIBRARY_BORROW` |
| `statusCode` | 响应状态，成功为 `OK` |
| `sender` | 发送方，可选 |
| `payload` | 请求或响应数据，必须可序列化 |

创建请求示例：

```java
Message request = Message.request("course-001", MessageType.COURSE_SELECTION_QUERY_V2, payload);
```

创建响应通常由服务器处理器完成：

```java
Message response = Message.response(request, StatusCode.OK, data);
```

### 5.2 MessageType 分配

当前已有消息类型：

| 模块 | MessageType |
|---|---|
| 用户管理 | `REGISTER`、`USER_IMPORT`、`UNREGISTER`、`LOGIN`、`LOGOUT`、`AUTHORIZE` |
| 学生学籍 | `STUDENT_QUERY`、`STUDENT_UPDATE` |
| 选课系统 | 完整选课 V2：`COURSE_SELECTION_QUERY_V2`、`COURSE_SELECT_OFFERING_V2`、`COURSE_DROP_RECORD_V2`；课程维护：`COURSE_MANAGE` + `CourseManagementCommand`，含课程目录、教学班创建、教学信息维护和选课轮次管理 |

如果需要新增消息类型，必须同步修改：

```text
common/src/main/java/cn/vcampus/common/MessageType.java
docs/INTERFACES.md
docs/MODULE_INTEGRATION_GUIDE.md
```

新增消息类型不能只改枚举。合并前必须同时确认：请求 payload、响应 payload、服务端 Handler、`ServerApplication` 分发、客户端远程调用、权限校验、接口文档和测试是否一起补齐。商店命令均携带 token；服务器端必须按 token 和角色判断数据范围，不能只靠客户端隐藏按钮。`STORE_ORDER_QUERY` 只返回本人订单，`STORE_ORDER_LIST_ALL` 才允许商店管理员查看全量订单。

商店钱包（`STORE_ACCOUNT_*`）与购买/结账对接：`DefaultStoreService` 注入 5 个依赖（第 4 个 `BankAccountRepository`、第 5 个 `WalletTransactionRepository`），`purchase`/`checkout` 走「预检(仅提示) → 原子 `deductStock` → 原子 `debit` → 建单(UUID) → 清空购物车」的补偿顺序，任一步失败按序回滚此前已扣项，每个补偿都检查返回值，补偿失败仍返回 `CONFLICT`；这是单 JVM 下的补偿一致性，不是数据库事务。余额以「分」为单位存 `long`（`balance_cents BIGINT`），支付边界 `Math.round(totalPrice * 100)` 换算一次。`--db` 分支下账户走 `AccessBankAccountRepository`、流水走 `AccessWalletTransactionRepository`（每仓储独立 JDBC 连接，无跨表事务），内存分支走对应 `InMemory*Repository`，两种模式接口一致。`STORE_ACCOUNT_ADJUST` 在服务端做双重门槛校验（`STORE_MANAGE` 权限 + 角色 ∈ {`ADMIN`, `STORE_MANAGER`}），客户端隐藏校正按钮只是 UX。

商店钱包流水（`STORE_ACCOUNT_LEDGER`）是**尽力而为的审计副产物**：每次资金变动（充值/购买/结账/补偿退款/管理员校正）在钱**已实际变动之后**追加一条 `tblWalletTransaction`，`append` 返回 `false` 或抛 `RuntimeException` 都只记日志，**绝不因审计写不进去而回滚一笔已成功的资金变动**；流水与余额写入不在同一事务内。`amountCents` 带符号（入账为正、扣款为负、校正为差额）可直接累加对账；`balanceAfterCents` 是写入后回读值，并发下**仅作展示、不作对账依据**；`operatorId` 让管理员校正不再丢失「谁改的」。

商店购物车的 `STORE_CART_UPDATE`（改数量）与 `STORE_CART_DETAIL`（明细）：两者 `userId` 均取自 token，改数量额外在服务层校验条目归属本人（不属于本人返回 `NOT_FOUND`，不区分「不存在」与「不是你的」）；明细返回 `CartLine` 读模型，是**读取时与商品实时联表**的结果，`CartItem` 未加快照字段、`tblCartItem` 未加列，旧库无需迁移。

完整选课流程统一使用显式 V2 Socket 协议。客户端必须使用：

- `COURSE_SELECTION_QUERY_V2` + `CourseSelectionQueryV2Command(token, roundId?)`
- `COURSE_SELECT_OFFERING_V2` + `CourseSelectOfferingV2Command(token, roundId, offeringId)`
- `COURSE_DROP_RECORD_V2` + `CourseDropRecordV2Command(token, recordId)`

客户端不再提交 `studentId` 作为本人身份，服务器必须根据 `token -> user_id -> student_id` 推导学生档案。

公共角色、权限编码和数据范围见 [`PERMISSIONS.md`](PERMISSIONS.md)。课程新增、修改和停开操作必须先校验 `COURSE_MANAGE`；任课教师录入成绩校验 `GRADE_WRITE`；教务复核校验 `ACADEMIC_REVIEW`。

## 6. Payload 设计规则

`payload` 可以是字符串、实体对象或命令对象，但必须实现 `java.io.Serializable`。

推荐规则：

- 查询列表：payload 可以传关键词、学生编号或专门的查询命令；
- 新增/修改：payload 传实体或保存命令；
- 涉及登录权限的操作：payload 必须包含 `token`；
- 不要在 payload 中传明文密码，除登录和管理员开户注册的 `UserCredentials` 外；
- 用户批量导入例外：`USER_IMPORT` 请求使用 `UserImportCommand(token, rows)`，每行是 `UserImportRow(userId, password, displayName, roleCode)`；只允许管理员端发起，服务端写入 `created_by`、`created_at`、`import_batch_id` 并返回 `UserImportResult`；
- 学生本人、教师本人相关操作应优先只传 `token` 和业务对象编号，服务器根据 `token -> user_id -> student_id/teacher_id` 推导真实业务身份；过渡期如命令对象仍带 `studentId` 或 `teacherId`，服务器必须做一致性校验，不能直接信任客户端传值；
- 不要把数据库连接、文件路径、Socket 对象放进 payload。

示例：学生本人选课命令对象应使用 V2 协议，只携带 token 和轮次/教学班编号：

```java
public final class CourseSelectOfferingV2Command implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String roundId;
    private final String offeringId;

    public CourseSelectOfferingV2Command(String token, String roundId, String offeringId) {
        this.token = token;
        this.roundId = roundId;
        this.offeringId = offeringId;
    }

    public String getToken() { return token; }
    public String getRoundId() { return roundId; }
    public String getOfferingId() { return offeringId; }
}
```

退选使用 `CourseDropRecordV2Command(token, recordId)`。服务器必须通过 token 查到当前 `user_id`，再查询绑定的 `student_id`，不能信任客户端提供的学生身份。

## 7. ServiceResult 返回规范

所有业务接口统一返回 `ServiceResult<T>`。

服务端处理业务请求时必须先校验 token、角色权限和数据范围。同一账号同一时间只允许一个活动会话；重复登录返回 `CONFLICT`，登出或注销后释放会话。

成功：

```java
return ServiceResult.ok(data);
```

失败：

```java
return ServiceResult.failure(StatusCode.NOT_FOUND, "course not found");
```

常用状态码：

| 状态码 | 使用场景 |
|---|---|
| `OK` | 成功 |
| `BAD_REQUEST` | 参数类型错误、字段为空、格式不合法 |
| `UNAUTHORIZED` | 未登录、token 无效 |
| `FORBIDDEN` | 已登录但没有权限 |
| `NOT_FOUND` | 数据不存在 |
| `CONFLICT` | 数据冲突，例如重复选课、重复账号、库存/余额并发变化导致补偿失败 |
| `PAYMENT_REQUIRED` | 余额不足，需先充值（商店钱包购买/结账） |
| `SERVER_ERROR` | 服务器内部错误 |

## 8. 服务器端如何接入模块

当前服务器入口：

```text
server/src/main/java/cn/vcampus/server/ServerApplication.java
```

当前用户管理处理器：

```text
server/src/main/java/cn/vcampus/server/UserMessageHandler.java
```

其他模块接入时，建议每个模块新增一个消息处理器：

```text
StudentMessageHandler.java
CourseMessageHandler.java
LibraryMessageHandler.java
StoreMessageHandler.java
```

选课处理器以 `CourseMessageHandler` 为唯一入口：学生查询、选课、退选命令先按 token 校验权限，再由 `user_id` 查询学生档案；教务管理命令则要求 `COURSE_MANAGE` 权限。不要重新引入由客户端传递 `studentId` 和 `courseId` 的课程级简化请求。

接入 `ServerApplication` 时，建议由组长统一整合 `dispatch`，避免多人同时修改同一个文件造成冲突。

整合后的分发逻辑应类似：

```java
private Message dispatch(Message request) {
    switch (request.getType()) {
        case REGISTER:
        case USER_IMPORT:
        case UNREGISTER:
        case LOGIN:
        case LOGOUT:
        case AUTHORIZE:
            return userMessages.handle(request);
        case COURSE_SELECTION_QUERY_V2:
        case COURSE_SELECT_OFFERING_V2:
        case COURSE_DROP_RECORD_V2:
        case COURSE_MANAGE:
            return courseMessages.handle(request);
        case LIBRARY_QUERY_V2:
        case LIBRARY_DETAIL_V2:
        case LIBRARY_BORROW_V2:
        case LIBRARY_RETURN_V2:
        case LIBRARY_HISTORY_V2:
        case LIBRARY_ADD_BOOK_V2:
            return libraryMessages.handle(request);
        case STORE_QUERY:
        case STORE_PURCHASE:
        case STORE_ORDER_QUERY:
        case STORE_RESTOCK:
        case STORE_PRODUCT_ADD:
        case STORE_PRODUCT_UPDATE:
        case STORE_PRODUCT_DEACTIVATE:
        case STORE_CART_ADD:
        case STORE_CART_REMOVE:
        case STORE_CART_UPDATE:
        case STORE_CART_QUERY:
        case STORE_CART_DETAIL:
        case STORE_CART_CHECKOUT:
        case STORE_ORDER_LIST_ALL:
        case STORE_HOT_PRODUCTS:
        case STORE_ACCOUNT_QUERY:
        case STORE_ACCOUNT_RECHARGE:
        case STORE_ACCOUNT_ADJUST:
        case STORE_ACCOUNT_LEDGER:
            return storeMessages.handle(request);
        case STUDENT_QUERY:
        case STUDENT_UPDATE:
            return studentMessages.handle(request);
        default:
            return Message.response(request, StatusCode.NOT_FOUND, "message type is not supported");
    }
}
```

## 9. 客户端如何接入 Swing 主界面

当前主界面：

```text
client/src/main/java/cn/vcampus/client/view/MainFrame.java
```

当前模块导航模型：

```text
client/src/main/java/cn/vcampus/client/view/ModuleNavigationModel.java
client/src/main/java/cn/vcampus/client/view/ModuleDescriptor.java
```

各模块负责人建议先提供自己的 `JPanel` 页面，不要一开始大改 `MainFrame`。

页面类示例：

```java
public final class CourseSelectionPanel extends JPanel {
    public CourseSelectionPanel(Session session) {
        setLayout(new BorderLayout());
        add(new JLabel("选课系统"), BorderLayout.NORTH);
    }
}
```

如果页面需要访问服务器，建议新增远程服务类：

```text
client/src/main/java/cn/vcampus/client/service/RemoteCourseService.java
client/src/main/java/cn/vcampus/client/service/RemoteLibraryService.java
client/src/main/java/cn/vcampus/client/service/RemoteStoreService.java
client/src/main/java/cn/vcampus/client/service/RemoteStudentService.java
```

远程服务类内部使用：

```text
client/src/main/java/cn/vcampus/client/transport/SocketMessageClient.java
```

客户端请求示例：

```java
try (SocketMessageClient client = new SocketMessageClient(host, port)) {
    Message request = Message.request("course-query-001", MessageType.COURSE_SELECTION_QUERY_V2,
            CourseSelectionQueryV2Command.availableRounds(token));
    Message response = client.send(request);
}
```

最终把页面接入 `MainFrame` 时，建议由组长统一合并，避免四个人同时改主界面文件导致冲突。

## 10. 数据库表设计约定

数据库脚本位置：

```text
database/schema.sql
database/migrations/
```

表名统一使用 `tbl` 前缀，例如：

| 模块 | 表名建议 |
|---|---|
| 用户管理 | `tblUser`、`tblAuditLog` |
| 学生学籍 | `tblStudent`、`tblClass` |
| 选课系统 | `tblCourse`、`tblCourseSelection` |
| 图书馆 | `tblBook`、`tblBorrowRecord` |
| 商店 | `tblProduct`、`tblOrder` |

字段命名建议统一使用小写加下划线，例如：

```text
student_id
course_id
created_at
updated_at
```

账号与学生/教师档案绑定字段固定如下：

| 字段 | 所在表 | 含义 | 使用规则 |
|---|---|---|---|
| `user_id` | `tblUser` | 登录账号主键 | 登录、会话、权限、商店订单、图书借阅统一使用 |
| `student_id` | `tblStudent` | 学生学号 | 学籍、选课、课程结果、学业审查使用 |
| `teacher_id` | `tblTeacher` | 教师工号 | 任课关系、成绩录入、教师档案使用 |
| `user_id` | `tblStudent` / `tblTeacher` | 档案绑定账号 | 可空、唯一；允许先建档案再绑定账号 |

各模块不得把学号直接当作登录账号，也不得把教师工号直接当作登录账号。需要从登录身份进入业务身份时，由服务器根据当前 `user_id` 查询绑定档案。

数据库代码必须使用参数化查询，不要拼接 SQL 字符串。

推荐：

```java
PreparedStatement statement = connection.prepareStatement(
        "SELECT * FROM tblUser WHERE user_id = ?");
statement.setString(1, userId);
```

禁止：

```java
"SELECT * FROM tblUser WHERE user_id = '" + userId + "'"
```

## 11. 各模块最低交付要求

每个模块负责人至少提交以下内容：

1. 业务接口实现；
2. 领域实体或命令对象；
3. Access 数据库访问类或内存测试实现；
4. 服务器消息处理器；
5. Swing 页面或页面雏形；
6. 单元测试或集成测试；
7. 数据库表结构说明；
8. 模块 README 或文档说明。

## 12. 本地验证命令

在 VSCode 终端进入项目根目录：

```powershell
cd D:\codex\java协作
```

运行全部测试：

```powershell
mvn clean test
```

数据库演示数据应预置系统管理员、教务管理员、图书管理员、商店管理员、教师和学生账号；所有演示账号的初始密码在 `database/seed.sql` 中说明。若使用内存模式或空数据库首次启动，可在启动服务器的同一个 PowerShell 终端设置：

```powershell
$env:VCAMPUS_BOOTSTRAP_ADMIN_ID="admin001"
$env:VCAMPUS_BOOTSTRAP_ADMIN_PASSWORD="Admin123"
$env:VCAMPUS_BOOTSTRAP_ADMIN_NAME="系统管理员"
```

该账号由服务器进程初始化，不经过客户端开户注册页面。不要把真实密码写入源码、脚本或提交记录。

启动服务器：

```powershell
java -cp "common\target\classes;user-management\target\classes;server\target\classes" cn.vcampus.server.ServerApplication 19090
```

启动客户端：

```powershell
java -cp "common\target\classes;user-management\target\classes;client\target\classes" cn.vcampus.client.ClientApplication --host 127.0.0.1 --port 19090
```

说明：启动命令中的地址和端口用于程序连接，不会显示在客户端界面中。

## 13. 提交和 PR 规则

每次完成一个小功能后提交：

```powershell
git add .
git commit -m "feat(course): add course selection service"
```

推送自己的分支：

```powershell
git push origin feature/course-selection
```

然后在 GitHub 创建 Pull Request。分支名可以继续使用英文或拼音，避免工具兼容问题；PR 标题和描述尽量使用中文，便于组内同学和老师助教理解。

PR 标题建议：

```text
选课：接入学生选课与退课功能
学籍：新增学生档案查询与保存接口
图书馆：接入馆藏查询、批量借阅、归还、借阅记录与馆藏维护
商店：接入商品购买与订单查询功能
用户管理：补充账号与学生教师档案绑定规范
```

PR 描述至少包含：

- 完成了什么功能；
- 修改了哪些模块；
- 如何测试；
- 是否修改了数据库表；
- 是否需要组长协助接入主界面或服务器分发。

## 14. PR 提交前检查清单

提交 PR 前请逐项检查：

- [ ] 只修改自己负责模块和必要的公共文件；
- [ ] 没有提交 `.class`、`target/`、IDE 配置、临时文件；
- [ ] 没有提交真实密码、token 或包含真实隐私的数据；
- [ ] 需要跨模块使用的类已经实现 `Serializable`；
- [ ] 服务器端已经做权限校验；
- [ ] 数据库访问使用参数化查询；
- [ ] `mvn clean test` 可以通过；
- [ ] 如果改了接口或消息类型，已同步更新 `docs/INTERFACES.md`；
- [ ] 如果新增了 `MessageType`，已同步补齐请求/响应载荷、服务器 Handler、客户端调用、权限校验和对应测试；
- [ ] 如果改了数据库表，已同步更新 `database/schema.sql` 或迁移脚本；
- [ ] 如果改了界面，已准备截图给组长确认。

## 14.1 组长合并前检查清单

组长合并 PR 前，除确认测试通过外，还需要重点检查公共契约是否变化：

- [ ] 是否修改 `common/` 下的 `MessageType`、`Permission`、`Role`、通用实体或状态码；
- [ ] 是否新增跨模块接口、命令对象、响应对象；
- [ ] 是否需要在 `ServerApplication` 中新增分发分支；
- [ ] 是否需要在客户端主界面或远程服务中新增入口；
- [ ] 是否同步更新 `docs/INTERFACES.md`、`docs/PERMISSIONS.md`、`database/README.md`；
- [ ] 是否存在“代码能编译，但其他模块不知道如何调用”的半成品接口；
- [ ] 是否存在只在客户端限制权限、服务器没有返回 `FORBIDDEN` 的安全漏洞。

如果发现新增接口没有被完整接入，应先让模块负责人补齐，或由组长创建小型公共接口修复提交，再合并到 `main`。

## 15. 推荐协作方式

为了减少冲突，建议这样分工：

- 组员负责自己模块的 Service、Repository、Handler、Panel；
- 组长负责最终整合 `ServerApplication` 和 `MainFrame`；
- 每个模块先独立通过测试，再发 PR；
- 涉及 `common/`、`server/ServerApplication.java`、`client/view/MainFrame.java` 的修改，提前在群里说明；
- 不要直接向 `main` 推送代码，统一通过 PR 合并。

这样做可以保证每个人的模块先独立完成，再由组长统一集成，避免五个人同时改核心入口文件导致大量冲突。


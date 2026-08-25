# 模块对接说明

本文档用于指导五个业务模块在同一个 vCampus 项目中协作开发和集成。请各模块负责人先阅读本文件，再开始编写代码。

## 1. 当前项目状态

当前仓库已经提供：

- Maven 多模块项目结构；
- 客户端、服务器端、公共协议模块；
- 用户管理模块基础实现；
- Swing 登录、注册、主界面总控框架；
- `Message` 消息协议、`ServiceResult` 返回格式和 `StatusCode` 状态码；
- 学生学籍、选课、图书馆、商店四个模块的基础接口和实体占位类。

当前其他四个业务模块还没有完整接入服务器分发和客户端页面，需要各模块负责人继续实现。

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
| 用户管理 | `user-management/` | 组长负责，提供注册、登录、登出、注销、授权 |
| 学生学籍管理 | `student-management/` | 学生信息实体、业务接口、数据库访问 |
| 选课系统 | `course-selection/` | 课程信息、选课、退课、已选课程查询 |
| 图书馆 | `library/` | 图书查询、借阅、归还、借阅记录 |
| 商店 | `store/` | 商品查询、购买、库存、购买记录 |
| 公共协议 | `common/` | 共享实体、消息类型、状态码，只在确有必要时修改 |
| 服务器 | `server/` | Socket 启动入口、消息处理器、Access 仓储实现 |
| 客户端 | `client/` | Swing 页面、远程服务调用、主界面接入 |
| 数据库 | `database/` | 表结构脚本、迁移脚本、数据库说明 |
| 文档 | `docs/` | 接口说明、设计说明、测试报告、使用说明 |

原则：业务逻辑优先写在自己模块中；公共类型放入 `common` 前要先和组长确认，避免公共协议被频繁改坏。

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
| `type` | 消息类型，例如 `COURSE_QUERY`、`LIBRARY_BORROW` |
| `statusCode` | 响应状态，成功为 `OK` |
| `sender` | 发送方，可选 |
| `payload` | 请求或响应数据，必须可序列化 |

创建请求示例：

```java
Message request = Message.request("course-001", MessageType.COURSE_QUERY, payload);
```

创建响应通常由服务器处理器完成：

```java
Message response = Message.response(request, StatusCode.OK, data);
```

### 5.2 MessageType 分配

当前已有消息类型：

| 模块 | MessageType |
|---|---|
| 用户管理 | `REGISTER`、`UNREGISTER`、`LOGIN`、`LOGOUT`、`AUTHORIZE` |
| 学生学籍 | `STUDENT_QUERY`、`STUDENT_UPDATE` |
| 选课系统 | `COURSE_QUERY`、`COURSE_SELECT`、`COURSE_DROP` |
| 图书馆 | `LIBRARY_QUERY`、`LIBRARY_BORROW`、`LIBRARY_RETURN` |
| 商店 | `STORE_QUERY`、`STORE_PURCHASE` |

如果需要新增消息类型，必须同步修改：

```text
common/src/main/java/cn/vcampus/common/MessageType.java
docs/INTERFACES.md
docs/MODULE_INTEGRATION_GUIDE.md
```

## 6. Payload 设计规则

`payload` 可以是字符串、实体对象或命令对象，但必须实现 `java.io.Serializable`。

推荐规则：

- 查询列表：payload 可以传关键词、学生编号或专门的查询命令；
- 新增/修改：payload 传实体或保存命令；
- 涉及登录权限的操作：payload 必须包含 `token`；
- 不要在 payload 中传明文密码，除登录/注册的 `UserCredentials` 外；
- 不要把数据库连接、文件路径、Socket 对象放进 payload。

示例：选课命令对象可以这样设计：

```java
public final class CourseSelectionCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String token;
    private final String studentId;
    private final String courseId;

    public CourseSelectionCommand(String token, String studentId, String courseId) {
        this.token = token;
        this.studentId = studentId;
        this.courseId = courseId;
    }

    public String getToken() { return token; }
    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
}
```

## 7. ServiceResult 返回规范

所有业务接口统一返回 `ServiceResult<T>`。

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
| `CONFLICT` | 数据冲突，例如重复选课、重复账号 |
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

处理器的基本结构参考：

```java
final class CourseMessageHandler {
    private final CourseSelectionService service;
    private final UserManagementService users;

    CourseMessageHandler(CourseSelectionService service, UserManagementService users) {
        this.service = service;
        this.users = users;
    }

    Message handle(Message request) {
        try {
            ServiceResult<?> result;
            switch (request.getType()) {
                case COURSE_QUERY:
                    result = service.listCourses();
                    break;
                case COURSE_SELECT:
                    CourseSelectionCommand select = payload(request, CourseSelectionCommand.class);
                    ServiceResult<Void> auth = users.authorize(select.getToken(), "COURSE_SELECT");
                    if (auth.getStatus() != StatusCode.OK) {
                        return Message.response(request, auth.getStatus(), null);
                    }
                    result = service.select(select.getStudentId(), select.getCourseId());
                    break;
                default:
                    return Message.response(request, StatusCode.NOT_FOUND, "course handler does not support this message");
            }
            return Message.response(request, result.getStatus(), result.getData());
        } catch (IllegalArgumentException invalidPayload) {
            return Message.response(request, StatusCode.BAD_REQUEST, "request payload is invalid");
        }
    }

    private static <T> T payload(Message request, Class<T> type) {
        Object payload = request.getPayload();
        if (!type.isInstance(payload)) {
            throw new IllegalArgumentException("unexpected payload type");
        }
        return type.cast(payload);
    }
}
```

接入 `ServerApplication` 时，建议由组长统一整合 `dispatch`，避免多人同时修改同一个文件造成冲突。

整合后的分发逻辑应类似：

```java
private Message dispatch(Message request) {
    switch (request.getType()) {
        case REGISTER:
        case UNREGISTER:
        case LOGIN:
        case LOGOUT:
        case AUTHORIZE:
            return userMessages.handle(request);
        case COURSE_QUERY:
        case COURSE_SELECT:
        case COURSE_DROP:
            return courseMessages.handle(request);
        case LIBRARY_QUERY:
        case LIBRARY_BORROW:
        case LIBRARY_RETURN:
            return libraryMessages.handle(request);
        case STORE_QUERY:
        case STORE_PURCHASE:
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
    Message request = Message.request("course-query-001", MessageType.COURSE_QUERY, null);
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

然后在 GitHub 创建 Pull Request。

PR 标题建议：

```text
feat(course): add course selection module
feat(student): add student management module
feat(library): add library module
feat(store): add store module
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
- [ ] 如果改了数据库表，已同步更新 `database/schema.sql` 或迁移脚本；
- [ ] 如果改了界面，已准备截图给组长确认。

## 15. 推荐协作方式

为了减少冲突，建议这样分工：

- 组员负责自己模块的 Service、Repository、Handler、Panel；
- 组长负责最终整合 `ServerApplication` 和 `MainFrame`；
- 每个模块先独立通过测试，再发 PR；
- 涉及 `common/`、`server/ServerApplication.java`、`client/view/MainFrame.java` 的修改，提前在群里说明；
- 不要直接向 `main` 推送代码，统一通过 PR 合并。

这样做可以保证每个人的模块先独立完成，再由组长统一集成，避免五个人同时改核心入口文件导致大量冲突。


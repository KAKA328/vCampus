# vCampus 软件设计说明书草案

> 本文按课程《软件设计说明书DEMO》编排，是开发和分工基线。最终提交时将内容复制到课程 Word 模板，并补充正式图件、成员信息、截图和教师要求的封面。

## 1. 引言

### 1.1 编写目的

本文定义 vCampus 虚拟校园综合管理系统的需求、结构、模块职责、接口、数据和验收边界，用于指导组内并行开发，保证客户端、服务器端、数据库和文档使用同一套命名与协议。

### 1.2 项目背景

系统把学生在校园内常用的用户身份、学籍、选课、图书馆和商店业务集中到一个 Java C/S 应用中。客户端负责交互，服务器端负责会话、权限、业务规则和数据访问，数据库保存业务数据。

### 1.3 范围

本期只验收五个必做模块：用户管理、学生学籍管理、选课系统、图书馆、商店。银行、医院、宿舍、在线课堂等不属于本期必交范围，若增加必须保证不影响五个必做模块。

### 1.4 目标用户和角色

| 角色 | 说明 |
|---|---|
| 学生 `STUDENT` | 查看和维护允许范围内的个人学籍，选课/退选，借书/还书，浏览和购买商品 |
| 教师 `TEACHER` | 查看授课课程和学生名单，录入/修改所负责课程成绩，查询教学相关数据 |
| 教务管理员 `ACADEMIC_ADMIN` | 维护学籍、开课和容量，执行成绩复核与学业审查 |
| 系统管理员 `ADMIN` | 用户授权和注销、全部业务维护、审计和验收 |
| 图书管理员 `LIBRARIAN` | 图书目录、库存、借阅规则和借还记录 |
| 商店管理员 `STORE_MANAGER` | 商品、价格、库存和订单状态 |

### 1.5 当前成员与模块对应关系

| 学号 | 姓名 | 角色 | 模块 |
|---|---|---|---|
| `_09024429` | 何锦恒 | 组长 | 用户管理、公共协议、总控与集成 |
| `_09024326` | 时伟博 | 组员 | 选课系统 |
| `_09024417` | 欧阳挥骏 | 组员 | 商店 |
| `61524814` | 卢嘉鸣 | 组员 | 图书馆 |
| `_09024428` | 王峥荣 | 组员 | 学生学籍管理 |

## 2. 系统分析

### 2.1 可行性分析

- 技术可行：JDK 8、Socket、对象流、Swing、Access 均符合课程环境；Maven 多模块便于协作。
- 组织可行：五个业务模块可以按负责人并行，公共协议和集成由组长控制。
- 运行可行：服务器集中管理数据库连接和权限，客户端只负责界面和请求，不直接访问数据库。
- 验收可行：系统提供演示账号、测试数据、两个 JAR 和部署步骤，可按课程清单现场验证。

### 2.2 设计原则

1. 客户端不直接连接数据库，所有业务请求经过服务器。
2. 业务接口与实现分离，各模块通过 `ServiceResult<T>` 返回结果。
3. `common` 只放客户端和服务器共享的可序列化类型，禁止反向依赖业务实现。
4. 权限在服务器端再次检查，不能只依赖客户端隐藏按钮。
5. 每张业务表有主键；跨表关系使用外键；账号、课程、商品等业务编号使用唯一约束。
6. 先完成可测试的接口和内存实现，再替换 Access Repository，降低联调风险。

### 2.3 角色权限基线

系统采用“角色 → 权限编码 → 数据范围”三级检查。学生和教师进入商店后仍保持原系统角色，只通过 `STORE_PURCHASE` 获得买家能力；商店管理员不具有任何课程权限。完整矩阵、权限编码及服务端强制规则见 [`PERMISSIONS.md`](PERMISSIONS.md)。

同一账号同一时间只允许一个活动会话，重复登录由服务器返回 `CONFLICT`。客户端隐藏无权限入口仅作为界面优化，不能替代服务器端授权。

系统不提供面向未登录用户的公开自助注册入口。学生、教师和各类管理员账号由系统管理员在用户管理模块中创建、批量导入或由数据库初始化脚本预置，并发放初始密码。账号创建只解决“能否登录系统”和“具有什么角色权限”的问题；学生档案、教师档案、历史成绩和学业审查数据仍由学籍/教务模块维护或由演示数据导入。推荐流程是先建立学生/教师档案，再由管理员通过 `student_id` / `teacher_id` 把档案绑定到 `user_id`。首修、重修、成绩、是否通过和获得学分保存在课程结果表中；学业审查根据这些历史记录统计生成。若账号尚未关联学籍或教师档案，相关页面应提示联系管理员维护。

## 3. 系统总体结构

### 3.1 项目级层级

```text
vCampus
├── common                  # Message、状态码、角色、共享实体/载荷
├── client                  # Swing 界面、客户端控制器、Socket 客户端
├── server                  # ServerSocket、线程池、会话、请求分发
├── user-management         # 管理员开户注册/注销/登录/登出/授权
├── student-management      # 学生、院系、班级和学籍
├── course-selection        # 课程、开课、选课、退选、成绩
├── library                 # 图书、库存、借阅、归还
└── store                   # 商品、库存、订单、购买
```

### 3.2 依赖方向

```text
Swing View/Controller (client)
          │ Message over ObjectStream
          ▼
ServerSocket → ClientHandler/ThreadPool → MessageRouter
                                      │
       ┌──────────────────────────────┼──────────────────────────────┐
       ▼                              ▼                              ▼
  Auth/Session                 Business Services                 Permission
       │                              │                              │
       └──────────────────────────────┼──────────────────────────────┘
                                      ▼
                             Repository/DAO
                                      ▼
                              Access: vCampus
```

### 3.3 客户端逻辑分层

```text
client.view              登录页、主界面、五个模块页面、提示框
client.controller        页面事件、输入校验、导航和状态刷新
client.service           面向界面的远程服务适配器
client.transport         SocketClient、ObjectOutputStream、ObjectInputStream
common                   Message、载荷、状态码、User、Session
```

客户端不得导入 JDBC/Access 类；页面不直接拼装 SQL。

### 3.4 服务器端逻辑分层

```text
server.bootstrap         ServerApplication、端口、关闭流程
server.network           ServerSocket、ClientHandler、线程池、对象流
server.protocol          MessageRouter、消息校验、错误映射
server.auth              SessionManager、UserManagementService、权限矩阵
业务模块 service         Student、Course、Library、Store 服务
业务模块 repository      DAO 接口、Access 实现、事务/库存约束
database                 vCampus.accdb、schema.sql、seed.sql
```

服务器端的调用顺序固定为：反序列化 → 消息类型和载荷校验 → 会话/权限校验 → 业务服务 → Repository → 封装 `Message` 响应。何锦恒负责公共网络入口和路由，各模块负责人负责自己业务服务和数据访问实现。

## 4. 完整系统功能

### 4.1 用户管理模块（组长）

| 功能 | 学生/教师 | 管理员 | 规则 |
|---|---:|---:|---|
| 开立账号 | 否 | 是 | 账号唯一；密码 6-16 位；由管理员创建学生、教师或管理账号，并同步维护对应档案 |
| 登录 | 是 | 是 | 成功创建会话令牌；失败不泄露账号是否存在 |
| 登出 | 是 | 是 | 令牌失效；重复登出返回未授权 |
| 注销账号 | 当前账号 | 任意账号 | 当前用户需确认；管理员可注销其他账号；保留审计记录 |
| 授权检查 | 被动使用 | 管理权限 | 服务器端按角色和权限编码检查 |
| 用户查询/维护 | 查看本人 | 增删改查 | 管理员可启用/禁用账号、重置密码 |

核心接口：`UserManagementService.register`、`unregister`、`login`、`currentSession`、`logout`、`authorize`。其中 `register` 表示管理员端开户注册能力，不作为登录页公开自助入口。

### 4.2 学生学籍管理模块

| 功能 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|
| 查看本人学籍 | 是 | 否 | 是 |
| 修改本人联系方式 | 是 | 否 | 是 |
| 查询学生/班级 | 按授权 | 授课范围 | 是 |
| 管理院系、班级 | 否 | 否 | 是 |
| 学籍状态维护 | 否 | 否 | 是 |
| 学籍统计 | 否 | 授权范围 | 是 |

学生状态至少包括 `ENROLLED`、`SUSPENDED`、`GRADUATED`、`WITHDRAWN`。退学、毕业等状态变化必须阻止不符合规则的选课操作。

### 4.3 选课系统模块

| 功能 | 学生 | 教师 | 管理员 |
|---|---:|---:|---:|
| 查询开放课程 | 是 | 是 | 是 |
| 选课 | 是 | 否 | 可代办 |
| 退选 | 是 | 否 | 可代办 |
| 查看本人课程/成绩 | 是 | 否 | 是 |
| 查看授课学生 | 否 | 是 | 是 |
| 录入/修改成绩 | 否 | 授课课程 | 是 |
| 选课统计 | 否 | 授权范围 | 是 |

业务约束：课程开放时段、容量上限、重复选课、先修课程、时间冲突、学生学籍状态和退选截止时间必须在服务器端检查。

### 4.4 图书馆模块

| 功能 | 学生 | 教师 | 图书管理员 |
|---|---:|---:|---:|
| 按书名/作者/分类查询 | 是 | 是 | 是 |
| 查看可借库存 | 是 | 是 | 是 |
| 借书 | 是 | 是 | 可代办 |
| 还书 | 是 | 是 | 可代办 |
| 查看借阅记录 | 本人 | 本人 | 全部 |
| 图书目录/库存维护 | 否 | 否 | 是 |
| 逾期和罚金处理 | 查看本人 | 查看本人 | 是 |

业务约束：库存大于 0、同一本书不可重复借阅、达到借阅上限不可借、归还只能由当前借阅人或管理员完成。

### 4.5 商店模块

| 功能 | 学生 | 商店管理员 | 管理员 |
|---|---:|---:|---:|
| 浏览商品 | 是 | 是 | 是 |
| 查询库存/价格 | 是 | 是 | 是 |
| 购买商品 | 是 | 否 | 可代办 |
| 查看本人订单 | 是 | 否 | 是 |
| 商品维护 | 否 | 是 | 是 |
| 库存调整 | 否 | 是 | 是 |
| 订单发货/取消 | 查看状态 | 是 | 是 |

业务约束：购买数量为正、库存充足、订单和订单明细在同一事务内写入，库存扣减不能出现负数。

## 5. 关键业务流程

### 5.1 登录流程

```text
用户输入账号/密码
 → ClientController 校验非空和长度
 → Message(LOGIN, UserCredentials)
 → Server 校验消息和账号状态
 → PasswordHasher 校验密码
 → SessionManager 创建 token
 → 返回 Message(OK, Session)
 → 客户端保存当前会话并显示主界面
```

失败时统一返回 `UNAUTHORIZED` 或 `BAD_REQUEST`，不返回密码、哈希、堆栈和数据库细节。

### 5.2 选课流程

```text
查询课程 → 服务器返回开放课程
选择课程 → 校验 token、角色、学籍、容量、时间冲突、重复选课
         → 事务写入 tblCourseSelection
         → 返回成功/具体失败原因
```

### 5.3 借书流程

```text
查询图书 → 检查库存和借阅上限 → 写入 tblBorrowRecord
         → 原子扣减 tblBook.available_quantity
         → 返回借阅编号和应还日期
```

### 5.4 购买流程

```text
浏览商品 → 提交商品编号和数量 → 校验身份/数量/库存
         → 创建 tblOrder 和 tblOrderItem
         → 原子扣减库存 → 返回订单编号和状态
```

## 6. 公共通信协议 Message

### 6.1 消息字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `requestId` | `String` | 请求唯一编号，用于日志和响应匹配 |
| `type` | `MessageType` | 请求或响应对应的业务类型 |
| `statusCode` | `StatusCode` | 请求默认 `OK`；响应表示处理结果 |
| `sender` | `String` | 用户编号或服务器标识；不作为权限依据 |
| `payload` | `Serializable` | 业务载荷，如 `UserCredentials`、`Course` |

### 6.2 消息类型

| 类型 | 载荷 | 权限 |
|---|---|---|
| `REGISTER` | `UserCredentials` 或管理员开户注册命令 | 管理员 |
| `UNREGISTER` | `UserCommand` | 当前用户/管理员 |
| `LOGIN` | `UserCredentials` | 未登录 |
| `LOGOUT` | `String token` | 已登录 |
| `AUTHORIZE` | `AuthorizationRequest` | 已登录 |
| `STUDENT_QUERY/UPDATE` | 学生查询/更新请求 | 按角色和数据范围 |
| `COURSE_QUERY/SELECT/DROP` | 课程/选课请求 | `COURSE_READ` 或 `COURSE_SELECT`，并校验本人/授课范围 |
| `COURSE_CREATE/UPDATE/DEACTIVATE` | 课程维护请求 | `COURSE_MANAGE` |
| `LIBRARY_QUERY/BORROW/RETURN` | 图书/借还请求 | 按借阅规则 |
| `STORE_QUERY` | `StoreQueryCommand(token)` | `STORE_READ`，服务端按 token 校验 |
| `STORE_PURCHASE` | `StorePurchaseCommand(token, productId, quantity)` | `STORE_PURCHASE`，服务端按 token 取得 userId |
| `STORE_ORDER_QUERY` | `StoreOrderQueryCommand(token)` | `STORE_READ`，仅返回当前用户订单 |

### 6.3 状态码

`OK`、`BAD_REQUEST`、`UNAUTHORIZED`、`FORBIDDEN`、`NOT_FOUND`、`CONFLICT`、`SERVER_ERROR`。错误响应只能返回用户可理解的业务信息，详细异常写服务器日志。

## 7. 实体类与数据库设计

### 7.1 命名规范

- 表名：`tbl` + PascalCase，例如 `tblUser`、`tblCourseSelection`。
- 主键：`<entity>_id`，字符串编号由业务生成；关联表使用组合唯一约束。
- 外键字段与被引用主键同名后加 `_id`。
- 布尔值使用 `BIT`；日期时间使用 Access 的 `DATETIME`；金额使用 `DECIMAL(12,2)`。
- 密码字段只保存哈希值，不保存明文密码。

### 7.2 核心表

| 表 | 关键字段 | 约束/关系 |
|---|---|---|
| `tblUser` | `user_id`、`password_hash`、`display_name`、`role_code`、`active` | `user_id` PK、角色白名单、账号唯一 |
| `tblDepartment` | `department_id`、`department_name` | 编号 PK、名称唯一 |
| `tblClass` | `class_id`、`department_id`、`class_name`、`major_name` | Department 1:N Class |
| `tblStudent` | `student_id`、`user_id`、`class_id`、`status`、`email` | User/Class 外键；学号唯一 |
| `tblTeacher` | `teacher_id`、`user_id`、`department_id`、`title` | User/Department 外键 |
| `tblCourse` | `course_id`、`course_name`、`credits`、`capacity`、`status` | 课程编号唯一；学分和容量非负 |
| `tblCourseOffering` | `offering_id`、`course_id`、`teacher_id`、`semester`、`start_time`、`end_time` | Course/Teacher 外键 |
| `tblCourseSelection` | `selection_id`、`offering_id`、`student_id`、`status`、`score` | Student/Offering 外键；二者组合唯一 |
| `tblBook` | `book_id`、`isbn`、`title`、`author`、`total_quantity`、`available_quantity` | 库存不为负；ISBN 可唯一 |
| `tblBorrowRecord` | `borrow_id`、`book_id`、`user_id`、`borrowed_at`、`due_at`、`returned_at`、`status` | Book/User 外键；借阅状态约束 |
| `tblProduct` | `product_id`、`product_name`、`price`、`stock`、`active` | 价格、库存非负 |
| `tblOrder` | `order_id`、`buyer_id`、`total_amount`、`status`、`created_at` | Buyer/User 外键 |
| `tblOrderItem` | `order_item_id`、`order_id`、`product_id`、`quantity`、`unit_price` | Order/Product 外键；数量正数 |
| `tblAuditLog` | `log_id`、`user_id`、`action`、`target_type`、`target_id`、`created_at` | 记录注销、授权、库存等敏感操作 |

### 7.3 E-R 关系

```text
Department 1 ── N Class ── N Student ── N CourseOffering ── 1 Course
     │             │              │              │
     └── N Teacher ┘              └── N CourseSelection

User 1 ── 0..1 Student / Teacher
User 1 ── N BorrowRecord ── N Book
User 1 ── N Order ── N OrderItem ── N Product
```

正式 Word 版应根据此关系绘制 E-R 图，并在图下列出每个实体的字段和约束。

## 8. 接口设计

| 模块 | 接口 | 核心方法 |
|---|---|---|
| 用户 | `UserManagementService` | `register`、`unregister`、`login`、`logout`、`authorize` |
| 学籍 | `StudentManagementService` | `findById`、`findByClass`、`save` |
| 选课 | `CourseSelectionService` | `listCourses`、`select`、`drop`、`selectedCourses` |
| 图书馆 | `LibraryService` | `search`、`borrow`、`returnBook` |
| 商店 | `StoreService` | `listProducts`、`purchase`、`findOrdersByUserId` |

每个接口实现均遵循：输入校验 → 权限检查 → 业务规则 → Repository → `ServiceResult<T>`。客户端通过远程适配器调用，不直接引用数据库实现。

## 9. 界面设计原型

### 9.1 页面层级

```text
LoginFrame
└── MainFrame
    ├── UserPanel
    ├── StudentPanel
    ├── CourseSelectionPanel
    ├── LibraryPanel
    └── StorePanel
```

### 9.2 页面要求

| 页面 | 必须包含 |
|---|---|
| 登录页 | 用户编号、密码、登录、忘记密码/联系管理员、错误提示 |
| 主界面 | 当前用户、角色、退出、模块导航、连接状态 |
| 用户管理 | 创建账号、注销、重置密码、登录状态、权限反馈；管理员显示账号维护 |
| 学籍页 | 学号、姓名、院系、班级、状态、联系方式、查询/保存 |
| 选课页 | 课程筛选、容量、时间、选课/退选、已选课程和成绩 |
| 图书馆页 | 关键词查询、库存、借阅、归还、借阅记录和应还日期 |
| 商店页 | 商品、价格、库存、数量、购买、订单状态 |
| 管理页 | 按角色显示用户、课程、图书、商品、统计操作 |

## 10. 测试和验收

### 10.1 测试层次

1. 单元测试：实体校验、Service 规则、权限判断、库存和选课约束。
2. 协议测试：`Message` 对象流序列化/反序列化、载荷类型校验、状态码映射。
3. 集成测试：客户端 → Socket → 服务器 → Service → Access。
4. 验收测试：按课程清单部署、启动、正常流程、异常流程和答辩演示。

### 10.2 最低验收用例

| 编号 | 用例 | 预期 |
|---|---|---|
| U01 | 管理员创建新账号 | 成功；重复账号返回 `CONFLICT`；学生/教师账号创建后可查到对应档案 |
| U02 | 错误密码登录 | `UNAUTHORIZED`，不泄露敏感信息 |
| U03 | 登出后访问业务 | `UNAUTHORIZED` |
| U04 | 学生调用管理员接口 | `FORBIDDEN` |
| U05 | 重复选课/满员选课 | 拒绝且数据不改变 |
| U06 | 无库存借书/购买 | 拒绝且库存不变 |
| U07 | 两个客户端同时连接 | 两个会话均可独立处理 |
| U08 | 客户端异常断开 | 服务器释放线程/资源，不影响其他客户端 |
| U09 | 非法消息载荷 | `BAD_REQUEST`，服务器不崩溃 |
| U10 | Access 数据库重启后运行 | 数据可读取，部署步骤可复现 |

## 11. 与课程提交材料的对应关系

| 课程材料 | 本仓库对应内容 |
|---|---|
| 软件设计说明书 | 本文件，最终转为 DEMO Word 版 |
| 小组进度计划报告 | `docs/PROJECT_PLAN.md`，每周补充实际状态 |
| 系统使用说明 | 根据最终 UI 和部署脚本编写，必须覆盖安装、启动、登录和五个模块操作 |
| 源代码帮助文档 | 对 Java 源码执行 JavaDoc 生成 HTML |
| 小组项目报告 | 根据实际开发过程、关键技术和问题补写 |
| 个人项目小结 | 每位成员描述实际分工、贡献、问题和收获 |
| 组内/组间互评 | 使用课程提供表格，按时提交 |

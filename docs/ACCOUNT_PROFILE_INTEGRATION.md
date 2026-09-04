# 账号与学生/教师档案绑定对接规范

本文档用于指导用户管理、学籍管理、选课、图书馆和商店模块围绕同一套身份模型对接。核心原则是：登录身份统一使用 `user_id`，学生和教师档案分别使用 `student_id`、`teacher_id` 保存业务身份，二者通过绑定关系关联。

## 1. 总体流程

```text
先有学生/教师档案
-> 管理员创建或导入账号
-> 通过 student_id / teacher_id 绑定 user_id
-> 学籍、选课、图书馆、商店等模块统一使用 user_id 作为登录身份
```

其中：

- `user_id`：系统登录账号，也是服务器识别当前登录用户的统一身份。
- `student_id`：学生学号，只表示学籍档案编号。
- `teacher_id`：教师工号，只表示教师档案编号。

客户端不直接连接 Access 数据库，也不直接相信界面中输入的 `student_id`、`teacher_id` 或 `user_id`。所有需要权限的数据操作都先由服务器根据 `token` 找到当前 `user_id`，再按角色和绑定关系判断能访问哪些数据。

新建或导入 `STUDENT` / `TEACHER` 账号时，服务端必须先验证对应档案存在且未绑定；账号写入后绑定失败必须补偿删除账号。已绑定学生/教师账号不能直接改成另一类角色，切换到学生/教师角色也必须已有对应档案。

## 2. 各模块职责边界

| 模块 | 负责内容 | 不负责内容 | 对外提供/依赖 |
|---|---|---|---|
| 用户管理 | 账号创建、账号批量导入、登录登出、会话、角色权限、账号启停、导入人和审计记录、账号与档案绑定入口 | 不维护学生专业、班级、历史成绩、教师授课信息等业务字段 | 提供当前 `user_id`、角色、权限判断；绑定时写入或委托写入 `tblStudent.user_id` / `tblTeacher.user_id` |
| 学生学籍管理 | 学生档案、学号、姓名、院系、专业、班级、学籍状态、联系方式、学业审查基础数据 | 不创建登录账号、不判断登录密码 | 通过 `user_id` 查本人学生档案；通过 `student_id` 维护学生业务数据 |
| 教师档案/教务数据 | 教师工号、姓名、院系、职称、任课关系 | 不创建登录账号、不保存教师登录密码 | 通过 `user_id` 查教师档案；通过 `teacher_id` 关联开课、成绩录入等数据 |
| 选课系统 | 课程查询、学生选课退课、已选课程、开课与容量、首修/重修、成绩录入 | 不直接维护账号密码，不信任客户端传入的学生身份 | 学生操作：`token -> user_id -> student_id`；教师操作：`token -> user_id -> teacher_id` |
| 图书馆 | 图书查询、借阅、归还、借阅记录 | 不维护学生学籍或教师档案 | 借阅身份统一保存 `user_id`；展示姓名时可通过用户或档案模块查询 |
| 商店 | 商品查询、购买、库存、订单记录 | 不维护学号或教师工号 | 订单购买人统一保存 `user_id`，学生和教师在商店中都只是当前登录用户 |
| 服务器公共层 | 消息分发、权限校验、会话解析、数据范围判断 | 不把权限判断下放给客户端页面 | 各 Handler 先校验 token，再调用对应 Service |
| 客户端页面 | 展示入口、收集表单、显示结果 | 不直接拼 SQL、不直接访问数据库、不只靠隐藏按钮做权限控制 | 所有业务请求携带 `token`，由服务器判断真实身份和权限 |

## 3. Access 表关系约定

现有数据库设计已经预留账号与档案绑定字段：

| 表 | 主键 | 绑定字段 | 约束建议 |
|---|---|---|---|
| `tblUser` | `user_id` | 无 | `user_id` 唯一；保存密码哈希、显示名、角色、状态、创建/导入信息 |
| `tblStudent` | `student_id` | `user_id` | `user_id` 可空且唯一；允许先导入学生档案，后绑定登录账号 |
| `tblTeacher` | `teacher_id` | `user_id` | `user_id` 可空且唯一；允许先导入教师档案，后绑定登录账号 |

绑定规则：

1. 学生账号只能绑定到一条学生档案，教师账号只能绑定到一条教师档案。
2. 同一条学生/教师档案最多绑定一个登录账号。
3. 未绑定账号的学生/教师档案可以存在，用于先导入基础档案。
4. 历史遗留的未绑定学生/教师账号可以登录，但进入学籍、选课、授课等页面时应提示“暂无对应档案，请联系管理员维护”；新建或导入流程不得产生这类账号。
5. 批量导入账号不直接生成课程历史、学业审查或授课关系，避免用户模块替代其它业务模块。

如后续需要新增绑定命令、索引或字段，应使用单独迁移脚本，并把表结构变更和演示数据导入分开提交。

## 4. 推荐接口

### 4.1 用户管理模块

用户管理继续负责账号层能力：

```text
register(token, credentials)
importUsers(token, rows)
login(credentials)
logout(token)
unregister(token, targetUserId)
authorize(token, permission)
currentSession(token)
```

账号与档案绑定建议后续补充为独立能力，避免混在开户注册和批量导入里：

```text
bindStudentProfile(token, userId, studentId)
bindTeacherProfile(token, userId, teacherId)
unbindStudentProfile(token, userId, studentId)
unbindTeacherProfile(token, userId, teacherId)
```

绑定操作要求 `USER_MANAGE` 或经团队确认的教务管理权限。绑定前服务器必须检查：

- 当前管理员 token 有权限；
- `userId` 对应账号存在且启用；
- 学生账号角色为 `STUDENT`，教师账号角色为 `TEACHER`；
- `studentId` 或 `teacherId` 对应档案存在；
- 目标档案没有绑定其它账号；
- 目标账号没有绑定其它同类型档案。

### 4.2 学籍管理模块

学籍模块建议提供以下能力：

```text
findStudentById(token, studentId)
findStudentByUserId(token, userId)
findMyStudentProfile(token)
saveStudent(token, studentRecord)
bindStudentAccount(token, studentId, userId)
```

在 Java 业务层，`StudentManagementService.findMyStudentProfile(userId)` 是服务器完成 Token 解析后的兼容别名，内部复用 `findByUserId(userId)`，不会接受客户端自行传入的身份编号。

学生本人查询时，客户端只传 `token`；服务器解析出 `user_id` 后查询 `tblStudent.user_id`，不要让学生在界面上手工输入学号来决定查询范围。

教务管理员维护学生档案时，可以按 `student_id` 查询和保存，但服务器仍要先检查教务权限。

### 4.3 教师档案与选课模块

教师相关接口建议至少支持：

```text
findTeacherById(token, teacherId)
findTeacherByUserId(token, userId)
findMyTeacherProfile(token)
bindTeacherAccount(token, teacherId, userId)
```

任课教师录入成绩或查看授课名单时，服务器流程应为：

```text
token -> 当前 user_id
-> 查询 tblTeacher.user_id 得到 teacher_id
-> 检查该 teacher_id 是否是对应教学班任课教师
-> 允许查看名单或录入成绩
```

学生选课或退课时，服务器流程应为：

```text
token -> 当前 user_id
-> 查询 tblStudent.user_id 得到 student_id
-> 检查学生学籍状态、选课轮次、课程容量和首修/重修规则
-> 写入或更新选课记录
```

如果旧命令对象中仍包含 `studentId`，服务器不能直接信任它。过渡期可以用它做一致性校验；新接口应尽量改为由服务器根据 `token` 推导本人 `student_id`。

## 5. 各子系统对接规则

### 学籍页面

- 学生进入页面：只显示本人档案，数据来源为 `token -> user_id -> student_id`。
- 教务管理员进入页面：可按学号、班级、专业等条件查询和维护学生档案。
- 未绑定学生档案：显示友好提示，不凭账号信息自动生成学籍记录。

### 选课页面

- 学生选课、退课、查看已选课程：客户端只需要携带 `token` 和课程/教学班编号。
- 服务端根据 `user_id` 查到 `student_id` 后再处理选课业务。
- 首修/重修判断使用 `student_id` 查询课程历史，不使用 `user_id` 直接判断学业记录。

### 教师成绩页面

- 教师进入页面：根据 `token -> user_id -> teacher_id` 查询本人授课教学班。
- 教师只能录入本人授课课程的成绩。
- 教务管理员可复核成绩，但仍需记录操作人 `user_id`。

### 图书馆页面

- 借书、还书、借阅记录以 `user_id` 作为读者身份。
- 学生和教师都可以借阅，图书馆模块不需要直接区分 `student_id` 和 `teacher_id`。
- 如页面需要展示班级或院系，再通过学籍/教师档案查询补充信息。

### 商店页面

- 购买人和订单查询统一使用 `user_id`。
- 学生和教师在商店中都是普通购买用户，不需要传学号或教师工号。
- 商店管理员维护商品和库存时，以管理员 `user_id` 记录操作人。

## 6. 请求与错误处理约定

| 场景 | 建议状态 |
|---|---|
| token 无效或未登录 | `UNAUTHORIZED` |
| 已登录但无权限 | `FORBIDDEN` |
| 学生/教师档案不存在 | `NOT_FOUND` |
| 当前账号尚未绑定档案 | `NOT_FOUND` 或业务层返回明确提示 |
| 账号角色与档案类型不匹配 | `BAD_REQUEST` |
| 档案已绑定其它账号 | `CONFLICT` |
| 账号已绑定其它同类型档案 | `CONFLICT` |

错误提示要让操作者知道如何处理，例如“该学生档案尚未绑定登录账号”“当前教师账号未绑定教师档案”“该学号已绑定其它账号”。不要把数据库异常、SQL 语句或密码哈希返回给客户端。

## 7. 后续实验拆分

| 实验 | 范围 | 验收点 |
|---|---|---|
| E4.1 绑定方案确认 | 文档、接口、表关系、模块职责 | 同学能明确 `user_id`、`student_id`、`teacher_id` 的区别 |
| E4.2 档案查询接口 | 学籍/教师档案按 `user_id` 查询 | 学生和教师登录后能定位自己的档案 |
| E4.3 账号绑定操作 | 管理员绑定/解绑学生或教师档案 | 绑定冲突和角色不匹配能正确提示 |
| E4.4 选课/成绩接入 | 选课和教师成绩使用绑定关系 | 不再信任客户端手填身份 |
| E4.5 全模块身份联调 | 学籍、选课、图书馆、商店统一会话身份 | 同一账号在各模块身份一致，权限拒绝路径清楚 |

## 8. 合并前检查清单

- [ ] 是否仍然把 `user_id` 作为登录身份？
- [ ] 是否把 `student_id`、`teacher_id` 只用于业务档案和业务表关联？
- [ ] 学生本人操作是否由服务器根据 `token` 查出 `student_id`？
- [ ] 教师本人操作是否由服务器根据 `token` 查出 `teacher_id`？
- [ ] 商店和图书馆是否继续使用 `user_id` 作为当前用户身份？
- [ ] 是否在服务器端做了角色权限和数据范围校验？
- [ ] 是否避免客户端直接拼接 SQL 或直接访问 Access 数据库？
- [ ] 如果改了表结构，是否同步更新 `database/schema.sql`、迁移脚本和数据库说明？

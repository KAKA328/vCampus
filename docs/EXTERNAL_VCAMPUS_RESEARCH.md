# 外部 vCampus 项目调研与账号-学籍设计决策

更新时间：2026-08-27  
范围：仅记录 GitHub 上其他 SEU / vCampus 相关项目中与“账号注册/开户注册、学籍信息绑定、学业审查、子系统联动”有关的设计线索，避免后续只依赖聊天记录。

## 1. 已核对来源

| 项目 | 创建/最近推送 | 相关线索 | 对本项目的启发 |
|---|---|---|---|
| [Serissia/vCampusSEU](https://github.com/Serissia/vCampusSEU) | 2026-08-25 创建，2026-08-26 推送 | Java C/S；存在 `UserVO`、`UserDao`、`GradeVO`、课程/成绩/选课 DAO 和 Service。`UserVO` 使用一卡通号/学工号作为统一身份，成绩对象按学生账号关联课程成绩。 | 可以把登录账号作为进入系统的身份入口，但课程成绩、选课、学籍仍应在业务表中维护。 |
| [nineloong-bot/vCampus](https://github.com/nineloong-bot/vCampus) | 2026-08-24 创建，2026-08-27 推送 | 用户模块设计写明用户模块负责登录、密码、账户状态、审计，并为学籍模块提供学生账户内部创建接口；学生账户由管理员在新生录取时创建。学籍模块设计强调一次录取原子完成编号分配、学生用户创建、学生档案创建和审计。 | 最近项目里比较清晰的方案是“用户账号”和“学籍档案”分离，但在管理员录取/开户注册流程中绑定。 |
| [wusheng121/Vcampus](https://github.com/wusheng121/Vcampus) | 2025-09-01 创建，2026-08-17 推送 | Java 项目；存在 `User`、`Student`、`StudentPersonal`、`StudentPersonalAudit`、用户管理和学籍管理界面。`Student` 含 `userId`，学生个人信息变更有审核模型。 | 学籍资料不应混在登录账号里；学生档案通过 `userId` 与账号关联，个人资料变更可进入审核流程。 |
| [weiweishaoshaohuang/VCampus](https://github.com/weiweishaoshaohuang/VCampus) | 2026-01-20 创建/推送 | Java Swing + Socket + 分层服务；说明覆盖选课、学籍、图书馆、校园商店，文件结构中有 `UserManagePanel`、`StudentInfoPanel`、`StudentMgrDialog`。 | 管理端用户维护页和学籍管理页可以分开呈现，职责更清楚。 |
| [zeroffa233/seu-vcampus](https://github.com/zeroffa233/seu-vcampus) | 2025-08-25 创建，2025-09-22 推送；2026-08-25 更新仓库元信息 | Java / JavaFX；存在 `AuthClient`、`StudentStatusClient`、`StudentStatusController`、课程与学籍相关页面。 | 身份认证、学籍状态、课程选择可按不同客户端网关/控制器拆分，但由统一会话贯通。 |
| [XuanyuChen-SEU/vcampus](https://github.com/XuanyuChen-SEU/vcampus) | 2025-08-29 创建，2025-09-20 推送；2026-08-24 更新仓库元信息 | Java 项目；生成文档中可见登录、用户管理、学生管理、课程管理等控制器。 | 管理端账号维护、学生管理、课程管理宜各自成页，通过权限和账号 ID 联动。 |
| [JinBridger/SEU-SummerSchool-VCampus](https://github.com/JinBridger/SEU-SummerSchool-VCampus) | 2023-08-22 创建，2023-09-15 推送；较旧 | Kotlin + Java；存在 `AddUserSubscene`、`ModifyUserSubscene`、学籍展示/修改、成绩列表等页面。添加账户页面由管理员维护一卡通号、初始密码和基本信息。 | 旧项目也常把“添加账户”放在管理员子场景中，而不是面向所有未登录用户开放。 |

## 2. 设计结论

### 2.1 账号注册还是发放注册密码

本项目采用“管理员开户注册 + 发放初始密码”，不提供登录页公开自助注册。

- 登录页只负责登录。
- 系统管理员在“用户管理”中创建学生、教师或管理账号。
- 创建成功后，管理员把初始密码发给对应用户。
- 数据库初始化脚本预置一组演示账号，便于联调和现场验收。
- 服务端 `REGISTER` 消息必须携带管理员会话令牌；普通学生、教师和未登录用户不能调用开户注册。

### 2.2 学籍子系统如何保存和读取课程/学分信息

账号与学籍分离，但通过 `user_id` 绑定：

- `tblUser`：保存登录账号、密码哈希、显示名、角色和账号状态。
- `tblStudent`：保存学生学籍档案，使用 `user_id` 关联登录账号，使用 `student_id` 作为学籍主键。
- `tblTeacher`：保存教师档案，使用 `user_id` 关联登录账号。
- `tblCourseResult`：保存学生历史课程结果，包括首修/重修、成绩、是否通过和获得学分。
- `tblAcademicReview`：保存一次学业审查结果，包括已获学分、未通过课程数、重修课程数和是否满足阶段要求。

学生登录后查看本人学籍时，服务端应按以下路径读取：

```text
Session.token
 → 当前登录 user_id
 → tblStudent.user_id = user_id
 → student_id
 → tblStudent 基本学籍
 → tblCourseResult 历史课程和学分
 → tblAcademicReview 或实时审查服务生成学业审查结果
```

也就是说，客户端不应让学生自己传入任意 `student_id` 来查学籍；学生端只能查“当前会话绑定的本人学籍”。教务管理员可以按学号、班级、院系等条件查询和维护更大范围的数据。

### 2.3 子系统功能和联动

| 子系统 | 本项目职责 | 主要联动 |
|---|---|---|
| 用户管理 | 管理员开户注册、登录、登出、注销、授权、重置密码、审计 | 为其他模块提供当前用户、角色、权限和 `user_id` |
| 学籍管理 | 学籍档案、联系方式、班级/专业、学籍状态、学业审查 | 通过 `user_id` 绑定账号；通过 `student_id` 连接课程结果；向选课模块提供学籍状态 |
| 选课系统 | 课程查询、选课、退课、已选课程查询、容量/冲突检查 | 选课前校验学生身份和学籍状态；课程结果进入学业审查 |
| 图书馆 | 图书查询、借阅、归还、借阅记录 | 借阅记录按 `user_id` 关联账号，权限来自用户模块 |
| 商店 | 商品查询、购买、库存、订单记录 | 订单按 `user_id` 关联账号，库存变更由商店管理员维护 |

## 3. 本项目当前落地状态

已完成：

- 文档口径改为“无公开自助注册；管理员开户注册”。
- 登录页移除公开注册入口。
- 管理员“用户管理”页面提供创建账号入口。
- 服务端 `REGISTER` 改为管理员权限操作，未登录或普通账号调用会被拒绝。
- `database/seed.sql` 预置测试账号，初始密码统一为 `Demo123`。
- 学籍相关表已规划 `tblStudent`、`tblTeacher`、`tblCourseResult`、`tblAcademicReview`，并写入演示数据。
- `student-management` 已有 `StudentRecord`、`CourseHistoryRecord`、`AcademicReview` 和内存学业审查服务。

仍需后续接入：

- 管理员创建或导入学生账号后，通过 `student_id` 绑定已有或新建的 `tblStudent` 档案；完整学籍字段仍由学籍模块维护。
- 管理员创建或导入教师账号后，通过 `teacher_id` 绑定已有或新建的 `tblTeacher` 档案；授课关系仍由教务/选课模块维护。
- 学籍页面接入“当前会话 user_id → tblStudent → 课程历史/审查”的本人查询接口。
- 教务管理员页面接入学籍维护、课程历史导入和学业审查保存。

## 4. 初始测试账号

初始化脚本 `database/seed.sql` 中预置以下演示账号，初始密码均为 `Demo123`：

| 账号 | 角色 | 用途 |
|---|---|---|
| `demo_admin` | `ADMIN` | 系统管理员，测试开户注册、注销、权限管理 |
| `demo_academic_admin` | `ACADEMIC_ADMIN` | 教务管理员，测试学籍和选课管理 |
| `demo_librarian` | `LIBRARIAN` | 图书管理员，测试图书管理 |
| `demo_store_manager` | `STORE_MANAGER` | 商店管理员，测试商品和库存管理 |
| `demo_student` | `STUDENT` | 学生，测试本人学籍、选课、图书和商店 |
| `demo_teacher` | `TEACHER` | 教师，测试授课/成绩/学籍查询入口 |


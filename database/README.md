# 数据库目录

最终提交时放入机房兼容版本的 `vCampus.accdb`，并补充 `schema.sql` 与 `seed.sql`。本项目当前只支持**全新数据库**：创建或重置数据库时必须按最新版 `schema.sql` 建表，再按需执行 `seed.sql` 导入演示数据；不提供旧 `.accdb` 的迁移、修复或兼容逻辑。

当前服务器使用 UCanAccess 4.0.4。该驱动不支持执行独立 `CREATE INDEX` 语句，因此 `schema.sql` 只保留主键和表内唯一约束来保证数据正确性；查询索引不是本项目演示环境的必要条件。

运行数据库统一使用 Access：服务器通过 `--db database/vCampus.accdb` 连接该文件，客户端不直接连接数据库。用户批量导入的外部源文件可以使用 `.xlsx`、`.csv` 或 `.tsv` 表格模板；这些文件只负责把账号清单读入系统，最终账号、导入人、导入时间和导入批次仍写入 `vCampus.accdb`。不建议把另一个 `.accdb/.mdb` 文件作为用户导入源，避免导入源表结构与系统运行数据库结构混淆。

## 用户模块表

- `tblUser`：用户账号表，保存登录账号、密码哈希、显示名、角色、启停状态。批量导入账号时额外记录 `created_by`、`created_at`、`import_batch_id`，用于追踪导入人、导入时间和批次。
- `tblAuditLog`：敏感用户操作审计表。批量导入每成功创建一个账号都会写入 `IMPORT_USER` 记录，`actor_user_id` 为导入管理员，`target_id` 为被创建账号。
- `tblPasswordResetApplication`：密码重置申请表。用户在登录前提交账号和新密码，系统只保存新密码哈希与待审批状态；管理员审批通过后将哈希写回 `tblUser.password_hash`，审批拒绝则只更新申请状态。

用户批量导入表格的推荐列名为：`账号`、`姓名`、`初始密码`、`角色`。英文模板也可使用 `userId`、`displayName`、`password`、`roleCode`。角色值使用系统角色编码，例如 `STUDENT`、`TEACHER`、`ADMIN`、`ACADEMIC_ADMIN`、`LIBRARIAN`、`STORE_MANAGER`。

## 选课模块表

- `tblCourse`：课程目录，保存课程号、课程名称、学分和启用/停用状态；实际可选人数由 `tblCourseOffering` 的具体教学班容量决定。
- `tblCourseSelection`：学生选课记录，包含学生、教学班、选课轮次、选课身份、选课/退选时间和状态。已退选记录会保留，但不计入容量和名单。
- `tblActiveCourseSelection`：当前有效选课的唯一占用键，保证同一学生、同一教学班最多只有一条有效记录；退选时删除该占用键。
- `tblCourseOfferingCapacityUsage`：教学班三个容量池当前已占用人数。选课通过数据库条件更新预留名额，避免多人同时选课时超额。
- `tblSelectionRound`：教务人员维护的选课轮次，保存学期、首修/重修类型、起止时间和状态；同一学期每种轮次类型最多一条。
- `tblTrainingPlan`、`tblTrainingPlanCourse`：教务人员维护的培养方案及课程要求，按专业和入学年份确定学生首修阶段可见的课程。
- `tblCourseMeeting`：教学班的结构化上课时间，保存星期、起止节次和实际上课地点；同一教学班可有多条记录，用于时间冲突检测。

服务端会通过数据库唯一键阻止同一学生重复选择同一个教学班，并通过容量占用表原子预留名额；已退选记录不会占用容量。已选人数不存入 `tblCourse`，避免课程目录与教学班人数数据不一致。

使用 `--db` 启动时，选课服务会从 `tblStudent` 按登录 `user_id` 读取学生资料，从 `tblCourseResult` 计算待重修课程，并从 `tblSelectionRound` 确定当前学期。因此教务人员需要先维护学生档案、培养方案、教学班和选课轮次，学生才会看到可选择的教学班。`seed.sql` 不预置开放轮次；如需演示选课，应由教务管理员先创建并开放首修或重修轮次。

## 学籍审查规划表

账号由系统管理员创建、批量导入或由初始化脚本预置。学生/教师档案可以先由对应子系统导入或维护，再通过 `student_id` / `teacher_id` 绑定 `user_id`；注册/开户注册流程不负责生成学生历史成绩。学籍审查需要的数据由教务管理员维护或由演示数据导入：

- `tblStudent`：学生基础学籍信息，保存学号、姓名、院系、专业、班级、入学年份、学籍状态和联系方式，可通过 `user_id` 关联登录账号。
- `tblClass`：班级基础信息，保存班级名称、所属院系、专业和年级，学生档案通过 `class_id` 关联。
- `tblTeacher`：教师基础信息，保存教师编号、姓名、院系和职称，可通过 `user_id` 关联登录账号。
- `tblCourseOffering`：具体学期开课记录，保存课程、任课教师、学期、显示用上课时间、地点、必修/选修/跨专业容量和状态。重修学生保留重修身份，但占用必修容量。
- `tblCourseMeeting`：具体教学班的结构化上课时间，用于恢复并执行选课时间冲突检测。
- `tblCourseResult`：历史课程结果，保存学生每次首修/重修记录、成绩、是否通过和获得学分。
- `tblAcademicReview`：学业审查结果快照，保存累计学分、挂科门数、重修门数、是否满足毕业要求和审核人。

学籍服务的 `latestReview(studentId)` 读取该表最近一次快照；`review(studentId, requiredCredits)` 根据 `tblCourseResult` 实时计算，不会覆盖历史快照。快照中的通过课程数由课程结果按课程号去重计算。

## 商店模块表

- `tblProduct`：商品、库存、价格和分类；`active` 表示是否上架。
- `tblOrder`：订单记录，保存用户、商品快照、数量和金额。
- `tblBankAccount`：校园钱包账户，`user_id` 为主键，`balance_cents` 以「分」为单位存 `BIGINT NOT NULL`；余额扣减/入账由应用层补偿保证一致性，不依赖数据库事务。

查询、购物车和购买只处理 `tblProduct.active=1` 的商品。校园钱包余额字段 `balance_cents` 已包含在最新版 `schema.sql` 中；请使用全新数据库，不执行历史迁移脚本。

身份字段分工如下：`tblUser.user_id` 是登录身份；`tblStudent.student_id` 是学生学号；`tblTeacher.teacher_id` 是教师工号；`tblStudent.user_id` 和 `tblTeacher.user_id` 是档案与登录账号之间的一对一绑定字段，可为空但绑定后应保持唯一。如果账号尚未关联 `tblStudent` 或 `tblTeacher`，相关页面应提示“暂无对应档案，请联系管理员维护”；学业审查、课程历史和授课关系不能根据账号信息凭空生成。

学籍表也已包含在最新版 `schema.sql` 中。项目开发阶段统一以该文件作为全新数据库的唯一建表来源。

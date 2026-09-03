# 数据库目录

最终提交时放入机房兼容版本的 `vCampus.accdb`，并补充 `schema.sql` 与 `seed.sql`（如 Access 版本不支持某条 SQL，以实际建库结果为准）。

运行数据库统一使用 Access：服务器通过 `--db database/vCampus.accdb` 连接该文件，客户端不直接连接数据库。用户批量导入的外部源文件可以使用 `.xlsx`、`.csv` 或 `.tsv` 表格模板；这些文件只负责把账号清单读入系统，最终账号、导入人、导入时间和导入批次仍写入 `vCampus.accdb`。不建议把另一个 `.accdb/.mdb` 文件作为用户导入源，避免导入源表结构与系统运行数据库结构混淆。

## 用户模块表

- `tblUser`：用户账号表，保存登录账号、密码哈希、显示名、角色、启停状态。批量导入账号时额外记录 `created_by`、`created_at`、`import_batch_id`，用于追踪导入人、导入时间和批次。
- `tblAuditLog`：敏感用户操作审计表。批量导入每成功创建一个账号都会写入 `IMPORT_USER` 记录，`actor_user_id` 为导入管理员，`target_id` 为被创建账号。
- `tblPasswordResetApplication`：密码重置申请表。用户在登录前提交账号和新密码，系统只保存新密码哈希与待审批状态；管理员审批通过后将哈希写回 `tblUser.password_hash`，审批拒绝则只更新申请状态。

用户批量导入表格的推荐列名为：`账号`、`姓名`、`初始密码`、`角色`。英文模板也可使用 `userId`、`displayName`、`password`、`roleCode`。角色值使用系统角色编码，例如 `STUDENT`、`TEACHER`、`ADMIN`、`ACADEMIC_ADMIN`、`LIBRARIAN`、`STORE_MANAGER`。

## 选课模块表

- `tblCourse`：课程基本信息，包含课程号、课程名称、学分和课程容量。
- `tblCourseSelection`：学生选课记录，包含学生学号、课程号和选课时间。

`tblCourseSelection(student_id, course_id)` 使用唯一索引，保证同一学生不能重复选择同一门课程。已选人数不单独存入 `tblCourse`，而是在选课时统计选课记录，避免人数数据不一致。

## 学籍审查规划表

账号由系统管理员创建、批量导入或由初始化脚本预置。学生/教师档案可以先由对应子系统导入或维护，再通过 `student_id` / `teacher_id` 绑定 `user_id`；注册/开户注册流程不负责生成学生历史成绩。学籍审查需要的数据由教务管理员维护或由演示数据导入：

- `tblStudent`：学生基础学籍信息，保存学号、姓名、院系、专业、班级、入学年份、学籍状态和联系方式，可通过 `user_id` 关联登录账号。
- `tblTeacher`：教师基础信息，保存教师编号、姓名、院系和职称，可通过 `user_id` 关联登录账号。
- `tblCourseOffering`：具体学期开课记录，保存课程、任课教师、学期、课程类型和容量。后续教务开课、改容量、停课应落在该表。
- `tblCourseResult`：历史课程结果，保存学生每次首修/重修记录、成绩、是否通过和获得学分。
- `tblAcademicReview`：学业审查结果快照，保存累计学分、挂科门数、重修门数、是否满足毕业要求和审核人。

## 商店模块表

- `tblProduct`：商品、库存、价格和分类；`active` 表示是否上架。
- `tblOrder`：订单记录，保存用户、商品快照、数量和金额。
- `tblBankAccount`：校园钱包账户，`user_id` 为主键，`balance_cents` 以「分」为单位存 `BIGINT NOT NULL`；余额扣减/入账由应用层补偿保证一致性，不依赖数据库事务。

查询、购物车和购买只处理 `tblProduct.active=1` 的商品。全新数据库按 `schema.sql` 创建 `active` 字段；已有按旧 `004_store` 建立的数据库先执行 `database/migrations/007_store_product_active.up.sql`，回滚使用同名 `.down.sql`，不要对新库重复执行该迁移。

校园钱包表 `tblBankAccount` 由 `database/migrations/009_store_bank_account.up.sql` 建表、`.down.sql` 回滚（编号 009，不碰 008 的 `tblCartItem`）。⚠️ 数据库必须按最新 `schema.sql` 重建：本次新增 `tblBankAccount` 且 `balance_cents` 为 `BIGINT`，旧 `.accdb` 不含该表、与本次改动不兼容，沿用旧库会导致账户相关功能报错。

身份字段分工如下：`tblUser.user_id` 是登录身份；`tblStudent.student_id` 是学生学号；`tblTeacher.teacher_id` 是教师工号；`tblStudent.user_id` 和 `tblTeacher.user_id` 是档案与登录账号之间的一对一绑定字段，可为空但绑定后应保持唯一。如果账号尚未关联 `tblStudent` 或 `tblTeacher`，相关页面应提示“暂无对应档案，请联系管理员维护”；学业审查、课程历史和授课关系不能根据账号信息凭空生成。

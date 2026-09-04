# 学籍模块向成绩审核流程提供的档案接口

本文记录学籍模块已实现的教师、学生档案能力，以及选课模块接入成绩审核时需要完成的调用。成绩草稿、文件导入、审核状态机和正式成绩写入不属于学籍模块。

## 1. 已实现接口

```java
TeacherProfileService.findById(String teacherId)
TeacherProfileService.findByUserId(String userId)
StudentManagementService.findById(String studentId)
StudentManagementService.findByIds(List<String> studentIds)
```

### TeacherProfile

| 字段 | 含义 |
|---|---|
| `teacherId` | 教师工号，写入 `tblCourseOffering.teacher_id` |
| `userId` | 绑定登录账号，可为空 |
| `teacherName` | 教学班和审核页面展示姓名 |
| `departmentName` | 所属院系 |
| `title` | 职称 |
| `active` | 是否在职；非在职教师不得安排新教学班 |

返回约定：

- 找到档案：`OK + TeacherProfile`；
- 档案不存在或账号未绑定：`NOT_FOUND`；
- 参数为空：`BAD_REQUEST`；
- 同一账号绑定多个教师档案：保存时返回 `CONFLICT`；
- 数据库异常：`SERVER_ERROR`。

### 学生批量查询

`findByIds` 用于根据有效选课记录中的学号一次加载教学班名单：

- `null` 输入返回 `BAD_REQUEST`；
- 空列表返回 `OK + empty list`；
- 学号去重，结果保持学号首次出现顺序；
- 任一学号不存在返回 `NOT_FOUND`，避免成绩名单静默缺人；
- 返回完整 `StudentRecord`，包含姓名、专业、班级和学籍状态。

上述档案接口是服务端内部的 Java Service 契约，当前由选课和学籍服务在同一进程内直接调用，不经过 Socket。若未来拆分为独立服务，需要另行增加公共消息类型、命令对象、Handler 和权限校验，不能把客户端传入的学生或教师编号直接作为可信身份。

## 2. 选课模块调用方式

### 创建或更换教学班教师

```text
ACADEMIC_ADMIN 请求
→ 校验 COURSE_MANAGE
→ TeacherProfileService.findById(teacherId)
→ 档案存在且 active=true
→ 才允许写入 tblCourseOffering.teacher_id
```

当前 Access 模式已在 `CourseServiceFactory` 中注入教师档案服务，并由 `AccessCourseOfferingService.create` 与 `updateTeachingInfo` 执行上述校验；不存在的教师返回 `NOT_FOUND`，非在职教师返回 `CONFLICT`。

### 教师本人进入成绩页面

```text
token → currentSession → userId
→ TeacherProfileService.findByUserId(userId)
→ teacherId
→ 查询 tblCourseOffering.teacher_id=teacherId
```

客户端传入的 `teacherId` 不能作为权限依据。

### 加载教学班学生名单

```text
offeringId
→ 查询 tblCourseSelection 中 status=ACTIVE 的 student_id
→ StudentManagementService.findByIds(studentIds)
→ 合并选课类别后返回名单页面
```

成绩导入文件中的学号只能与该有效名单匹配，不得使用文件内容扩充教学班名单。

## 3. 数据库变更

`tblTeacher` 增加：

```text
active BIT NOT NULL
```

历史迁移草案：

```text
database/migrations/013_teacher_profile.up.sql
database/migrations/013_teacher_profile.down.sql
```

本阶段验收允许弃用旧 `.accdb`，教师档案表以最新 `database/schema.sql` 为准，部署时按 `schema.sql` + `seed.sql` 重建数据库。停用教师只阻止新教学安排和授课范围读取，不删除历史教学班、选课和成绩。

## 4. 仍由选课模块负责

- `tblGradeSubmission` 成绩批次；
- `tblGradeSubmissionItem` 成绩草稿明细；
- CSV、XLS、XLSX 导入与整批事务；
- `DRAFT → PENDING_REVIEW → APPROVED/RETURNED/REVOKED` 状态流转；
- 教师只能操作本人教学班的权限校验；
- 教务审核、退回、撤回；
- 审核通过后写入 `tblCourseResult`；
- 给 `tblCourseResult` 增加 `source_submission_id` 和 `result_status`。

## 5. 下一次联调点

选课模块完成正式成绩状态后，学籍模块需要同步修改：

```text
AccessAcademicReviewService.historyFor
AccessAcademicReviewService.pendingRetakes
AccessAcademicReviewService.review
AccessStudentSelectionProfileProvider 的待重修查询
```

上述查询必须只读取：

```sql
result_status = 'APPROVED'
```

现有历史数据建议在迁移时默认标记为 `APPROVED`，`source_submission_id` 可为空，用于兼容导入前的历史成绩。

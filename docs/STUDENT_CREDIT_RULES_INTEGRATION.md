# 学籍与选课模块：学分及学业审查对接文档

本文档用于和选课负责人、组长确认学分、重修、毕业审查及 Access 数据责任。文档分为“当前已经实现”“建议采用的统一规则”和“必须确认的事项”，未确认内容不作为最终代码依据。

## 1. 对接目标

学籍模块负责学生主档案和学业数据查询，选课/教务模块负责课程、教学班、成绩写入和培养方案。双方必须围绕同一套学生编号和课程结果协作：

```text
Token
  -> user_id
  -> tblStudent.user_id
  -> student_id
  -> tblCourseResult
  -> 学分/挂科/重修审查
  -> 选课 V2 资格判断
```

客户端不能自行提交学号作为权限依据，选课模块不能把 `user_id` 直接当作 `student_id`。

## 2. 当前已经确定的接口

学籍模块对外提供：

```java
StudentManagementService.findByUserId(String userId)
AcademicReviewService.pendingRetakes(String studentId)
AcademicReviewService.review(String studentId, int requiredCredits)
AcademicReviewService.latestReview(String studentId)
```

### `findByUserId`

用于根据登录账号定位学生档案。成功时返回 `StudentRecord`，至少包含：

- `studentId`
- `userId`
- 姓名
- 专业
- 班级
- 入学年份
- 学籍状态

账号未绑定学生档案返回 `NOT_FOUND`，选课页面应提示学生联系管理员。

### `pendingRetakes`

用于生成当前待重修课程列表：

- 按 `student_id + course_id` 汇总历史记录；
- 某门课程只要有一次 `passed=true`，就不再返回待重修；
- 所有尝试均未通过时，返回该课程最新一次失败记录；
- 没有待重修课程返回 `OK + empty list`；
- 学号为空返回 `BAD_REQUEST`。

## 3. `tblCourseResult` 数据契约

`tblCourseResult` 是学业审查的输入表，由选课/教务模块负责写入，学籍模块只读：

| 字段 | 含义 | 写入要求 |
|---|---|---|
| `result_id` | 成绩记录编号 | 全局唯一 |
| `student_id` | 学号 | 必须来自已绑定学生档案 |
| `course_id` | 课程编号 | 必须对应 `tblCourse` |
| `offering_id` | 教学班编号 | 首修建议填写，历史补录可为空 |
| `semester` | 修读学期 | 使用统一格式，如 `2025-2026-1` |
| `attempt_no` | 第几次尝试 | 首修为 1，重修递增 |
| `attempt_type` | 首修/重修 | 建议使用 `首修`、`重修` |
| `score` | 成绩 | 允许空值表示未录入，非空范围 0-100 |
| `passed` | 是否通过 | 布尔值 |
| `earned_credits` | 本次实际获得学分 | 未通过通常为 0，通过时按课程规则计算 |
| `recorded_at` | 记录时间 | 必填 |

写入成绩时建议校验：

```text
passed = false  -> earned_credits = 0
passed = true   -> earned_credits <= tblCourse.credits
```

如果存在补考、免修或部分学分等特殊情况，由教务模块明确例外，不由学籍模块猜测。

## 4. 学分统计建议规则

### 4.1 课程学分与实际获得学分

- `tblCourse.credits`：课程规定学分；
- `tblCourseResult.earned_credits`：学生本次通过后实际获得学分；
- 统计累计学分时只累计通过记录的 `earned_credits`；
- 同一门课程不能因首修和重修两条记录重复累计学分。

### 4.2 同一课程多次记录

建议采用“最后一次有效通过记录”规则：

```text
同一 course_id：
1. 如果存在通过记录，课程视为已通过；
2. 取时间/attempt_no 最新的有效通过记录计入学分；
3. 失败记录不计入累计学分；
4. 该课程只计入一次通过课程数。
```

当前实现使用“最高 `earned_credits`”作为临时规则，见：

- `server/src/main/java/cn/vcampus/server/AccessAcademicReviewService.java`
- `student-management/src/main/java/cn/vcampus/student/InMemoryAcademicReviewService.java`

在选课负责人确认后，内存和 Access 实现应统一改为同一规则，并补充“多次通过记录”的测试。

### 4.3 重修统计必须拆开

以下两个概念不能混为一个字段：

| 概念 | 含义 | 用途 |
|---|---|---|
| 历史重修课程数 | 曾经出现过重修尝试的课程数 | 学业历史展示 |
| 当前待重修课程数 | 所有尝试均未通过的课程数 | 生成选课重修轮次 |

`pendingRetakes(studentId)` 只负责第二项，不表示学生历史上是否重修过。

## 5. 学业审查分层

### 5.1 阶段性审查

当前代码支持的阶段性规则是：

```text
累计获得学分 >= requiredCredits
且不存在未解决挂科
```

其中：

```text
creditShortfall = max(0, requiredCredits - totalEarnedCredits)
```

### 5.2 最终毕业审查

如果 `graduationReady` 表示最终毕业资格，仅检查总学分和挂科是不够的，还应增加：

- 培养方案中的必修课程是否全部通过；
- 选修课程学分是否达到要求；
- 跨专业选修是否符合允许范围；
- 是否存在待重修课程；
- 学籍状态是否允许毕业审核。

建议本阶段先将 `review(...)` 定义为“阶段性审查”，待选课模块完成培养方案 Access 后，再扩展为最终毕业审查，避免当前字段语义过重。

## 6. `AcademicReview` 与快照表

当前 `AcademicReview` 已有：

```text
totalEarnedCredits
requiredEarnedCredits
passedCourseCount
failedCourseCount
retakeCourseCount
graduationReady
reviewedBy
reviewedAt
remark
```

建议补充：

```text
creditShortfall
requiredCourseCompleted
pendingRetakeCourseCount
```

如果这些字段进入正式接口，应同步修改：

- `student-management/src/main/java/cn/vcampus/student/AcademicReview.java`
- `database/schema.sql`
- `database/migrations/`
- `docs/INTERFACES.md`
- 内存、Access 测试

另外，`latestReview(...)` 应完整读取 `tblAcademicReview` 快照，不应让部分字段来自历史快照、部分字段重新查询当前成绩，否则快照时间点会失去一致性。

## 7. Access 表责任边界

| 表 | 学籍模块 | 选课/教务模块 |
|---|---|---|
| `tblStudent` | 学生档案、账号绑定、状态和联系方式维护 | 只读 |
| `tblCourse` | 读取课程名称和规定学分 | 课程目录维护 |
| `tblCourseOffering` | 不维护 | 教学班、任课教师、学期和容量 |
| `tblCourseResult` | 读取历史成绩、通过状态和获得学分 | 首修/重修成绩写入和更正 |
| `tblAcademicReview` | 读取最新审查快照 | 教务审核确认和快照写入 |
| 培养方案表 | 不维护 | 专业、入学年份、课程类别和建议学期 |

两边必须使用同一个 `database/vCampus.accdb`，不得分别连接不同数据库文件。

## 8. 选课 V2 对接流程

```text
CourseMessageHandler
  -> 校验 token 和 COURSE_READ/COURSE_SELECT
  -> UserManagementService.currentSession(token)
  -> 得到 user_id
  -> StudentSelectionProfileAdapter.findByUserId(user_id)
  -> StudentManagementService.findByUserId(user_id)
  -> AcademicReviewService.pendingRetakes(student_id)
  -> 生成 StudentSelectionProfile
  -> CourseSelectionService 按轮次/培养方案/容量/冲突规则处理
```

选课模块只使用适配器返回的 `studentId`、专业、入学年份、状态和待重修课程，不要从客户端 payload 读取学生身份。

## 9. 联调场景

| 场景 | 数据 | 预期 |
|---|---|---|
| 学生 A | 在读，无挂科 | 可进入首修轮次 |
| 学生 B | 某课程所有尝试均未通过 | 出现重修轮次 |
| 学生 C | 曾挂科但后续通过 | 不出现该课程重修 |
| 学生 D | 账号未绑定 `tblStudent` | 返回 `NOT_FOUND` |
| 学分不足 | 通过课程学分小于要求 | 返回学分缺口 |
| 多次通过 | 同课程存在多条通过记录 | 只累计一次课程学分 |
| 休学/毕业/退学 | 学籍状态非在读 | 禁止新增选课 |

测试数据建议使用不同的账号和学号：

```text
user_id = login_001
student_id = 20230001
```

## 10. 待确认事项

请组长和选课负责人确认以下三项后再进行代码重构：

1. `earned_credits` 是否始终以 `tblCourse.credits` 为上限，是否存在部分学分或特殊成绩；
2. `graduationReady` 表示阶段性审查结果，还是最终毕业资格；
3. 学籍状态最终保存英文编码（`ENROLLED` 等）还是中文显示值（“在读”等）。

确认结果应回填本文件，并同步更新接口、数据库和测试。

## 11. 当前实现缺口

- 内存和 Access 学分统计规则需要统一；
- `AcademicReview` 尚未显式暴露学分缺口；
- `tblAcademicReview` 快照字段不完整；
- 课程历史对象缺少课程编号、学期和尝试类型的非空校验；
- 状态文档和运行数据的中英文编码尚未统一；
- 选课 V2 的 Access 持久化仍由选课模块完成；
- 教师查询学生的授课范围过滤仍需教师/教学班服务提供。

在上述事项确认前，不建议直接修改公共接口或数据库字段，以免和选课模块的 Access 实现产生二次冲突。

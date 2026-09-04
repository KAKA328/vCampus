# 学籍与选课模块对接说明

本文档是学生学籍模块与选课模块的实施契约。学籍模块负责学生主数据和课程历史查询，选课模块负责培养方案、开课、选课记录及成绩写入；两边通过服务器层的服务接口对接，不直接互写对方业务表。

学分、重修和毕业审查的专项规则见 [`STUDENT_CREDIT_RULES_INTEGRATION.md`](STUDENT_CREDIT_RULES_INTEGRATION.md)。

## 1. 身份和调用边界

| 标识 | 含义 | 使用位置 |
|---|---|---|
| `user_id` | 登录账号，由 Token 对应的会话解析得到 | `tblUser`、会话、权限、订单和借阅 |
| `student_id` | 学号，学生档案的业务主键 | `tblStudent`、选课记录、课程历史、学业审查 |

学生本人请求必须遵循：

```text
token -> 当前会话 user_id -> tblStudent.user_id -> student_id
```

客户端不能用输入框中的 `student_id` 或 `user_id` 决定本人身份，也不能直接连接 Access 数据库。选课 Handler 完成 Token 和权限校验后，调用学籍服务取得档案，再把服务返回的 `student_id` 传给选课业务服务。

## 2. 学籍模块提供的接口

业务层接口位于 `student-management` 模块：

```java
StudentManagementService.findByUserId(String userId)
AcademicReviewService.pendingRetakes(String studentId)
```

### 2.1 `findByUserId`

用途：根据登录账号定位学生档案。

- 成功：`OK + StudentRecord`，至少包含 `studentId`、`userId`、姓名、专业、班级、入学年份和学籍状态；
- 未绑定档案：`NOT_FOUND`，选课页面提示“暂无学籍档案，请联系管理员维护”；
- 参数为空：`BAD_REQUEST`；
- Access 实现：`AccessStudentRepository.findByUserId`；
- 服务器适配：`StudentSelectionProfileAdapter.findByUserId`。

服务器在 Token 已解析为 `userId` 后，也可以调用兼容别名：

```java
StudentManagementService.findMyStudentProfile(String userId)
```

该方法与 `findByUserId` 等价，不接受客户端自行传入的身份编号。

### 2.2 `pendingRetakes`

用途：生成学生当前待重修课程集合。

- 数据来源：`tblCourseResult`，课程名称通过 `tblCourse` 查询；
- 按 `student_id + course_id` 汇总；
- 某课程只要存在一条 `passed=true`，就不再返回待重修；
- 只有全部尝试未通过时，返回该课程最新一次失败记录；
- 没有待重修课程：`OK + empty list`；
- 学号为空：`BAD_REQUEST`。

学籍模块只读课程结果，不负责写入选课记录或成绩。课程结果由选课/教务模块维护，学籍模块提供查询和规则判断。

## 3. 选课模块的调用顺序

```text
COURSE_SELECTION_QUERY_V2 / COURSE_SELECT_OFFERING_V2 / COURSE_DROP_RECORD_V2
        |
        v
CourseMessageHandler 校验 token、会话和 COURSE_READ/COURSE_SELECT
        |
        v
StudentSelectionProfileAdapter.findByUserId(userId)
        |
        +--> StudentManagementService.findByUserId(userId)
        |
        +--> AcademicReviewService.pendingRetakes(studentId)
        |
        v
StudentSelectionProfile(studentId, major, enrollmentYear, status, pendingRetakes)
        |
        v
CourseSelectionService 执行轮次、专业、容量、冲突和重修规则
```

只有 `status` 为“在读”的学生允许新增选课；休学、毕业和退学学生可以查看允许范围内的历史，但新增选课应返回 `FORBIDDEN`。选课模块不应再使用 `userId.equals(studentId)` 判断本人。

## 4. Access 表和字段责任

| 表 | 学籍模块 | 选课/教务模块 |
|---|---|---|
| `tblStudent` | 负责学生档案、账号绑定、状态和联系方式的查询/维护 | 只读，不直接修改 |
| `tblCourse` | 读取课程名称用于历史记录展示 | 负责课程目录维护 |
| `tblCourseResult` | 只读历史成绩、通过标记和获得学分 | 负责首修/重修成绩写入和更正 |
| `tblAcademicReview` | 读取最新审查快照，实时审查由服务计算 | 负责教务审核流程和快照写入（如启用持久化审核） |
| 培养方案/教学班表 | 不维护 | 由选课/教务模块维护 |

对应实现：

- Access 学生档案：[server/src/main/java/cn/vcampus/server/AccessStudentRepository.java](../server/src/main/java/cn/vcampus/server/AccessStudentRepository.java)
- Access 课程历史：[server/src/main/java/cn/vcampus/server/AccessAcademicReviewService.java](../server/src/main/java/cn/vcampus/server/AccessAcademicReviewService.java)
- 选课适配器：[server/src/main/java/cn/vcampus/server/StudentSelectionProfileAdapter.java](../server/src/main/java/cn/vcampus/server/StudentSelectionProfileAdapter.java)

## 5. 状态和错误处理

| 场景 | 服务结果 | 选课页面处理 |
|---|---|---|
| Token 无效/过期 | `UNAUTHORIZED` | 返回登录页 |
| 没有课程权限 | `FORBIDDEN` | 提示无权操作 |
| 账号未绑定学生档案 | `NOT_FOUND` | 提示联系学籍管理员 |
| 学生休学、毕业或退学 | `FORBIDDEN` | 禁止新增选课 |
| 学籍数据库不可用 | `SERVER_ERROR` | 提示稍后重试并记录服务器日志 |

## 6. 联调数据和验收

建议准备四类账号/档案：

1. 学生 A：在读、无挂科，可查询本人档案和首修轮次；
2. 学生 B：存在全部未通过的课程结果，应出现重修轮次；
3. 学生 C：曾挂科但后续通过，不应出现该课程重修；
4. 学生 D：账号存在但未绑定 `tblStudent`，选课前返回 `NOT_FOUND`。

必须验证：

- Access 模式下 `user_id` 与 `student_id` 可以不同；
- 学生 Token 不能读取其他学生档案；
- 教务管理员可以按学号、班级和专业查询/维护；
- `pendingRetakes` 的通过优先规则正确；
- 选课 V2 请求不携带或信任客户端学生身份；
- `mvn clean test package` 全项目通过。

## 7. 当前实现和待确认项

- Access 模式下教师查询学生已经按教师档案、教学班和有效选课记录限制授课范围；内存模式无授课关系时默认拒绝；
- `--db` 模式的选课学期由 Access 选课轮次确定，内存演示仍使用 `CourseSelectionDemoFactory.DEMO_TERM`；
- `review(studentId, requiredCredits)` 当前实时计算，不覆盖 `tblAcademicReview` 历史快照；若要持久化审核结果，需要由教务模块确认审核人和写入接口。

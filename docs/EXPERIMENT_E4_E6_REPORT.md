# E4-E6 阶段实验推进报告

> 日期：2026-08-26  
> 范围：推进 E4 学籍审查、E5 选课管理与成绩录入、E6 五模块联调；不包含最终验收打包材料、JavaDoc、纸质报告装订。

## 1. 本轮目标

本轮目标是把项目从“用户管理 + 学生选课局部可用”推进到“五个必做模块都有可联调入口”的状态，并解决前期提出的关键问题：

- 学籍审查不能只依赖注册账号，需要独立保存学生档案、历史课程、首修/重修、通过情况和学分；
- GUI 不能只停留在首页卡片，主要子系统应能从主界面进入；
- 服务器必须做权限校验，不能只靠客户端隐藏按钮；
- 其他子系统新增公共接口时，必须同步 `MessageType`、Handler、客户端调用、文档和测试；
- 相同账号不能同时登录的问题已在前一轮完成，本轮继续保持测试覆盖。

## 2. 已完成内容

### 2.1 公共通信协议

新增或补齐以下公共消息类型：

| 模块 | 消息类型 | 用途 |
|---|---|---|
| 学籍 | `STUDENT_REVIEW` | 查询学生学业审查结果 |
| 选课 | `COURSE_GRADE_WRITE` | 任课教师录入成绩 |
| 商店 | `STORE_ORDER_QUERY` | 查询本人订单或管理员查询全部订单 |

并补充了对应的命令对象、服务器 Handler、客户端远程服务和接口文档。

### 2.2 学籍与学业审查

已完成：

- `StudentRecord` 学生基础档案字段；
- `CourseHistoryRecord` 历史课程记录；
- `AcademicReview` 学业审查结果；
- `InMemoryAcademicReviewService` 内存版审查计算；
- `InMemoryStudentManagementService` 内存版学籍档案；
- `StudentMessageHandler` 服务端消息处理；
- `StudentProfilePanel` Swing 页面。

处理原则：

- 注册只创建登录账号，不自动创建学籍档案；
- 学籍档案由学籍/教务模块维护；
- 历史课程结果由 `tblCourseResult` 规划保存；
- 学业审查以历史记录计算累计学分、未通过课程数、重修课程数和毕业条件。

### 2.3 选课管理与成绩录入

已完成：

- 课程新增、修改、停开接口；
- 教师成绩录入接口；
- `COURSE_CREATE`、`COURSE_UPDATE`、`COURSE_DEACTIVATE`、`COURSE_GRADE_WRITE` 服务端处理；
- `CourseManagementPanel` Swing 页面；
- 权限测试：商店管理员不能伪造请求进入选课管理。

当前限制：

- 任课教师“授课范围”目前只校验教师身份与 token 一致，还没有连接真实 `tblCourseOffering.teacher_id`；
- Access 版成绩持久化还未完整落地，后续应写入 `tblCourseResult`。

### 2.4 图书馆

已完成：

- `Book` 增加可借数量；
- `InMemoryLibraryService` 支持图书查询、借阅、归还；
- `LibraryMessageHandler` 服务端权限与数据范围校验；
- `RemoteLibraryService` 和 `LibraryPanel` 客户端页面；
- 权限测试：商店管理员不能进入图书馆；学生不能伪造他人读者编号借书。

### 2.5 商店

已完成：

- `Product` 商品实体基础校验；
- `StoreOrder` 订单实体；
- `InMemoryStoreService` 支持商品查询、购买、本人订单和全部订单；
- `StoreMessageHandler` 服务端权限与数据范围校验；
- `RemoteStoreService` 和 `StorePanel` 客户端页面；
- 权限测试：教务管理员不能进入商店；学生不能伪造他人买家编号购买。

### 2.6 GUI 与总控

已完成：

- 学籍、选课、图书馆、商店页面接入 `MainFrame`；
- 导航状态由“待接入”更新为“可用”；
- 学生、教师、教务管理员、图书管理员、商店管理员、系统管理员仍按角色显示不同入口；
- 首页卡片和主要页面使用滚动区域，减少窗口缩放导致内容显示不全的问题。

## 3. 权限处理结论

本轮继续采用“系统角色不随子系统变成另一个角色”的设计：

- 学生在商店中是买家，但系统角色仍是 `STUDENT`；
- 教师在商店中是买家，但系统角色仍是 `TEACHER`；
- 商店管理员是 `STORE_MANAGER`，只拥有商店权限，没有选课权限；
- 图书管理员是 `LIBRARIAN`，只拥有图书馆权限；
- 教务管理员是 `ACADEMIC_ADMIN`，只拥有学籍和选课管理权限；
- 系统管理员 `ADMIN` 保留全部权限用于维护和验收。

客户端负责隐藏无权入口；服务器负责最终拒绝伪造请求，返回 `FORBIDDEN`。

## 4. 仍未完成但不属于本轮“最终验收前全部解决”的内容

以下事项仍需后续推进：

1. Access 完整持久化：学籍、图书馆、商店、成绩录入都需要对应 Access Repository；
2. 真实授课范围：教师录成绩应校验 `tblCourseOffering.teacher_id`；
3. 教务成绩复核：成绩录入后由教务管理员复核并触发学业审查快照；
4. GUI 进一步美化：目前是可用布局，后续可继续统一表格配色、空状态、弹窗和图标；
5. 真实演示数据：需要准备多角色账号、学生档案、课程、图书、商品和订单；
6. 最终验收材料：JavaDoc、使用说明书、最终 Word 文档、纸质装订和签名。

## 5. 验证结果

已运行：

```powershell
mvn clean test
mvn package -DskipTests
java -jar client\target\vCampusClient.jar --demo --host 127.0.0.1 --port 19999
```

结果：

- 编译通过；
- 99 个单元测试通过；
- 服务端 Handler 权限测试通过；
- 客户端页面编译通过。
- 已生成 `server\target\vCampusServer.jar` 和 `client\target\vCampusClient.jar`；
- jar 烟测通过：注册、登录、授权、登出、旧 token 失效均符合预期。

## 6. 下一步建议

下一轮不建议再继续横向铺新功能，而应做“数据落地和演示闭环”：

1. 先完成 `student-management` 的 Access Repository；
2. 再完成商店和图书馆 Access Repository；
3. 然后把课程成绩写入 `tblCourseResult`；
4. 最后准备一套固定演示账号和数据，录屏或截图保存作为验收材料。

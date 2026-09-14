# 培养方案专业目录对接

最终提交验证（2026-09-14）：基于 main `1e3e546`，默认目录已补齐 CS/SE/CN，涵盖全部默认培养方案专业；汉语言文学归属通识教育学院，为本项目演示数据约定。全仓 `mvn clean test package "-DargLine=-Djdk.net.URLClassPath.disableClassPathURLCheck=true"` 通过，共 882 项测试，0 失败、0 错误、0 跳过。独立子智能体组长视角复审为 Approve。下方早期验证数量仅为阶段记录。

## 接口与数据

消息 `STUDENT_MAJOR_DIRECTORY_QUERY_V1`，参数 `MajorDirectoryQueryV1Command(token)`，成功返回 `List<MajorDirectoryEntry>`。字段为 `majorId`（稳定编号）、`majorName`、`departmentName`、`active`。仅 ADMIN、ACADEMIC_ADMIN 可调用；会话失效返回 UNAUTHORIZED，其他角色返回 FORBIDDEN，错误参数返回 BAD_REQUEST。

学籍模块拥有目录，Access 从独立 `tblMajor` 表读取有效专业，不从学生、班级或培养方案文本猜测目录。内存演示与 seed 的 CS/SE/CN 条目一致。按 Java 字符串升序依次比较院系、名称、编号，确保跨存储排序一致；不是拼音排序。没有有效专业返回 OK + 空列表，缺表或数据库异常返回 SERVER_ERROR，不回退为内存目录。

CS/SE/CN 是稳定的本地演示编码，不是国家统一专业代码；编号不得随着名称或排序改变。旧存储按名称定位培养方案，因此目录名称暂时全局唯一；跨院系同名专业在统一编号迁移前不能形成歧义映射。

## 客户端与服务端

`RemoteStudentService.activeMajors(token)` 获取目录；可复用的 `MajorDirectorySelector` 提供不可编辑下拉框，显示“专业编号 - 专业名称”，院系显示于选中项说明和悬浮提示。

2026-09-14 同步 main `1e3e546` 后，培养方案采用主线的列表、详情和编辑弹窗。每次打开新建或编辑弹窗都会重新加载目录；未加载、失败、空目录或未选有效专业时，确认按钮禁用，可刷新目录重试。旧专业不在有效目录中时不自动选择第一项，须明确选择有效专业；目录失败不妨碍查看旧方案列表。课程要求编辑继续沿用主线界面，不依赖专业目录加载。

`TrainingPlanEditorDialog` 已接入同一控件，每次打开新增/编辑弹窗时：

1. loader 调用 `RemoteStudentService.activeMajors(token)`；
2. 编辑时 `selectName(existingPlan.getMajorName())`，随后 `load()`；
3. changed 回调根据 `canSave()` 启用确认按钮；
4. 使用 `selectedName()` 保存，不回退到文本输入。

培养方案仍保存专业名称到 `TrainingPlan.majorName` 和 `tblTrainingPlan.major_name`，不保存拼接显示文字或专业编号，不改变旧 DTO 字段。服务端在 CREATE、UPDATE_BASIC_INFO 时重新验证名称对应当前有效专业；无效/停用/歧义专业返回 BAD_REQUEST，目录故障返回 SERVER_ERROR，避免绕过下拉框自由创建专业。

## 数据库与部署

schema/seed 已加入 tblMajor 和三个有效演示专业。已有数据库不会自动改写。在仓库根目录准备独立测试库：

```powershell
.\database\rebuild.ps1 -DatabasePath database\major-directory-test.accdb
java -jar server/target/vCampusServer.jar --db database/major-directory-test.accdb --port 19114
java -jar client/target/vCampusClient.jar --host 127.0.0.1 --port 19114
```

用 demo_academic_admin / Demo123 登录培养方案管理。重建目标已存在时脚本会先备份再替换，只用于测试库，不用于共享运行库。保留旧库数据时，维护人员先备份、停止服务，再按本次 schema 中 tblMajor 的建表语句和对应 seed INSERT 建立真实目录。未知历史专业须先确认编号和院系，不能自动视为有效。

此次只提供只读目录，不含专业维护 GUI；新增、停用由数据维护流程负责。客户端、common、student-management、server 配套升级。旧库缺表时页面明确报错并禁止保存，不允许未校验自由输入。

## 验证范围

有效过滤、停用排除、空目录、稳定排序、重开库编号一致、角色与会话权限、缺表故障、名称保存兼容、停用专业拒绝，以及客户端无选择/错误目录阻止提交。

2026-09-14 执行 `mvn clean test package`：BUILD SUCCESS，852 项测试，0 失败、0 错误、0 跳过；`git diff --check` 通过。本次总数包含已完成的旧表单覆盖修复；专业目录相关定向测试（含原培养方案回归）13 项通过。尚未操作两个真实 Swing 窗口人工验收。

审查后补充：培养方案管理失败响应现传递具体错误说明，其他选课消息响应行为不变。修正后的定向回归 11 项通过（CourseMessageHandlerTest 7 项、MajorDirectoryIntegrationTest 3 项、MajorDirectorySelectorTest 1 项）；未重新执行全量构建。子智能体只读复审结论为 Approve。真实 Swing 人工验收和目录异步生命周期测试仍待补充。

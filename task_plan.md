# 学籍系统对接阶段计划

## 目标
落实《对接文档_学籍系统同学版.docx》要求，完成 Access 数据库、Token 身份解析、学生查询、学业审查及选课模块接口的可验证闭环。

## 阶段
- [x] 阶段 1：核对当前分支、提交记录和对接文档落实情况
- [x] 阶段 2：审查学业审查实时计算与快照读取逻辑，补齐必要实现
- [x] 阶段 3：运行模块测试和全项目构建，处理兼容性问题
- [x] 阶段 4：检查差异、提交并推送 feature/student-management

## 关键决策
- 学籍模块只读 `tblCourseResult`，不重复维护成绩表。
- 培养方案由选课模块维护；学籍模块提供学生档案、历史成绩和待重修判断。
- `review` 默认实时计算，`latestReview` 读取 `tblAcademicReview` 快照。

## 状态
当前阶段已完成：新增测试已提交并推送到 `origin/feature/student-management`。

## Follow-up: 2026-09-02 main 文档同步与对接契约
- [x] 获取最新 `origin/main`（2230462）并确认本次为课程维护文档更新
- [x] 备份学籍分支并合并最新 main
- [x] 解决 `docs/INTERFACES.md`、`docs/SYSTEM_DESIGN.md` 文档冲突
- [x] 更新 `docs/MODULE_INTEGRATION_GUIDE.md` 中的学籍完成状态和课程维护操作
- [x] 新增 `docs/STUDENT_COURSE_INTEGRATION.md`，明确学籍与选课接口、字段责任、Token 流程和联调验收
- [x] 运行 `mvn -pl server -am test`（67 项通过）并推送 `5896c73`

## Follow-up: 2026-09-02 学分与学业审查规则复核
- [x] 对照软件说明、培养方案模型、课程历史表和选课 V2 服务检查当前业务口径
- [x] 确认当前审查只按“累计学分 + 未解决挂科”判断毕业，尚未覆盖必修课程完成情况
- [x] 发现 `AcademicReview` 未显式保存学分缺口，内存构造器与 Access 结果的 `requiredCredits` 语义不一致
- [x] 发现学分统计使用同课程最高 `earned_credits`，需要组内确认是否改为最新通过记录或课程目录学分
- [x] 发现状态文档使用英文枚举、数据库和运行代码使用中文值，需统一编码
- [x] 发现 `CourseHistoryRecord` 对课程编号/学期/类型缺少非空校验，内存 `historyFor` 未统一 trim
- [ ] 与选课负责人确认 `tblCourseResult` 字段写入规则和学分来源
- [ ] 与组长确认毕业判定是否包含培养方案必修课程完成
- [ ] 确认后再重构 `AcademicReview`、课程历史校验和 Access/内存实现，并补回归测试

## Follow-up: 2026-09-02 学分规则对接文档
- [x] 新增 `docs/STUDENT_CREDIT_RULES_INTEGRATION.md`
- [x] 明确 `tblCourseResult` 字段责任、学分来源、重修统计和阶段/最终审查边界
- [x] 记录当前实现缺口及需要组长、选课负责人确认的三项规则
- [x] 在 `docs/STUDENT_COURSE_INTEGRATION.md` 增加专项规则文档入口

## Follow-up: 2026-09-02 PR33 审查问题修复
- [x] 修复列表选中学生后表单字段不完整的问题：按学号重新查询完整 `StudentRecord`
- [x] 通过选择更新保护避免单条查询自动触发重复加载
- [x] 将学籍迁移从 `008_student_academic` 顺延为 `010_student_academic`，避免与 PR31 的购物车迁移和 PR36 的钱包迁移冲突
- [x] 同步 `database/README.md` 的迁移编号说明
- [x] 运行 `mvn clean test package`（250 项通过）
- [ ] 等待用户明确指令后再推送并更新 PR33

## Follow-up: 2026-09-02 PR33 二次审查问题修复
- [x] 修复快速切换学生导致表单与当前行不一致的问题
- [x] 异步加载学生完整档案期间禁用学生列表，阻止并发选择事件
- [x] 保持保存按钮在加载期间禁用，加载完成后统一恢复
- [ ] 运行验证后等待用户明确指令，再推送并更新 PR33

## Follow-up: 2026-09-02 组长视角自检补充
- [x] 启动独立子智能体从组长视角审查 PR33 修复
- [x] 发现并修复列表刷新/详情失败后的旧表单残留风险
- [x] 增加 `loadedRecord` 守卫，只有完整档案加载成功后才启用保存
- [x] 增加客户端回归测试，覆盖加载期间表格禁用与恢复
- [x] 子模块测试通过，等待用户明确指令后再推送

## Follow-up: 2026-09-02 PR33 第三次审查问题修复
- [x] 修复按学号查询和查询本人前未清空旧档案的问题
- [x] 查询失败时通过 `loadedRecord=false` 保证保存按钮保持禁用
- [x] 增加清空编辑区后不可保存的客户端回归测试
- [ ] 等待用户明确指令后再推送并更新 PR33

## Follow-up: 2026-09-04 最新主线学籍完善
- [x] 更新本地 `main` 到 `origin/main@496b7e3`，确认 PR33、PR35 已合并
- [x] 从最新主线创建 `feature/student-academic-hardening`
- [x] 确认 `--db` 已接入选课 V2 Access 服务，学籍迁移已统一为 010
- [x] 统一课程历史必填字段校验和内存/Access 空学号语义
- [x] 修复内存审查丢失 `requiredCredits` 的问题并增加学分缺口 getter
- [x] 基于最新 Access 教学班/选课记录实现教师授课范围校验
- [x] 移除 `userId == studentId` 的学生本人绑定旁路
- [x] 运行相关模块验证并复核差异（学籍 14 项、服务器 118 项通过）
- [x] 运行全项目 `mvn clean test package`（359 项通过，失败 0）
- [ ] 等待学分聚合和最终毕业口径确认后再做下一阶段重构

## Follow-up: 2026-09-04 学籍类图提取与讲解
- [x] 解析根目录 `类图.drawio.html`，定位 `student_management` 区域
- [x] 提取原图中的五个学籍核心类及三条关系
- [x] 生成 Mermaid 源码、Markdown 讲解和 PNG 静态预览
- [x] 对照最新代码补充身份、Repository、Access、Handler、页面和选课对接说明
- [x] 使用 Mermaid CLI 验证语法并完成图片可读性检查

## Follow-up: 2026-09-04 教师档案与成绩审核对接第一阶段
- [x] 新增教师档案实体、Service、Repository 及内存实现
- [x] 实现 Access 教师档案查询、保存、账号唯一绑定与在职状态
- [x] 为学生档案增加按学号批量查询能力
- [x] 增加 `tblTeacher.active` 数据库迁移并同步 schema、seed、README
- [x] 编写选课模块调用所需的成绩审核档案接口对接文档
- [x] 补充内存、Access、异常与绑定冲突测试
- [x] 运行相关模块和全项目验证（367 项通过）

### 本阶段边界
- 选课模块负责成绩草稿、文件导入、审核状态机和 `tblCourseResult` 正式成绩写入。
- 学籍模块本阶段只提供教师/学生档案接口，不实现成绩审核流程。

### 本阶段错误记录
- UCanAccess 4.0.4 在临时测试库中不支持动态执行 `CREATE UNIQUE INDEX`；测试改为验证 Repository 的事务内重复绑定检查，正式 schema 继续保留唯一索引定义。
- UCanAccess 4.0.4 测试库对 `BIT` 字段使用 `true` 字面量会报类型转换错误；测试初始化已改用与正式 Access 种子一致的 `1`/`0` 写法。

### 本阶段验证结果
- `mvn -pl server -am -Dtest=AccessTeacherRepositoryTest test`：3 项通过。
- `mvn clean test package`：全项目 367 项通过，失败 0；server 122 项、client 47 项均通过，并生成服务器和客户端可执行包。

## Follow-up: 2026-09-04 组长视角审查修复
- [x] 将 `TeacherProfileService` 注入 Access 课程服务组装流程
- [x] 创建教学班和更换任课教师前校验教师档案存在且在职
- [x] 补充未知教师、非在职教师和教师档案联动回归测试
- [x] 加强学生批量查询的返回学号集合与重复记录校验
- [x] 在数据库唯一索引拒绝并发重复绑定后恢复 `CONFLICT` 业务语义
- [x] 在对接文档中明确档案接口为服务端同进程 Java 契约
- [x] 相关模块测试通过：学籍 19 项、服务器 123 项
- [x] 运行全项目构建并完成最终差异检查（369 项通过，0 失败）

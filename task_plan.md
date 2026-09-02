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

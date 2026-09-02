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

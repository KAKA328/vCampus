# 数据库目录

最终提交时放入机房兼容版本的 `vCampus.accdb`，并补充 `schema.sql` 与 `seed.sql`（如 Access 版本不支持某条 SQL，以实际建库结果为准）。

## 选课模块表

- `tblCourse`：课程基本信息，包含课程号、课程名称、学分和课程容量。
- `tblCourseSelection`：学生选课记录，包含学生学号、课程号和选课时间。

`tblCourseSelection(student_id, course_id)` 使用唯一索引，保证同一学生不能重复选择同一门课程。已选人数不单独存入 `tblCourse`，而是在选课时统计选课记录，避免人数数据不一致。

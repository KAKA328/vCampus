-- 将课程学分、正式成绩已获学分和学分审查字段升级为两位小数。
-- 该脚本使用 Microsoft Access 原生 ALTER COLUMN 语法；UCanAccess 4.x 不支持此 DDL，
-- 测试环境请直接按最新 database/schema.sql + database/seed.sql 重建数据库。
ALTER TABLE tblCourse ALTER COLUMN credits DECIMAL(10,2);
ALTER TABLE tblCourseResult ALTER COLUMN earned_credits DECIMAL(10,2);
ALTER TABLE tblAcademicReview ALTER COLUMN total_earned_credits DECIMAL(10,2);
ALTER TABLE tblAcademicReview ALTER COLUMN required_earned_credits DECIMAL(10,2);
ALTER TABLE tblAcademicAssessment ALTER COLUMN earned_credits DECIMAL(10,2);
ALTER TABLE tblAcademicAssessment ALTER COLUMN required_credits DECIMAL(10,2);

-- 学籍模块专项测试数据
-- 用法：先按 schema.sql + seed.sql 重建数据库，再执行本文件。
-- 本文件不创建登录账号；新增档案默认由 ADMIN/ACADEMIC_ADMIN 查看。
-- 所有手机号均为 11 位数字，便于验证档案格式规则。

INSERT INTO tblClass(class_id, class_name, department_name, major_name, grade_year)
VALUES ('SE2024-01', '软件工程2024级1班', '计算机科学与工程学院', '软件工程', 2024);

INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name,
        major_name, class_id, enrollment_year, status, phone, email)
VALUES ('test_student_enrolled', NULL, '测试在读学生', '女', '计算机科学与工程学院',
        '软件工程', 'SE2024-01', 2024, '在读', '13900000001', 'enrolled@example.test');

INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name,
        major_name, class_id, enrollment_year, status, phone, email)
VALUES ('test_student_leave', NULL, '测试休学学生', '男', '计算机科学与工程学院',
        '软件工程', 'SE2024-01', 2024, '休学', '13900000002', 'leave@example.test');

INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name,
        major_name, class_id, enrollment_year, status, phone, email)
VALUES ('test_student_withdrawn', NULL, '测试退学学生', '未知', '计算机科学与工程学院',
        '软件工程', 'SE2024-01', 2024, '退学', '13900000003', 'withdrawn@example.test');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('test_teacher_active', NULL, '测试在职教师', '计算机科学与工程学院', '讲师', 1);

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('test_teacher_inactive', NULL, '测试非在职教师', '计算机科学与工程学院', '副教授', 0);

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester,
        attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('test-result-enrolled-pass', 'test_student_enrolled', 'JAVA101', NULL,
        '2025-2026-1', 1, '首修', 88, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester,
        attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('test-result-enrolled-fail', 'test_student_enrolled', 'DB101', NULL,
        '2025-2026-1', 1, '首修', 55, 0, 0, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester,
        attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('test-result-enrolled-retake', 'test_student_enrolled', 'DB101', NULL,
        '2025-2026-2', 2, '重修', 78, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester,
        attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('test-result-leave-fail', 'test_student_leave', 'JAVA101', NULL,
        '2025-2026-1', 1, '首修', 49, 0, 0, NOW());

-- 预期：
-- test_student_enrolled：6 学分、2 门通过、1 次历史重修、当前无待重修。
-- test_student_leave：0 学分、1 门待重修；状态为休学，不应允许办理毕业。
-- test_student_withdrawn：无成绩；状态为退学，不应允许办理毕业。
-- test_teacher_active：可被教务查看，允许作为在职教师候选。
-- test_teacher_inactive：可被教务查看，但不能安排新教学班。

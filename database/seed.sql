-- Demo-only accounts. Replace password_hash values with hashes from the chosen implementation.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-admin', 'REPLACE_WITH_HASH', 'Demo Administrator', 'ADMIN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo-student', 'REPLACE_WITH_HASH', 'Demo Student', 'STUDENT', 1);

-- 选课模块演示课程。
INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('JAVA101', 'Java 程序设计', 3, 40);

INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('DB101', '数据库原理', 3, 40);

INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('NET101', '计算机网络', 3, 30);

-- 学籍与学业审查演示数据：注册账号不自动生成这些记录，正式数据由教务维护或导入。
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('demo-student', 'demo-student', 'Demo Student', '未知', '计算机科学与工程学院', '软件工程', 'SE2023-01', 2023, '在读', '', '');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title)
VALUES ('demo-teacher', NULL, 'Demo Teacher', '计算机科学与工程学院', '讲师');

INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, semester, course_type, total_capacity, major_capacity, cross_major_capacity, active)
VALUES ('offering-java-2025a', 'JAVA101', 'demo-teacher', '2025-2026-1', '必修', 40, 35, 5, 1);

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-java-demo-1', 'demo-student', 'JAVA101', 'offering-java-2025a', '2025-2026-1', 1, '首修', 86, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-1', 'demo-student', 'DB101', NULL, '2025-2026-1', 1, '首修', 52, 0, 0, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-2', 'demo-student', 'DB101', NULL, '2025-2026-2', 2, '重修', 75, 1, 3, NOW());

INSERT INTO tblAcademicReview(review_id, student_id, total_earned_credits, required_earned_credits, failed_course_count, retake_course_count, graduation_ready, reviewed_by, reviewed_at, remark)
VALUES ('review-demo-student-1', 'demo-student', 6, 6, 0, 1, 1, 'demo-admin', NOW(), '演示数据：包含首修未通过和重修通过记录。');

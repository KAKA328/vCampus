-- Demo-only accounts. Initial password for all demo accounts: Demo123.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_admin', 'zOeizxrgRZic/JFPuVpBUg==:xWXvxTlDz+TMHc7vtlTIA5co9c9CGtcym4aYtr2LK7M=', 'Demo Administrator', 'ADMIN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_academic_admin', '2URhsAIut9zD4Wpa2LitDg==:hqrBro/Sc1nex0VjnlgVqlFs+1hSpS0g/RfTZAnot2g=', 'Demo Academic Administrator', 'ACADEMIC_ADMIN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_librarian', 'CyUGA2zztjSKYZHTcuFFVw==:P3kwUfUVmXevVzORB4/2AO72BYFFLnpKXD4k+Vs/6XE=', 'Demo Librarian', 'LIBRARIAN', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_store_manager', 'MlmQfJs4JPqfyzrOS2vWSA==:60rRuqawBtN2BIANRJmd3X++VrG/WgO0npwd09JfU4Y=', 'Demo Store Manager', 'STORE_MANAGER', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_student', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Student', 'STUDENT', 1);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active)
VALUES ('demo_teacher', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', 'Demo Teacher', 'TEACHER', 1);

-- 选课模块演示课程。
INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('JAVA101', 'Java 程序设计', 3, 40);

INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('DB101', '数据库原理', 3, 40);

INSERT INTO tblCourse(course_id, course_name, credits, capacity)
VALUES ('NET101', '计算机网络', 3, 30);

-- 学籍与学业审查演示数据：演示账号已预先绑定档案；正式历史成绩由教务维护或导入。
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('demo_student', 'demo_student', 'Demo Student', '未知', '计算机科学与工程学院', '软件工程', 'SE2023-01', 2023, '在读', '', '');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title)
VALUES ('demo_teacher', 'demo_teacher', 'Demo Teacher', '计算机科学与工程学院', '讲师');

INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, semester, course_type, total_capacity, major_capacity, cross_major_capacity, active)
VALUES ('offering-java-2025a', 'JAVA101', 'demo_teacher', '2025-2026-1', '必修', 40, 35, 5, 1);

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-java-demo-1', 'demo_student', 'JAVA101', 'offering-java-2025a', '2025-2026-1', 1, '首修', 86, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-1', 'demo_student', 'DB101', NULL, '2025-2026-1', 1, '首修', 52, 0, 0, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-2', 'demo_student', 'DB101', NULL, '2025-2026-2', 2, '重修', 75, 1, 3, NOW());

INSERT INTO tblAcademicReview(review_id, student_id, total_earned_credits, required_earned_credits, failed_course_count, retake_course_count, graduation_ready, reviewed_by, reviewed_at, remark)
VALUES ('review-demo-student-1', 'demo_student', 6, 6, 0, 1, 1, 'demo_admin', NOW(), '演示数据：包含首修未通过和重修通过记录。');

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

-- Demo-only accounts. Initial password for all demo accounts: Demo123.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_admin', 'zOeizxrgRZic/JFPuVpBUg==:xWXvxTlDz+TMHc7vtlTIA5co9c9CGtcym4aYtr2LK7M=', 'Demo Administrator', 'ADMIN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_academic_admin', '2URhsAIut9zD4Wpa2LitDg==:hqrBro/Sc1nex0VjnlgVqlFs+1hSpS0g/RfTZAnot2g=', 'Demo Academic Administrator', 'ACADEMIC_ADMIN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_librarian', 'CyUGA2zztjSKYZHTcuFFVw==:P3kwUfUVmXevVzORB4/2AO72BYFFLnpKXD4k+Vs/6XE=', 'Demo Librarian', 'LIBRARIAN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_store_manager', 'MlmQfJs4JPqfyzrOS2vWSA==:60rRuqawBtN2BIANRJmd3X++VrG/WgO0npwd09JfU4Y=', 'Demo Store Manager', 'STORE_MANAGER', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Student', 'STUDENT', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_teacher', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', 'Demo Teacher', 'TEACHER', 1, 0);

-- 选课模块演示课程。
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('JAVA101', 'Java 程序设计', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('DB101', '数据库原理', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('NET101', '计算机网络', 3, 'ACTIVE');

-- 学籍与学业审查演示数据：演示账号已预先绑定档案；正式历史成绩由教务维护或导入。
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('demo_student', 'demo_student', 'Demo Student', '未知', '计算机科学与工程学院', '软件工程', 'SE2023-01', 2023, '在读', '', '');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title)
VALUES ('demo_teacher', 'demo_teacher', 'Demo Teacher', '计算机科学与工程学院', '讲师');

INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-java-2025a', 'JAVA101', 'demo_teacher', '2025-2026-1', '周一第1-2节', '教学楼A201', 35, 0, 5, 'OPEN');

INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, location)
VALUES ('offering-java-2025a', 1, 1, 2, '教学楼A201');

INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-se-2026', '软件工程', 2026, 'PUBLISHED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2026', 'JAVA101', 1, 'REQUIRED', 0);
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2026', 'DB101', 1, 'ELECTIVE', 0);

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-java-demo-1', 'demo_student', 'JAVA101', 'offering-java-2025a', '2025-2026-1', 1, '首修', 86, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-1', 'demo_student', 'DB101', NULL, '2025-2026-1', 1, '首修', 52, 0, 0, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-2', 'demo_student', 'DB101', NULL, '2025-2026-2', 2, '重修', 75, 1, 3, NOW());

INSERT INTO tblAcademicReview(review_id, student_id, total_earned_credits, required_earned_credits, failed_course_count, retake_course_count, graduation_ready, reviewed_by, reviewed_at, remark)
VALUES ('review-demo-student-1', 'demo_student', 6, 6, 0, 1, 1, 'demo_admin', NOW(), '演示数据：包含首修未通过和重修通过记录。');

-- 商店模块演示商品。
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active)
VALUES ('P001', '黑色签字笔', 200, 2.0, '0.5mm 中性笔，流畅书写', '文具', 1);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active)
VALUES ('P002', '笔记本 A5', 150, 5.0, '80页横线本，封面随机', '文具', 1);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active)
VALUES ('P003', '矿泉水 550ml', 300, 1.5, '天然矿泉水', '零食饮料', 1);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active)
VALUES ('P004', '薯片 60g', 100, 6.0, '原味薯片，酥脆可口', '零食饮料', 1);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active)
VALUES ('P005', '抽纸 3连包', 80, 8.5, '三层加厚面巾纸', '日用品', 1);

-- 商店模块演示订单。
INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-001', 'demo_student', 'P001', 5, 10.0, NOW(), '黑色签字笔', 2.0);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-002', 'demo_student', 'P003', 2, 3.0, NOW(), '矿泉水 550ml', 1.5);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-003', 'demo_teacher', 'P002', 3, 15.0, NOW(), '笔记本 A5', 5.0);

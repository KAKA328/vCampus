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
VALUES ('demo_teacher', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', '李明远', 'TEACHER', 1, 0);

-- 选课联调账号：同一初始密码 Demo123，分别覆盖无选课、重修、选修与跨专业场景。
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student_new', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo New Student', 'STUDENT', 1, 0);
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student_retake', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Retake Student', 'STUDENT', 1, 0);
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student_elective', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Elective Student', 'STUDENT', 1, 0);
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student_cross', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Cross Major Student', 'STUDENT', 1, 0);
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_teacher_002', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', '周雨桐', 'TEACHER', 1, 0);
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_teacher_003', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', '陈思涵', 'TEACHER', 1, 0);

-- 选课模块演示课程。
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('JAVA101', 'Java 程序设计', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('DB101', '数据库原理', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('NET101', '计算机网络', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('GE101', '大学写作', 2, 'ACTIVE');
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('OS101', '操作系统', 3, 'ACTIVE');
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('MATH101', '高等数学', 4, 'ACTIVE');
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('HIST101', '中国近现代史纲要', 3, 'DISABLED');
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('AI101', '人工智能导论', 2, 'ACTIVE');
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('ENG101', '大学英语', 2, 'ACTIVE');

-- 学籍与学业审查演示数据：每个选课联调账号都预先绑定档案。
INSERT INTO tblClass(class_id, class_name, department_name, major_name, grade_year)
VALUES ('CS2026-01', '计算机科学与技术2026级1班', '计算机科学与工程学院', '计算机科学与技术', 2026);
INSERT INTO tblClass(class_id, class_name, department_name, major_name, grade_year)
VALUES ('SE2023-01', '软件工程2023级1班', '计算机科学与工程学院', '软件工程', 2023);
INSERT INTO tblClass(class_id, class_name, department_name, major_name, grade_year)
VALUES ('CS2026-02', '计算机科学与技术2026级2班', '计算机科学与工程学院', '计算机科学与技术', 2026);

INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260001', 'demo_student', 'Demo Student', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-01', 2026, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260002', 'demo_student_new', 'Demo New Student', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-01', 2026, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20230003', 'demo_student_retake', 'Demo Retake Student', '未知', '计算机科学与工程学院', '软件工程', 'SE2023-01', 2023, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260004', 'demo_student_elective', 'Demo Elective Student', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-01', 2026, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260005', 'demo_student_cross', 'Demo Cross Major Student', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-01', 2026, '在读', '', '');
-- 为 ClientApplication --demo 保留一个未绑定档案，演示管理员开户注册和档案绑定闭环。
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260006', NULL, 'Demo Registration Student', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-01', 2026, '在读', '', '');
-- 复用学籍实体扩充教学班与成绩审核场景，不额外创建登录账号。
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260007', NULL, 'Demo Grade Student One', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-02', 2026, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260008', NULL, 'Demo Grade Student Two', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-02', 2026, '在读', '', '');
INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('20260009', NULL, 'Demo Grade Student Three', '未知', '计算机科学与工程学院', '计算机科学与技术', 'CS2026-02', 2026, '在读', '', '');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('教师001', 'demo_teacher', '李明远', '计算机科学与工程学院', '讲师', 1);
INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('教师002', 'demo_teacher_002', '周雨桐', '计算机科学与工程学院', '讲师', 1);
INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('教师003', 'demo_teacher_003', '陈思涵', '通识教育学院', '讲师', 1);

INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-java-2026a', 'JAVA101', '教师001', '2026-2027-1', '1-8周，周一1-2节，周三3-4节；9-16周，周二5-6节', '教学楼A201', 40, 20, 10, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-java-2026b', 'JAVA101', '教师001', '2026-2027-1', '1-16周，周三3-4节', '教学楼A202', 40, 20, 10, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-db-2026a', 'DB101', '教师002', '2026-2027-1', '1-16周，周二3-4节', '教学楼A203', 20, 30, 10, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-net-2026a', 'NET101', '教师001', '2026-2027-1', '1-16周，周四5-6节', '教学楼B301', 30, 10, 5, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-net-2026b', 'NET101', '教师001', '2026-2027-1', '1-8周，周一1-2节；9-16周，周五7-8节', '教学楼A205', 30, 10, 5, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-ge-2026a', 'GE101', '教师003', '2026-2027-1', '1-16周，周三5-6节', '教学楼A204', 0, 10, 20, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-os-draft', 'OS101', '教师002', '2026-2027-1', '1-16周，周五1-2节', '教学楼B302', 30, 10, 5, 'DRAFT');
-- 人工智能导论故意设置为低容量教学班，用于演示并发抢课容量控制。
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-ai-2026a', 'AI101', '教师003', '2026-2027-1', '1-16周，周一1-2节', '教学楼C101', 1, 1, 1, 'OPEN');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-math-2025b', 'MATH101', '教师002', '2025-2026-2', '1-16周，周二1-2节', '教学楼B201', 30, 10, 5, 'CLOSED');
INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-eng-2025a', 'ENG101', '教师003', '2025-2026-1', '1-16周，周四3-4节', '教学楼C201', 30, 10, 5, 'CLOSED');

-- 容量占用辅助表只记录当前已选人数；新建教学班的三个容量池均从 0 开始。
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2026a', 'REQUIRED', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2026a', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2026a', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-java-2026b', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-java-2026b', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-java-2026b', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-db-2026a', 'REQUIRED', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-db-2026a', 'ELECTIVE', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-db-2026a', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026a', 'REQUIRED', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026a', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026a', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026b', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026b', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-net-2026b', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ge-2026a', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ge-2026a', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ge-2026a', 'CROSS_MAJOR', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-os-draft', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-os-draft', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-os-draft', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ai-2026a', 'REQUIRED', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ai-2026a', 'ELECTIVE', 1);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-ai-2026a', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-math-2025b', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-math-2025b', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-math-2025b', 'CROSS_MAJOR', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-eng-2025a', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-eng-2025a', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count) VALUES ('offering-eng-2025a', 'CROSS_MAJOR', 0);

INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location)
VALUES ('offering-java-2026a', 1, 1, 2, 1, 8, '教学楼A201');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-java-2026a', 3, 3, 4, 1, 8, '教学楼A201');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-java-2026a', 2, 5, 6, 9, 16, '教学楼A201');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-java-2026b', 3, 3, 4, 1, 16, '教学楼A202');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-db-2026a', 2, 3, 4, 1, 16, '教学楼A203');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-net-2026a', 4, 5, 6, 1, 16, '教学楼B301');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-net-2026b', 1, 1, 2, 1, 8, '教学楼A205');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-net-2026b', 5, 7, 8, 9, 16, '教学楼A205');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-ge-2026a', 3, 5, 6, 1, 16, '教学楼A204');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-os-draft', 5, 1, 2, 1, 16, '教学楼B302');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-ai-2026a', 1, 1, 2, 1, 16, '教学楼C101');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-math-2025b', 2, 1, 2, 1, 16, '教学楼B201');
INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, start_week, end_week, location) VALUES ('offering-eng-2025a', 4, 3, 4, 1, 16, '教学楼C201');

INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-cs-2026', '计算机科学与技术', 2026, 'PUBLISHED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-cs-2026', 'JAVA101', 1, 'REQUIRED', 0);
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-cs-2026', 'DB101', 1, 'ELECTIVE', 0);
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-cs-2026', 'NET101', 1, 'REQUIRED', 0);
INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-cn-2026', '汉语言文学', 2026, 'PUBLISHED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-cn-2026', 'GE101', 1, 'ELECTIVE', 1);
INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-se-2023', '软件工程', 2023, 'PUBLISHED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2023', 'JAVA101', 7, 'REQUIRED', 0);
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2023', 'DB101', 7, 'ELECTIVE', 0);
INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-cs-2025-draft', '计算机科学与技术', 2025, 'DRAFT');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-cs-2025-draft', 'MATH101', 1, 'REQUIRED', 0);
INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-se-2026-archived', '软件工程', 2026, 'ARCHIVED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2026-archived', 'ENG101', 1, 'ELECTIVE', 0);

-- 当前学期的首修与重修轮次均处于开放状态，便于直接演示学生端轮次选择。
INSERT INTO tblSelectionRound(round_id, term, round_type, starts_at, ends_at, status)
VALUES ('round-2026-initial', '2026-2027-1', 'INITIAL', DATEADD('d', -1, NOW()), DATEADD('d', 30, NOW()), 'OPEN');
INSERT INTO tblSelectionRound(round_id, term, round_type, starts_at, ends_at, status)
VALUES ('round-2026-retake', '2026-2027-1', 'RETAKE', DATEADD('d', -1, NOW()), DATEADD('d', 30, NOW()), 'OPEN');
INSERT INTO tblSelectionRoundKey(term, round_type) VALUES ('2026-2027-1', 'INITIAL');
INSERT INTO tblSelectionRoundKey(term, round_type) VALUES ('2026-2027-1', 'RETAKE');
-- 三个固定学期均提供轮次样例；历史学期已结束，因此状态为停用。
INSERT INTO tblSelectionRound(round_id, term, round_type, starts_at, ends_at, status)
VALUES ('round-2025b-initial', '2025-2026-2', 'INITIAL', DATEADD('d', -210, NOW()), DATEADD('d', -180, NOW()), 'CLOSED');
INSERT INTO tblSelectionRound(round_id, term, round_type, starts_at, ends_at, status)
VALUES ('round-2025a-retake', '2025-2026-1', 'RETAKE', DATEADD('d', -390, NOW()), DATEADD('d', -360, NOW()), 'CLOSED');
INSERT INTO tblSelectionRoundKey(term, round_type) VALUES ('2025-2026-2', 'INITIAL');
INSERT INTO tblSelectionRoundKey(term, round_type) VALUES ('2025-2026-1', 'RETAKE');

-- 当前有效选课记录：覆盖必修、选修、跨专业选修和重修四种身份。
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-java-001', '20260001', 'offering-java-2026a', 'round-2026-initial', 'REQUIRED', DATEADD('d', -2, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-db-retake', '20230003', 'offering-db-2026a', 'round-2026-retake', 'RETAKE', DATEADD('d', -2, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-db-elective', '20260004', 'offering-db-2026a', 'round-2026-initial', 'ELECTIVE', DATEADD('d', -1, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-ge-cross', '20260005', 'offering-ge-2026a', 'round-2026-initial', 'CROSS_MAJOR', DATEADD('h', -12, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-net-001', '20260002', 'offering-net-2026a', 'round-2026-initial', 'REQUIRED', DATEADD('h', -10, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-ai-required', '20260007', 'offering-ai-2026a', 'round-2026-initial', 'REQUIRED', DATEADD('h', -8, NOW()), 'ACTIVE', NULL);
INSERT INTO tblCourseSelection(selection_id, student_id, offering_id, round_id, selection_type, selected_at, status, dropped_at)
VALUES ('selection-demo-ai-elective', '20260008', 'offering-ai-2026a', 'round-2026-initial', 'ELECTIVE', DATEADD('h', -7, NOW()), 'ACTIVE', NULL);
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260001', 'offering-java-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20230003', 'offering-db-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260004', 'offering-db-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260005', 'offering-ge-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260002', 'offering-net-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260007', 'offering-ai-2026a');
INSERT INTO tblActiveCourseSelection(student_id, offering_id) VALUES ('20260008', 'offering-ai-2026a');

-- 教师与教务审核联调：Java 成绩单待审核；数据库成绩单已被退回修改。
INSERT INTO tblGradeSubmission(submission_id, offering_id, teacher_id, status, created_at, updated_at, reviewed_by, reviewed_at, review_remark)
VALUES ('grade-demo-java-001', 'offering-java-2026a', '教师001', 'PENDING_REVIEW', DATEADD('h', -5, NOW()), DATEADD('h', -4, NOW()), NULL, NULL, NULL);
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-java-001', '20260001', 'REQUIRED', 92, DATEADD('h', -4, NOW()));
INSERT INTO tblGradeSubmissionSnapshot(submission_id, version_no, submitted_at)
VALUES ('grade-demo-java-001', 1, DATEADD('h', -4, NOW()));
INSERT INTO tblGradeSubmissionSnapshotEntry(submission_id, version_no, student_id, selection_type, score)
VALUES ('grade-demo-java-001', 1, '20260001', 'REQUIRED', 92);
INSERT INTO tblGradeSubmissionAudit(audit_id, submission_id, action, actor_id, occurred_at, remark)
VALUES ('grade-audit-java-001', 'grade-demo-java-001', 'SUBMITTED', '教师001', DATEADD('h', -4, NOW()), '提交第1版');
INSERT INTO tblGradeSubmission(submission_id, offering_id, teacher_id, status, created_at, updated_at, reviewed_by, reviewed_at, review_remark)
VALUES ('grade-demo-db-001', 'offering-db-2026a', '教师002', 'RETURNED', DATEADD('h', -4, NOW()), DATEADD('h', -1, NOW()), 'demo_academic_admin', DATEADD('h', -1, NOW()), '请复核重修学生的成绩依据');
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-db-001', '20230003', 'RETAKE', 58, DATEADD('h', -3, NOW()));
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-db-001', '20260004', 'ELECTIVE', 88, DATEADD('h', -3, NOW()));
INSERT INTO tblGradeSubmissionSnapshot(submission_id, version_no, submitted_at)
VALUES ('grade-demo-db-001', 1, DATEADD('h', -2, NOW()));
INSERT INTO tblGradeSubmissionSnapshotEntry(submission_id, version_no, student_id, selection_type, score)
VALUES ('grade-demo-db-001', 1, '20230003', 'RETAKE', 58);
INSERT INTO tblGradeSubmissionSnapshotEntry(submission_id, version_no, student_id, selection_type, score)
VALUES ('grade-demo-db-001', 1, '20260004', 'ELECTIVE', 88);
INSERT INTO tblGradeSubmissionAudit(audit_id, submission_id, action, actor_id, occurred_at, remark)
VALUES ('grade-audit-db-001', 'grade-demo-db-001', 'SUBMITTED', '教师002', DATEADD('h', -2, NOW()), '提交第1版');
INSERT INTO tblGradeSubmissionAudit(audit_id, submission_id, action, actor_id, occurred_at, remark)
VALUES ('grade-audit-db-002', 'grade-demo-db-001', 'RETURNED', 'demo_academic_admin', DATEADD('h', -1, NOW()), '请复核重修学生的成绩依据');
-- 已通过、待审核、已退回和草稿四种状态共同覆盖教师录入与教务审核演示。
INSERT INTO tblGradeSubmission(submission_id, offering_id, teacher_id, status, created_at, updated_at, reviewed_by, reviewed_at, review_remark)
VALUES ('grade-demo-net-001', 'offering-net-2026a', '教师001', 'APPROVED', DATEADD('d', -3, NOW()), DATEADD('d', -2, NOW()), 'demo_academic_admin', DATEADD('d', -2, NOW()), '成绩审核通过');
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-net-001', '20260002', 'REQUIRED', 85, DATEADD('d', -3, NOW()));
INSERT INTO tblGradeSubmissionSnapshot(submission_id, version_no, submitted_at)
VALUES ('grade-demo-net-001', 1, DATEADD('d', -3, NOW()));
INSERT INTO tblGradeSubmissionSnapshotEntry(submission_id, version_no, student_id, selection_type, score)
VALUES ('grade-demo-net-001', 1, '20260002', 'REQUIRED', 85);
INSERT INTO tblGradeSubmissionAudit(audit_id, submission_id, action, actor_id, occurred_at, remark)
VALUES ('grade-audit-net-001', 'grade-demo-net-001', 'SUBMITTED', '教师001', DATEADD('d', -3, NOW()), '提交第1版');
INSERT INTO tblGradeSubmissionAudit(audit_id, submission_id, action, actor_id, occurred_at, remark)
VALUES ('grade-audit-net-002', 'grade-demo-net-001', 'APPROVED', 'demo_academic_admin', DATEADD('d', -2, NOW()), '成绩审核通过');
INSERT INTO tblGradeSubmission(submission_id, offering_id, teacher_id, status, created_at, updated_at, reviewed_by, reviewed_at, review_remark)
VALUES ('grade-demo-ai-001', 'offering-ai-2026a', '教师003', 'DRAFT', DATEADD('h', -6, NOW()), DATEADD('h', -2, NOW()), NULL, NULL, NULL);
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-ai-001', '20260007', 'REQUIRED', 96, DATEADD('h', -2, NOW()));
INSERT INTO tblGradeEntry(submission_id, student_id, selection_type, score, updated_at)
VALUES ('grade-demo-ai-001', '20260008', 'ELECTIVE', 91, DATEADD('h', -2, NOW()));

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-retake-failed', '20230003', 'DB101', NULL, '2025-2026-2', 1, '首修', 48, 0, 0, NOW());
INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-java-demo-1', '20260001', 'JAVA101', 'offering-java-2026a', '2025-2026-2', 1, '首修', 90, 1, 3, NOW());
INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-1', '20260001', 'DB101', 'offering-db-2026a', '2025-2026-2', 1, '首修', 86, 1, 3, NOW());
INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-net-approved-001', '20260002', 'NET101', 'offering-net-2026a', '2026-2027-1', 1, '首修', 85, 1, 3, DATEADD('d', -2, NOW()));
INSERT INTO tblGradeSubmissionResult(submission_id, result_id)
VALUES ('grade-demo-net-001', 'result-net-approved-001');

INSERT INTO tblAcademicReview(review_id, student_id, total_earned_credits, required_earned_credits, failed_course_count, retake_course_count, graduation_ready, reviewed_by, reviewed_at, remark)
VALUES ('review-demo-retake-1', '20230003', 0, 6, 1, 1, 0, 'demo_academic_admin', NOW(), '演示数据：数据库原理首修未通过，当前处于重修轮次。');

-- 商店模块演示商品。
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P001', '黑色签字笔', 200, 2.0, '0.5mm 中性笔，流畅书写', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P002', '笔记本 A5', 150, 5.0, '80页横线本，封面随机', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P003', '矿泉水 550ml', 300, 1.5, '天然矿泉水', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P004', '薯片 60g', 100, 6.0, '原味薯片，酥脆可口', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P005', '抽纸 3连包', 80, 8.5, '三层加厚面巾纸', '日用品', 1, 0);

-- 商店模块扩展演示商品（长期测试数据：P001..P105 共 105 种，覆盖 8 个类别）。
-- 注意：脚本以「;」切分逐条执行，商品名/说明内不得含「;」与单引号「'」。
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P006', '晨光按动中性笔 0.5mm', 120, 3.5, '黑蓝红多色可选 按动出芯 书写顺滑', '文具', 1, 0),
('P007', '得力订书机 装订机', 60, 12.0, '省力型可旋转底座 标配一盒订书钉', '文具', 1, 0),
('P008', 'A4 文件袋 透明网格', 200, 4.0, '加厚防水 分类收纳试卷资料', '文具', 1, 0),
('P009', '彩色记号笔 荧光 6 支装', 90, 8.0, '速干不晕染 划重点好帮手', '文具', 1, 0),
('P010', '固体胶棒 36g', 150, 3.0, '易涂不拉丝 粘贴牢固', '文具', 1, 0),
('P011', '便利贴 76x76mm 混色', 180, 4.5, '多色索引 随手记录备忘', '文具', 1, 0),
('P012', '自动铅笔 0.5mm', 110, 5.0, '金属笔夹 握感舒适', '文具', 1, 0),
('P013', '2B 橡皮擦 白色', 200, 2.0, '清洁力强 不伤纸张', '文具', 1, 0),
('P014', '线圈笔记本 B5', 130, 6.5, '100 页 可 180 度平摊', '文具', 1, 0),
('P015', '剪刀 中号 170mm', 100, 7.0, '防锈合金 儿童安全圆头可选', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P016', '可乐 330ml 罐装', 240, 2.5, '冰镇更爽 经典碳酸饮料', '零食饮料', 1, 0),
('P017', '苏打饼干 原味', 120, 5.5, '咸香酥脆 独立小包', '零食饮料', 1, 0),
('P018', '牛奶 250ml 盒装', 260, 3.0, '全脂纯牛奶 早餐好搭档', '零食饮料', 1, 0),
('P019', '奶茶 即冲粉 3 连包', 150, 8.0, '港式风味 热水即冲', '零食饮料', 1, 0),
('P020', '巧克力 榛子夹心', 100, 9.5, '丝滑夹心 补充能量', '零食饮料', 1, 0),
('P021', '能量饮料 250ml', 200, 5.0, '提神醒脑 运动前后饮用', '零食饮料', 1, 0),
('P022', '坚果混合装 100g', 90, 15.0, '每日坚果 五种混合', '零食饮料', 1, 0),
('P023', '话梅蜜饯 罐装', 80, 10.0, '酸甜开胃 休闲零嘴', '零食饮料', 1, 0),
('P024', '果汁 300ml 橙味', 180, 4.5, '真实果汁 冷饮更佳', '零食饮料', 1, 0),
('P025', '辣条 大面筋', 160, 3.0, '香辣筋道 怀旧味道', '零食饮料', 1, 0),
('P026', '燕麦片 即食 400g', 70, 18.0, '无糖纯燕麦 冲泡即食', '零食饮料', 1, 0),
('P027', '咖啡 三合一 袋装', 140, 2.0, '速溶奶咖 熬夜提神', '零食饮料', 1, 0),
('P028', '果冻 综合口味 桶装', 110, 6.0, 'QQ 弹弹 多种水果味', '零食饮料', 1, 0),
('P029', '薯片 番茄味 60g', 130, 6.0, '轻薄香脆 追剧必备', '零食饮料', 1, 0),
('P030', '运动饮料 600ml', 190, 5.5, '补充电解质 快速补水', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P031', '洗衣液 500g', 90, 12.0, '深层去渍 衣物清香', '日用品', 1, 0),
('P032', '垃圾袋 加厚 100 只', 130, 6.0, '抽绳式 大容量耐用', '日用品', 1, 0),
('P033', '挂钩 强力无痕 4 个装', 170, 5.0, '免打孔 承重牢固', '日用品', 1, 0),
('P034', '收纳箱 中号 带盖', 60, 25.0, '透明可视 衣物杂物分类', '日用品', 1, 0),
('P035', '衣架 浸塑 10 个装', 150, 8.0, '防滑耐用 晾晒不变形', '日用品', 1, 0),
('P036', '雨伞 全自动折叠', 80, 22.0, '一键开收 晴雨两用', '日用品', 1, 0),
('P037', '保温杯 500ml', 100, 39.0, '316 不锈钢 长效保温', '日用品', 1, 0),
('P038', '毛巾 纯棉 3 条装', 120, 15.0, '柔软吸水 亲肤不掉毛', '日用品', 1, 0),
('P039', '电池 5 号 4 粒装', 200, 5.0, '碱性电池 遥控器适用', '日用品', 1, 0),
('P040', '鞋刷 硬毛清洁刷', 90, 3.0, '洗鞋去污 手柄舒适', '日用品', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P041', 'Type-C 数据线 1m', 180, 9.0, '快充支持 尼龙编织耐磨', '数码配件', 1, 0),
('P042', '手机支架 桌面折叠', 140, 12.0, '多角度调节 铝合金底座', '数码配件', 1, 0),
('P043', '蓝牙耳机 入耳式', 100, 79.0, '降噪通话 续航持久', '数码配件', 1, 0),
('P044', '充电宝 10000mAh', 90, 89.0, '双向快充 轻薄便携', '数码配件', 1, 0),
('P045', '鼠标 无线静音', 110, 45.0, '2.4G 连接 办公游戏两用', '数码配件', 1, 0),
('P046', '键盘膜 14 寸', 130, 10.0, '硅胶防尘 键位贴合', '数码配件', 1, 0),
('P047', 'U 盘 32G USB3.0', 160, 25.0, '高速读写 金属外壳', '数码配件', 1, 0),
('P048', '笔记本散热垫', 80, 49.0, '静音风扇 多档调速', '数码配件', 1, 0),
('P049', 'HDMI 转接头', 120, 18.0, '音视频同步 即插即用', '数码配件', 1, 0),
('P050', '手机贴膜 钢化膜', 200, 8.0, '高透抗指纹 附贴膜工具', '数码配件', 1, 0),
('P051', '桌面收纳盒 数据线整理', 150, 13.0, '理线分格 清爽桌面', '数码配件', 1, 0),
('P052', '笔记本内胆包 14 寸', 110, 35.0, '加绒防震 轻薄护机', '数码配件', 1, 0),
('P053', '耳机收纳包', 140, 8.0, '防缠绕 EVA 硬壳', '数码配件', 1, 0),
('P054', '摄像头遮挡盖', 220, 3.0, '隐私保护 粘贴式', '数码配件', 1, 0),
('P055', '三合一充电线', 170, 12.0, '苹果安卓 Type-C 通用', '数码配件', 1, 0),
('P056', '桌面理线器 3 个装', 190, 6.0, '磁吸理线 整洁办公', '数码配件', 1, 0),
('P057', '机械键盘 87 键', 70, 129.0, '青轴打字清脆 键帽可换', '数码配件', 1, 0),
('P058', '无线充电板 15W', 100, 45.0, '随放随充 兼容多机型', '数码配件', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P059', '羽毛球拍 入门款', 90, 68.0, '全碳素 含球拍包', '体育用品', 1, 0),
('P060', '篮球 7 号', 80, 89.0, '室内外通用 耐磨防滑', '体育用品', 1, 0),
('P061', '瑜伽垫 加厚', 110, 45.0, '防滑回弹 附绑带', '体育用品', 1, 0),
('P062', '跳绳 竞速轴承', 160, 15.0, '长度可调 计数可选', '体育用品', 1, 0),
('P063', '乒乓球拍 双拍套装', 120, 35.0, '含 3 颗球 对练入门', '体育用品', 1, 0),
('P064', '哑铃 5kg 一对', 60, 99.0, '环保包胶 防滑握把', '体育用品', 1, 0),
('P065', '臂力器 30kg', 70, 39.0, '家用健身 渐进负荷', '体育用品', 1, 0),
('P066', '运动水壶 750ml', 130, 25.0, '食品级材质 一键开启', '体育用品', 1, 0),
('P067', '健腹轮 自动回弹', 90, 42.0, '加宽轮距 附跪垫', '体育用品', 1, 0),
('P068', '握力器 可调节', 140, 18.0, '五档调力 训练前臂', '体育用品', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P069', '洗发水 400ml', 110, 28.0, '去屑控油 清爽留香', '个人护理', 1, 0),
('P070', '沐浴露 500ml', 120, 22.0, '滋润保湿 泡沫绵密', '个人护理', 1, 0),
('P071', '牙膏 120g', 200, 8.0, '清新口气 含氟防蛀', '个人护理', 1, 0),
('P072', '电动牙刷头 替换装 2 支', 90, 29.0, '深层清洁 软毛护龈', '个人护理', 1, 0),
('P073', '护手霜 随身装', 150, 12.0, '滋润不油腻 秋冬必备', '个人护理', 1, 0),
('P074', '洗面奶 100g', 140, 26.0, '温和洁净 控油祛痘', '个人护理', 1, 0),
('P075', '湿巾 80 抽 便携装', 220, 6.0, '温和无酒精 清洁保湿', '个人护理', 1, 0),
('P076', '牙线棒 50 支装', 170, 9.0, '薄荷味 清洁齿缝', '个人护理', 1, 0),
('P077', '镜子 折叠双面', 130, 15.0, '高清放大镜 桌面立式', '个人护理', 1, 0),
('P078', '指甲剪 套装 8 件', 100, 18.0, '不锈钢便携 附皮套', '个人护理', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P079', '台灯 护眼可调光', 90, 69.0, '无频闪 三档色温', '宿舍生活', 1, 0),
('P080', '床上折叠桌', 80, 45.0, '宿舍神器 轻便可收纳', '宿舍生活', 1, 0),
('P081', '耳塞 睡眠降噪 5 对', 180, 8.0, '柔软回弹 隔音助眠', '宿舍生活', 1, 0),
('P082', '眼罩 遮光 真丝内衬', 160, 12.0, '轻柔软绵 午休必备', '宿舍生活', 1, 0),
('P083', '插座 一转二 USB', 150, 35.0, '带儿童保护门 兼容快充', '宿舍生活', 1, 0),
('P084', '晾衣绳 可伸缩', 130, 10.0, '宿舍阳台 免钉固定', '宿舍生活', 1, 0),
('P085', '桌面小风扇 充电式', 120, 49.0, '静音大风量 USB 供电', '宿舍生活', 1, 0),
('P086', '保温饭盒 双层', 90, 39.0, '304 内胆 保温保冷', '宿舍生活', 1, 0),
('P087', '折叠凳 便携小马扎', 140, 22.0, '加厚钢管 承重稳固', '宿舍生活', 1, 0),
('P088', '宿舍门锁 密码挂锁', 110, 20.0, '四位密码 免钥匙', '宿舍生活', 1, 0),
('P089', '防潮除湿盒', 170, 13.0, '吸湿防霉 衣柜适用', '宿舍生活', 1, 0),
('P090', '蚊帐 支架式', 100, 35.0, '单人床 免打孔安装', '宿舍生活', 1, 0),
('P091', '多孔笔筒 桌面收纳', 200, 12.0, '分层分格 收纳文具杂物', '宿舍生活', 1, 0),
('P092', '小型药箱 家庭常备', 130, 28.0, '分层收纳 防尘密封', '宿舍生活', 1, 0),
('P093', '灭蚊灯 物理吸入式', 120, 39.0, '静音无味 USB 供电', '宿舍生活', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P094', '校园明信片 一套 10 张', 180, 10.0, '校园风景手绘 可寄可收藏', '文创纪念品', 1, 0),
('P095', '校徽钥匙扣', 220, 8.0, '金属烤漆 小巧别致', '文创纪念品', 1, 0),
('P096', '帆布包 校名款', 150, 25.0, '加厚帆布 大容量', '文创纪念品', 1, 0),
('P097', '马克杯 校训定制', 160, 22.0, '陶瓷 350ml 保温防烫', '文创纪念品', 1, 0),
('P098', '书签 金属镂空', 200, 6.0, '流苏挂坠 精美耐用', '文创纪念品', 1, 0),
('P099', 'T 恤 毕业纪念款', 130, 45.0, '纯棉印花 尺码齐全', '文创纪念品', 1, 0),
('P100', '笔记本 烫金校徽 A5', 170, 18.0, '硬壳封面 内页厚实', '文创纪念品', 1, 0),
('P101', '钢笔 纪念礼盒', 90, 88.0, '金属笔身 附礼盒墨水', '文创纪念品', 1, 0),
('P102', '钥匙扣 母校建筑', 200, 9.0, '树脂立体 送人自用两宜', '文创纪念品', 1, 0),
('P103', '文件夹 校园四季', 180, 8.0, '春季限定图案 收纳文件', '文创纪念品', 1, 0),
('P104', '徽章 专业学院款', 220, 5.0, '珐琅工艺 别针式', '文创纪念品', 1, 0),
('P105', '保温马克杯 两用', 140, 35.0, '可冲泡 可外带 密封杯盖', '文创纪念品', 1, 0);

-- 商店模块演示订单。
INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-001', 'demo_student', 'P001', 5, 10.0, NOW(), '黑色签字笔', 2.0);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-002', 'demo_student', 'P003', 2, 3.0, NOW(), '矿泉水 550ml', 1.5);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-003', 'demo_teacher', 'P002', 3, 15.0, NOW(), '笔记本 A5', 5.0);

-- 商店模块演示购物车条目。
INSERT INTO tblCartItem(cart_item_id, user_id, product_id, quantity, added_at)
VALUES ('demo-cart-001', 'demo_student', 'P001', 2, NOW());

INSERT INTO tblCartItem(cart_item_id, user_id, product_id, quantity, added_at)
VALUES ('demo-cart-002', 'demo_student', 'P003', 1, NOW());

-- 校园钱包演示余额（单位：分）。给能购买的演示账号留足余额，便于直接体验购买/购物车结算；
-- 仍保留一张 100 元档（demo_admin 20000 分=200 元）演示小金额场景。1 元 = 100 分。
INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_student', 1000000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_teacher', 1000000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_admin', 20000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_store_manager', 50000);

-- 图书馆模块演示馆藏：共 50 种、150 册，含演示借阅后 147 册可借。
-- price 为模拟价格（元），可供后续遗失赔偿规则测试，不代表实际售价。
INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B001', 'Java核心技术（卷I）', 'Cay S. Horstmann', '9787115547392', '计算机', '机械工业出版社', 129.00, 3, 2, 'A-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B002', '算法导论', 'Thomas H. Cormen', '9787111407010', '计算机', '机械工业出版社', 128.00, 2, 1, 'A-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B003', '红楼梦', '曹雪芹', '9787020002207', '文学', '人民文学出版社', 59.70, 2, 2, 'B-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B004', '三体', '刘慈欣', '9787536692930', '科幻', '重庆出版社', 39.00, 4, 3, 'B-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B005', '高等数学（第七版）', '同济大学数学系', '9787040396638', '教材', '高等教育出版社', 56.80, 5, 5, 'C-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B006', '深入理解计算机系统', 'Randal E. Bryant', '9787111544937', '计算机', '机械工业出版社', 139.00, 3, 3, 'A-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B007', '设计模式', 'Erich Gamma', '9787111210340', '计算机', '机械工业出版社', 79.00, 2, 2, 'A-04');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B008', '计算机网络：自顶向下方法', 'James F. Kurose', '9787111599715', '计算机', '机械工业出版社', 89.00, 3, 3, 'A-05');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B009', '活着', '余华', '9787530215593', '文学', '北京十月文艺出版社', 35.00, 4, 4, 'B-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B010', '人类简史', '尤瓦尔·赫拉利', '9787508647357', '历史', '中信出版社', 68.00, 2, 2, 'D-01');

-- B011–B050 的 isbn 字段使用 DEMO-Bxxx 演示编码，出版社为占位数据；不代表真实出版版本。
INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B011', '平凡的世界', '路遥', 'DEMO-B011', '文学', '演示出版社', 79.00, 3, 3, 'B-04');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B012', '围城', '钱锺书', 'DEMO-B012', '文学', '演示出版社', 48.00, 3, 3, 'B-05');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B013', '骆驼祥子', '老舍', 'DEMO-B013', '文学', '演示出版社', 32.00, 3, 3, 'B-06');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B014', '朝花夕拾', '鲁迅', 'DEMO-B014', '文学', '演示出版社', 28.00, 3, 3, 'B-07');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B015', '边城', '沈从文', 'DEMO-B015', '文学', '演示出版社', 30.00, 3, 3, 'B-08');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B016', '老人与海', '欧内斯特·海明威', 'DEMO-B016', '文学', '演示出版社', 26.00, 3, 3, 'B-09');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B017', '百年孤独', '加西亚·马尔克斯', 'DEMO-B017', '文学', '演示出版社', 68.00, 3, 3, 'B-10');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B018', '傲慢与偏见', '简·奥斯汀', 'DEMO-B018', '文学', '演示出版社', 39.00, 3, 3, 'B-11');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B019', '流浪地球', '刘慈欣', 'DEMO-B019', '科幻', '演示出版社', 45.00, 3, 3, 'B-12');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B020', '球状闪电', '刘慈欣', 'DEMO-B020', '科幻', '演示出版社', 38.00, 3, 3, 'B-13');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B021', '银河帝国：基地', '艾萨克·阿西莫夫', 'DEMO-B021', '科幻', '演示出版社', 52.00, 3, 3, 'B-14');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B022', '海底两万里', '儒勒·凡尔纳', 'DEMO-B022', '科幻', '演示出版社', 35.00, 3, 3, 'B-15');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B023', '时间机器', 'H. G. 威尔斯', 'DEMO-B023', '科幻', '演示出版社', 29.00, 3, 3, 'B-16');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B024', '数据结构（C语言版）', '严蔚敏', 'DEMO-B024', '计算机', '演示出版社', 39.00, 3, 3, 'A-06');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B025', '代码整洁之道', '罗伯特·C. 马丁', 'DEMO-B025', '计算机', '演示出版社', 79.00, 3, 3, 'A-07');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B026', '重构：改善既有代码的设计', '马丁·福勒', 'DEMO-B026', '计算机', '演示出版社', 88.00, 3, 3, 'A-08');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B027', '计算机程序的构造和解释', '哈罗德·阿贝尔森', 'DEMO-B027', '计算机', '演示出版社', 99.00, 3, 3, 'A-09');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B028', '数据库系统概念', '亚伯拉罕·西尔伯沙茨', 'DEMO-B028', '计算机', '演示出版社', 109.00, 3, 3, 'A-10');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B029', '万历十五年', '黄仁宇', 'DEMO-B029', '历史', '演示出版社', 49.00, 3, 3, 'D-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B030', '中国历代政治得失', '钱穆', 'DEMO-B030', '历史', '演示出版社', 32.00, 3, 3, 'D-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B031', '全球通史', 'L. S. 斯塔夫里阿诺斯', 'DEMO-B031', '历史', '演示出版社', 96.00, 3, 3, 'D-04');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B032', '史记', '司马迁', 'DEMO-B032', '历史', '演示出版社', 85.00, 3, 3, 'D-05');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B033', '中国近代史', '蒋廷黻', 'DEMO-B033', '历史', '演示出版社', 39.00, 3, 3, 'D-06');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B034', '线性代数', '同济大学数学系', 'DEMO-B034', '教材', '演示出版社', 39.00, 3, 3, 'C-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B035', '概率论与数理统计', '盛骤', 'DEMO-B035', '教材', '演示出版社', 45.00, 3, 3, 'C-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B036', '大学物理', '程守洙', 'DEMO-B036', '教材', '演示出版社', 62.00, 3, 3, 'C-04');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B037', '离散数学', '左孝凌', 'DEMO-B037', '教材', '演示出版社', 49.00, 3, 3, 'C-05');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B038', '苏菲的世界', '乔斯坦·贾德', 'DEMO-B038', '哲学', '演示出版社', 58.00, 3, 3, 'E-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B039', '理想国', '柏拉图', 'DEMO-B039', '哲学', '演示出版社', 45.00, 3, 3, 'E-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B040', '论语', '孔子及其弟子', 'DEMO-B040', '哲学', '演示出版社', 29.00, 3, 3, 'E-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B041', '道德经', '老子', 'DEMO-B041', '哲学', '演示出版社', 26.00, 3, 3, 'E-04');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B042', '艺术的故事', 'E. H. 贡布里希', 'DEMO-B042', '艺术', '演示出版社', 168.00, 3, 3, 'F-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B043', '美的历程', '李泽厚', 'DEMO-B043', '艺术', '演示出版社', 59.00, 3, 3, 'F-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B044', '谈美', '朱光潜', 'DEMO-B044', '艺术', '演示出版社', 32.00, 3, 3, 'F-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B045', '经济学原理', 'N. 格里高利·曼昆', 'DEMO-B045', '经济', '演示出版社', 128.00, 3, 3, 'G-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B046', '国富论', '亚当·斯密', 'DEMO-B046', '经济', '演示出版社', 79.00, 3, 3, 'G-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B047', '牛奶可乐经济学', '罗伯特·弗兰克', 'DEMO-B047', '经济', '演示出版社', 42.00, 3, 3, 'G-03');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B048', '时间简史', '史蒂芬·霍金', 'DEMO-B048', '自然科学', '演示出版社', 45.00, 3, 3, 'H-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B049', '从一到无穷大', '乔治·伽莫夫', 'DEMO-B049', '自然科学', '演示出版社', 49.00, 3, 3, 'H-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, price, total_copies, available_copies, location)
VALUES ('B050', '物种起源', '查尔斯·达尔文', 'DEMO-B050', '自然科学', '演示出版社', 58.00, 3, 3, 'H-03');

-- 借阅演示数据使用相对日期，重建数据库后始终包含临期、普通、已归还和逾期场景。
INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('demo-br-001', 'demo-bo-001', 'demo_student', 'B001', DATEADD('d', -28, NOW()), DATEADD('d', 2, NOW()), NULL, 'BORROWED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('demo-br-002', 'demo-bo-002', 'demo_student', 'B004', DATEADD('d', -23, NOW()), DATEADD('d', 7, NOW()), NULL, 'BORROWED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('demo-br-003', 'demo-bo-003', 'demo_student', 'B003', DATEADD('d', -50, NOW()), DATEADD('d', -20, NOW()), DATEADD('d', -35, NOW()), 'RETURNED');

INSERT INTO tblBorrowRecord(record_id, order_id, user_id, book_id, borrow_date, due_date, return_date, status)
VALUES ('demo-br-004', 'demo-bo-004', 'demo_teacher', 'B002', DATEADD('d', -32, NOW()), DATEADD('d', -2, NOW()), NULL, 'BORROWED');
